package ru.rooyzee.elytrixclans.utils;

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
 */
public final class PlayerHeadCache {

    private static final Map<String, OfflinePlayer> KNOWN = new ConcurrentHashMap<>();
    private static final Map<String, Long> FAILED_AT = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<Entry> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();

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
                remember(event.getPlayer());
            }
        }, owner);

        Bukkit.getScheduler().runTaskTimerAsynchronously(owner, PlayerHeadCache::pump, 40L, 20L);
        // Первичное заполнение из локального кэша профиля (playercache) — без единого сетевого запроса.
        Bukkit.getScheduler().runTaskLaterAsynchronously(owner, PlayerHeadCache::seedFromLocalCache, 100L);
    }

    public static void shutdown() {
        QUEUE.clear();
        PENDING.clear();
        plugin = null;
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
        if (KNOWN.containsKey(key(name))) return;
        request(name, player -> {
            if (player == null) return;
            try {
                if (slot < 0 || slot >= inventory.getSize()) return;
                ItemStack item = inventory.getItem(slot);
                if (item == null || item.getType() != Material.PLAYER_HEAD) return;
                ItemMeta itemMeta = item.getItemMeta();
                if (!(itemMeta instanceof SkullMeta)) return;
                applyOwner((SkullMeta) itemMeta, player);
                item.setItemMeta(itemMeta);
                inventory.setItem(slot, item);
            } catch (Throwable ignored) {
            }
        });
    }

    /** Ставит голову в инвентарь: мгновенно, если ник известен, иначе — как только станет известен. */
    public static void fillHead(Inventory inventory, int slot, ItemStack head, String name, Player onlinePlayer) {
        if (inventory == null || head == null) return;
        ItemMeta itemMeta = head.getItemMeta();
        if (itemMeta instanceof SkullMeta) {
            SkullMeta meta = (SkullMeta) itemMeta;
            if (onlinePlayer != null) {
                applyOwner(meta, onlinePlayer);
            } else {
                applyOwner(meta, name);
            }
            head.setItemMeta(meta);
        }
        inventory.setItem(slot, head);
        if (onlinePlayer == null) {
            updateSlotLater(inventory, slot, name);
        }
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
        try {
            Bukkit.getScheduler().runTask(owner, () -> {
                try {
                    entry.callback.accept(resolved);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ignored) {
        }
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
