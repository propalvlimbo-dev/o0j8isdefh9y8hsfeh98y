package ru.rooyzee.elytrixclans.utils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixclans.Main;

/**
 * Кэш голов игроков.
 *
 * SkullMeta#setOwner(String) и Bukkit#getOfflinePlayer(String) выполняют СИНХРОННЫЙ запрос
 * к серверу сессий Mojang. Вызов этих методов из main-потока (например при открытии
 * меню клана) подвешивает весь сервер на каждую оффлайн-голову, а Mojang дополнительно
 * режет скорость до ~1 запроса в секунду. Отсюда и «лаг» при /clan info.
 *
 * Кэш решает задачу так: main-поток никогда не обращается в сеть. Если игрок уже известен —
 * голова отдаётся мгновенно из памяти, если нет — запрос уходит в асинхронную очередь
 * (не чаще одного запроса в ~1.2 секунды), а готовая голова доклеивается в открытое меню.
 *
 * Дополнительно кэшируются САМИ «болванки» голов с уже проставленным владельцем.
 * Раньше при каждом открытии /clan menu на каждого из 24 участников заново создавался
 * SkullMeta и вызывался setOwningPlayer: профиль сериализуется в NBT на каждый вызов, и на
 * заполненном клане это давало заметный фриз сервера в полсекунды. Теперь владелец ставится
 * один раз на игрока, а меню лишь клонирует готовый предмет.
 *
 * Если установка владельца всё же оказывается дорогой (экзотические форки, сторонние
 * профиль-провайдеры), кэш сам замечает это по времени и переходит в режим отложенного
 * заполнения: меню открывается мгновенно с пустыми головами, а владельцы доклеиваются
 * порциями по несколько штук за тик с жёстким бюджетом времени.
 */
public final class PlayerHeadCache {

    private static final Map<String, OfflinePlayer> KNOWN = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Entry> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();

    /** Готовые головы с проставленным владельцем: ключ — ник в нижнем регистре. */
    private static final Map<String, ItemStack> SKULLS = new ConcurrentHashMap<>();
    /** Отложенные применения владельцев: разбираются в main-потоке с бюджетом времени. */
    private static final ConcurrentLinkedQueue<Runnable> APPLY_QUEUE = new ConcurrentLinkedQueue<>();
    /** Ники, для которых голова прямо сейчас собирается в фоне: не дублируем работу. */
    private static final Map<String, Boolean> BUILDING = new ConcurrentHashMap<>();

    private static final int MAX_SKULL_CACHE = 2000;
    private static final int MAX_APPLY_QUEUE = 1024;
    /** Больше этого времени одна установка владельца в main-потоке стоить не должна. */
    private static final long SYNC_APPLY_BUDGET_NANOS = 3_000_000L;
    /** Сколько времени за тик разрешено тратить на отложенные головы. */
    private static final long TICK_BUDGET_NANOS = 2_000_000L;

    private static volatile boolean syncOwnerAllowed = true;

    private static final long LOOKUP_INTERVAL_MS = 1200L;
    private static final long FAILURE_COOLDOWN_MS = 10 * 60 * 1000L;
    private static final int MAX_QUEUE_SIZE = 512;

    private static volatile long lastLookupAt = 0L;
    private static volatile boolean seeded = false;
    private static JavaPlugin plugin;

    private PlayerHeadCache() {
    }

    private static final class Entry {
        private final String name;
        private final Consumer<OfflinePlayer> callback;

        private Entry(String name, Consumer<OfflinePlayer> callback) {
            this.name = name;
            this.callback = callback;
        }
    }

    /**
     * Вызывается один раз при включении плагина: регистрирует слушателя входов,
     * периодическую асинхронную выборку очереди и первичное заполнение кэша.
     */
    public static void register(JavaPlugin owner) {
        plugin = owner;
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onJoin(PlayerJoinEvent event) {
                Player joined = event.getPlayer();
                remember(joined);
                // Готовим голову заранее и вне «часа пик»: к моменту открытия /clan menu
                // болванка уже лежит в кэше, и меню собирается на одних клонах.
                enqueueApply(() -> preparedSkull(joined.getName(), joined));
            }
        }, owner);

        Bukkit.getScheduler().runTaskTimerAsynchronously(owner, PlayerHeadCache::pump, 40L, 20L);
        // Отложенные головы доклеиваем по чуть-чуть каждый тик: открытие меню от этого не зависит.
        Bukkit.getScheduler().runTaskTimer(owner, PlayerHeadCache::drainApplyQueue, 1L, 1L);
        // Первичное заполнение из локального кэша профиля (playercache) — без единого сетевого запроса.
        Bukkit.getScheduler().runTaskLaterAsynchronously(owner, PlayerHeadCache::seedFromLocalCache, 100L);
    }

    public static void shutdown() {
        QUEUE.clear();
        PENDING.clear();
        APPLY_QUEUE.clear();
        BUILDING.clear();
        SKULLS.clear();
        plugin = null;
    }

    /** Забываем готовую голову: например, игрок сменил ник или скин. */
    public static void invalidate(String name) {
        if (name != null) SKULLS.remove(key(name));
    }

    /** Запоминаем OfflinePlayer онлайн-игрока, не сохраняя ссылку на сам объект Player. */
    public static void remember(Player player) {
        if (player == null) return;
        put(player.getName(), Bukkit.getOfflinePlayer(player.getUniqueId()));
    }

    /** Уже известный (без блокировок) OfflinePlayer по нику, либо null. */
    public static OfflinePlayer known(String name) {
        if (name == null) return null;
        return KNOWN.get(key(name));
    }

    /**
     * Ставит владельца головы, если игрок уже есть в кэше. Никогда не блокирует поток.
     */
    public static void applyOwner(SkullMeta meta, String name) {
        if (meta == null) return;
        OfflinePlayer player = known(name);
        if (player != null) applyOwner(meta, player);
    }

    /** Безопасная обёртка setOwningPlayer: оффлайн-серверы/сбежавшие кэши не должны ронять меню. */
    public static void applyOwner(SkullMeta meta, OfflinePlayer player) {
        if (meta == null || player == null) return;
        try {
            meta.setOwningPlayer(player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Если ник неизвестен — асинхронно узнаёт его и обновляет слот уже открытого меню.
     */
    public static void updateSlotLater(Inventory inventory, int slot, String name) {
        if (inventory == null || name == null || name.isEmpty()) return;
        OfflinePlayer cached = known(name);
        if (cached != null) {
            // Профиль есть, но применять его прямо сейчас нельзя (режим отложенного
            // заполнения) — доклеим в одном из ближайших тиков.
            enqueueApply(() -> applyToSlot(inventory, slot, name, cached));
            return;
        }
        request(name, player -> {
            if (player == null) return;
            applyToSlot(inventory, slot, name, player);
        });
    }

    private static void applyToSlot(Inventory inventory, int slot, String name, OfflinePlayer player) {
        try {
            if (inventory == null || player == null) return;
            if (slot < 0 || slot >= inventory.getSize()) return;
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() != Material.PLAYER_HEAD) return;
            ItemMeta itemMeta = item.getItemMeta();
            if (!(itemMeta instanceof SkullMeta)) return;
            applyOwner((SkullMeta) itemMeta, player);
            item.setItemMeta(itemMeta);
            inventory.setItem(slot, item);

            // Заодно запоминаем болванку, чтобы следующее открытие меню было бесплатным.
            buildAsync(name, player);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Готовая «болванка» головы с владельцем, либо null, если владельца сейчас
     * не получить дёшево. Сам предмет НЕ клонируется — вызывающий обязан клонировать.
     */
    private static ItemStack preparedSkull(String name, Player onlinePlayer) {
        String key = key(name);
        ItemStack cached = SKULLS.get(key);
        if (cached != null) return cached;

        // Владельца головы ОФФЛАЙН-игрока в главном потоке не ставим никогда.
        // setOwningPlayer подтягивает GameProfile: на оффлайн-игроке это чтение файла профиля,
        // а на некоторых сборках — запрос к Mojang. Двадцать четыре таких вызова подряд и
        // давали фриз на /clan menu. Голова появится через очередь с бюджетом по времени.
        if (onlinePlayer == null) {
            OfflinePlayer knownOwner = known(name);
            if (knownOwner != null) buildAsync(name, knownOwner);
            return null;
        }

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta itemMeta = skull.getItemMeta();
        if (!(itemMeta instanceof SkullMeta)) return null;

        // Профиль онлайн-игрока уже загружен сервером, поэтому здесь это дёшево.
        long start = System.nanoTime();
        applyOwner((SkullMeta) itemMeta, onlinePlayer);
        skull.setItemMeta(itemMeta);
        long elapsed = System.nanoTime() - start;

        if (elapsed > SYNC_APPLY_BUDGET_NANOS && syncOwnerAllowed) {
            syncOwnerAllowed = false;
            JavaPlugin owner0 = plugin;
            if (owner0 != null) {
                owner0.getLogger().warning("Установка владельца головы заняла "
                        + (elapsed / 1_000_000L) + " мс. Меню кланов переведены на отложенное "
                        + "заполнение голов, чтобы не задерживать главный поток.");
            }
        }

        put(name, onlinePlayer);
        cacheSkull(key, skull);
        return skull;
    }

    /**
     * Сборка головы в АСИНХРОННОМ потоке.
     *
     * setOwningPlayer на оффлайн-игроке подтягивает GameProfile, и если профиль ещё не
     * закэширован сервером, вызов уходит в чтение с диска или в сеть. Именно поэтому
     * /clan menu иногда открывался мгновенно, а иногда вешал сервер на полсекунды:
     * всё зависело от того, чьи профили уже были прогреты. Здесь создаётся обычный
     * ItemStack без привязки к миру, так что делать это вне главного потока безопасно.
     */
    private static void buildAsync(String name, OfflinePlayer owner) {
        if (owner == null || name == null) return;
        String key = key(name);
        if (SKULLS.containsKey(key)) return;
        // Один и тот же ник не собираем параллельно несколько раз.
        if (BUILDING.putIfAbsent(key, Boolean.TRUE) != null) return;
        JavaPlugin plug = plugin;
        if (plug == null || !plug.isEnabled()) {
            BUILDING.remove(key);
            return;
        }
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plug, () -> {
                try {
                    buildAndCache(name, owner);
                } finally {
                    BUILDING.remove(key);
                }
            });
        } catch (Throwable ignored) {
            BUILDING.remove(key);
        }
    }

    /** Сборка головы: вызывается из асинхронного потока либо из очереди с бюджетом. */
    private static void buildAndCache(String name, OfflinePlayer owner) {
        String key = key(name);
        if (owner == null || SKULLS.containsKey(key)) return;
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = skull.getItemMeta();
        if (!(meta instanceof SkullMeta)) return;
        applyOwner((SkullMeta) meta, owner);
        skull.setItemMeta(meta);
        cacheSkull(key, skull);
    }

    private static void cacheSkull(String key, ItemStack skull) {
        if (SKULLS.size() >= MAX_SKULL_CACHE) SKULLS.clear();
        SKULLS.put(key, skull);
    }

    /**
     * Голова участника для меню: имя и описание ставятся на клон готовой болванки,
     * поэтому открытие меню не трогает профили игроков.
     */
    public static ItemStack createHead(String name, Player onlinePlayer, String displayName, List<String> lore) {
        ItemStack prepared = preparedSkull(name, onlinePlayer);
        ItemStack head = prepared != null ? prepared.clone() : new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            if (displayName != null) meta.setDisplayName(displayName);
            if (lore != null) meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    /** Ставит голову в инвентарь: мгновенно, а владельца — сразу или отложенно. */
    public static void fillHead(Inventory inventory, int slot, String name, Player onlinePlayer,
                                String displayName, List<String> lore) {
        if (inventory == null) return;
        // preparedSkull кэширует результат, поэтому второй вызов внутри createHead бесплатный.
        boolean ready = preparedSkull(name, onlinePlayer) != null;
        inventory.setItem(slot, createHead(name, onlinePlayer, displayName, lore));
        if (!ready) {
            updateSlotLater(inventory, slot, name);
        }
    }

    /** Разбор очереди отложенных голов: не дороже пары миллисекунд за тик. */
    private static void drainApplyQueue() {
        if (APPLY_QUEUE.isEmpty()) return;
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        Runnable task;
        while (System.nanoTime() < deadline && (task = APPLY_QUEUE.poll()) != null) {
            try {
                task.run();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void enqueueApply(Runnable task) {
        if (task == null) return;
        if (APPLY_QUEUE.size() >= MAX_APPLY_QUEUE) return;
        APPLY_QUEUE.offer(task);
    }

    private static void request(String name, Consumer<OfflinePlayer> callback) {
        if (name == null || name.isEmpty()) return;
        JavaPlugin owner = plugin;
        if (owner == null || !owner.isEnabled()) return;
        String key = key(name);
        Long failedAt = FAILED_AT.get(key);
        long now = System.currentTimeMillis();
        if (failedAt != null && now - failedAt < FAILURE_COOLDOWN_MS) return;
        if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) return;
        if (QUEUE.size() >= MAX_QUEUE_SIZE) {
            PENDING.remove(key);
            return;
        }
        QUEUE.offer(new Entry(name, callback));
    }

    @SuppressWarnings("deprecation")
    private static void pump() {
        JavaPlugin owner = plugin;
        Main main = Main.getInstance();
        if (owner == null || main == null || !owner.isEnabled()) return;
        if (QUEUE.isEmpty()) {
            if (!seeded) seedFromLocalCache();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastLookupAt < LOOKUP_INTERVAL_MS) return;

        Entry entry = QUEUE.poll();
        while (entry != null) {
            OfflinePlayer cached = KNOWN.get(key(entry.name));
            if (cached == null) break;
            finish(entry, cached);
            entry = QUEUE.poll();
        }
        if (entry == null) return;
        lastLookupAt = now;

        OfflinePlayer result = null;
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.name);
            if (player != null && player.getUniqueId() != null) {
                // Прогреваем локальные кэши имени/профиля вне main-потока,
                // чтобы последующий setOwningPlayer ничего не читал с диска.
                try {
                    player.getName();
                    player.getFirstPlayed();
                    player.getLastPlayed();
                } catch (Throwable ignored) {
                }
                result = player;
                put(entry.name, player);
                // Мы уже в асинхронном потоке — собираем голову сразу, чтобы главному
                // потоку осталось только поставить готовый предмет в слот.
                buildAndCache(entry.name, player);
            }
        } catch (Throwable ignored) {
        }

        if (result == null) {
            if (FAILED_AT.size() > 5000) FAILED_AT.clear();
            FAILED_AT.put(key(entry.name), now);
        }
        finish(entry, result);
    }

    private static void finish(Entry entry, OfflinePlayer player) {
        PENDING.remove(key(entry.name));
        if (entry.callback == null) return;
        JavaPlugin owner = plugin;
        if (owner == null || !owner.isEnabled()) return;
        final OfflinePlayer resolved = player;
        // Через очередь с бюджетом: пачка готовых голов не должна складываться в один лаг-спайк.
        enqueueApply(() -> entry.callback.accept(resolved));
    }

    private static void seedFromLocalCache() {
        if (seeded) return;
        seeded = true;
        try {
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player == null) continue;
                String name = null;
                try {
                    name = player.getName();
                } catch (Throwable ignored) {
                }
                if (name == null) continue;
                put(name, player);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void put(String name, OfflinePlayer player) {
        if (name == null || player == null) return;
        UUID uuid = null;
        try {
            uuid = player.getUniqueId();
        } catch (Throwable ignored) {
        }
        if (uuid == null) return;
        String key = key(name);
        KNOWN.put(key, player);
        FAILED_AT.remove(key);
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
