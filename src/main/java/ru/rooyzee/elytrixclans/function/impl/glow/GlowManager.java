package ru.rooyzee.elytrixclans.function.impl.glow;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.Plugin;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;

public class GlowManager {

    // Пакетный слушатель читает эту карту, поэтому она конкурентная.
    private final Map<String, Color> clanColors = new ConcurrentHashMap<>();
    private final ProtocolManager protocolManager;
    private int syncTaskId = -1;

    public GlowManager() {
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    /** Ключ подсветки по названию клана: регистронезависимый и без зависимости от локали JVM. */
    private static String key(Clan clan) {
        return clan == null || clan.getName() == null ? "" : clan.getName().toLowerCase(Locale.ROOT);
    }

    public void setGlowColor(Clan clan, Color color) {
        if (clan == null || color == null) return;
        clanColors.put(key(clan), color);
        clan.setGlow(true);
        // Цвет держим и в самом клане: карта живёт только в памяти, и после рестарта
        // флаг glow=true оставался, а цвета не было — подсветка «тихо» пропадала.
        clan.setGlowColor(rgbToHex(color));
        refreshClanArmor(clan);
    }

    public void enableGlow(Clan clan) {
        if (clan == null) return;
        if (!clanColors.containsKey(key(clan))) {
            Color restored = colorFromHexOrNull(clan.getGlowColor());
            clanColors.put(key(clan), restored != null ? restored : Color.fromRGB(255, 105, 180));
        }
        clan.setGlow(true);
        clan.setGlowColor(rgbToHex(clanColors.get(key(clan))));
        refreshClanArmor(clan);
    }

    public void disableGlow(Clan clan) {
        if (clan == null) return;
        clan.setGlow(false);
        syncClan(clan);
    }

    public void removeClanColor(Clan clan) {
        if (clan == null) return;
        clan.setGlow(false);
        clanColors.remove(key(clan));
        clan.setGlowColor(null);
        syncClan(clan);
    }

    /**
     * Восстановление подсветки из сохранённых данных клана (вызывается после загрузки clans.yml).
     * Раньше цвет жил только в оперативной памяти и терялся при каждом рестарте сервера.
     */
    public void restoreFromStorage() {
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;
        for (Clan clan : main.getClanManager().getClans()) {
            if (clan == null || !clan.isGlow()) continue;
            Color color = colorFromHexOrNull(clan.getGlowColor());
            if (color == null) {
                // Цвета в файле нет (например, данные из старой версии) — подсветку не включаем,
                // иначе союзники увидят «случайный» розовый шлем.
                clan.setGlow(false);
                continue;
            }
            clanColors.put(key(clan), color);
        }
    }

    private static String rgbToHex(Color color) {
        if (color == null) return null;
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    public Color getGlowColor(Clan clan) {
        if (clan == null || clan.getName() == null) return null;
        return clanColors.get(key(clan));
    }

    /**
     * Цвет, который реально должен видеть союзник прямо сейчас.
     *
     * Единая точка решения и для пакетного слушателя, и для ручной рассылки: раньше слушатель
     * гасил подсветку на время клановой PvP, а ручная рассылка — нет, и подкрашенный шлем
     * «застревал» на клиенте до перезахода.
     */
    public Color effectiveColor(Clan clan) {
        if (clan == null || !clan.isGlow() || clan.isPvp()) return null;
        return clanColors.get(key(clan));
    }

    public boolean hasGlow(Clan clan) {
        return clanColors.containsKey(key(clan));
    }

    /**
     * Быстрая проверка для пакетного слушателя: есть ли вообще кланы с подсветкой.
     * Позволяет не делать ничего на каждом пакете экипировки, когда подсветкой никто не пользуется.
     */
    public boolean hasAnyGlow() {
        return !clanColors.isEmpty();
    }

    // ------------------------------------------------------------------
    //  Синхронизация шлемов
    // ------------------------------------------------------------------

    /**
     * Рассылает актуальный шлем игрока ВСЕМ, кто его сейчас видит.
     *
     * Союзникам уходит подкрашенный кожаный шлем, всем остальным — настоящий. Важно именно
     * «всем остальным», а не только клану: подделка живёт на клиенте до следующего пакета
     * экипировки, поэтому игрок, вышедший из клана (или наблюдавший за бывшим союзником),
     * продолжал видеть кожаный шлем, пока не перезайдёт.
     */
    public void syncHelmetFor(Player wearer) {
        if (wearer == null || !wearer.isOnline()) return;
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;

        Clan clan = main.getClanManager().getPlayerClan(wearer);
        Color color = effectiveColor(clan);
        ItemStack real = realHelmet(wearer);
        ItemStack fake = color != null ? createColoredHelmet(color) : null;

        List<Player> nearby = wearer.getWorld().getPlayers();
        for (int i = 0, size = nearby.size(); i < size; i++) {
            Player viewer = nearby.get(i);
            if (viewer == null || viewer.equals(wearer) || !viewer.isOnline()) continue;
            if (!canTrack(viewer, wearer)) continue;
            boolean ally = fake != null && main.getClanManager().getPlayerClan(viewer) == clan;
            sendEquipmentPacket(viewer, wearer.getEntityId(), EnumWrappers.ItemSlot.HEAD, ally ? fake : real);
        }
    }

    /** Обратное направление: присылает зрителю актуальные шлемы всех, кого он видит. */
    public void syncViewer(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;

        Clan viewerClan = main.getClanManager().getPlayerClan(viewer);
        List<Player> nearby = viewer.getWorld().getPlayers();
        for (int i = 0, size = nearby.size(); i < size; i++) {
            Player wearer = nearby.get(i);
            if (wearer == null || wearer.equals(viewer) || !wearer.isOnline()) continue;
            if (!canTrack(viewer, wearer)) continue;
            Clan wearerClan = main.getClanManager().getPlayerClan(wearer);
            Color color = wearerClan != null && wearerClan == viewerClan ? effectiveColor(wearerClan) : null;
            ItemStack helmet = color != null ? createColoredHelmet(color) : realHelmet(wearer);
            sendEquipmentPacket(viewer, wearer.getEntityId(), EnumWrappers.ItemSlot.HEAD, helmet);
        }
    }

    /** Синхронизация по всему составу клана (включение/выключение подсветки, смена цвета). */
    public void syncClan(Clan clan) {
        if (clan == null) return;
        for (ClanMember member : clan.getMemberList()) {
            if (member == null) continue;
            Player player = member.getPlayer();
            if (player == null || !player.isOnline()) continue;
            syncHelmetFor(player);
            syncViewer(player);
        }
    }

    /**
     * Видит ли зритель носителя прямо сейчас: один мир, не скрыт и в пределах трекинга.
     * Пакеты для неотслеживаемых сущностей клиент всё равно выбрасывает.
     */
    private boolean canTrack(Player viewer, Player wearer) {
        if (viewer.getWorld() != wearer.getWorld()) return false;
        if (!viewer.canSee(wearer)) return false;
        int range = Math.max(64, Bukkit.getViewDistance() * 16);
        return viewer.getLocation().distanceSquared(wearer.getLocation()) <= (double) range * range;
    }

    public void refreshClanArmor(Clan clan) {
        if (clan == null) return;
        syncClan(clan);
        // Повтор через тик: сразу после клика по цвету клиент ещё может получить от сервера
        // «настоящую» экипировку (обновление инвентаря после закрытия меню) и перекрыть наш
        // пакет — из-за этого шлем у союзников появлялся не сразу, а только после перезахода
        // или смены брони.
        scheduleSync(clan, 1L);
        scheduleSync(clan, 5L);
    }

    private void scheduleSync(final Clan clan, long delayTicks) {
        final Main main = Main.getInstance();
        if (main == null || !main.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTaskLater(main, new Runnable() {
                @Override
                public void run() {
                    if (main.getGlowManager() == null) return;
                    syncClan(clan);
                }
            }, delayTicks);
        } catch (IllegalStateException ignored) {
            // Плагин выключается — повторять уже некому.
        }
    }

    public void refreshPlayer(Player player) {
        if (player == null) return;
        // Обе стороны: и окружающие видят правильный шлем игрока, и сам игрок — правильные
        // шлемы окружающих. Вызываем даже без клана: так снимается подделка, залипшая
        // на клиенте с прошлой сессии в клане.
        syncHelmetFor(player);
        syncViewer(player);
    }

    public void resetArmorFor(Player wearer, Clan clan) {
        syncHelmetFor(wearer);
    }

    public void resetArmorForEveryone(Player wearer) {
        syncHelmetFor(wearer);
    }

    /**
     * Полный сброс подсветки для игрока, покинувшего клан (выход, кик, роспуск).
     *
     * Клан передаётся явно: к моменту вызова игрока уже могли убрать из memberList, и
     * getPlayerClan() вернул бы null — именно поэтому подкрашенный шлем раньше «застревал»
     * у обеих сторон до перезахода.
     *
     * Сбрасываем в обе стороны и по ВСЕМ наблюдателям вокруг, а не только по составу клана:
     * подделку мог получить и тот, кто уже успел выйти из клана раньше.
     */
    public void resetGlowForLeaver(Player leaver, Clan clan) {
        if (leaver == null) return;
        syncHelmetFor(leaver);
        syncViewer(leaver);

        // Повтор через пару тиков: телепорт/респавн/закрытие меню в тот же тик может заново
        // прислать клиенту устаревшую экипировку.
        final Main main = Main.getInstance();
        if (main == null || !main.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTaskLater(main, new Runnable() {
                @Override
                public void run() {
                    if (!leaver.isOnline()) return;
                    syncHelmetFor(leaver);
                    syncViewer(leaver);
                }
            }, 2L);
        } catch (IllegalStateException ignored) {
        }
    }

    private static ItemStack realHelmet(Player player) {
        if (player == null) return new ItemStack(Material.AIR);
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null ? helmet : new ItemStack(Material.AIR);
    }

    public void removeAllGlow() {
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;
        for (Clan clan : main.getClanManager().getClans()) {
            if (clan != null && clan.isGlow()) {
                disableGlow(clan);
            }
        }
    }

    // ------------------------------------------------------------------
    //  Фоновое «лечение» залипших шлемов
    // ------------------------------------------------------------------

    /**
     * Раз в несколько секунд переотправляет корректный шлем носителям из кланов с подсветкой.
     *
     * Подделка живёт на клиенте до следующего пакета экипировки, а сервер шлёт его только при
     * РЕАЛЬНОМ изменении брони. Если у игрока шлема нет вовсе, настоящий «пустой» пакет не
     * придёт никогда — именно так кожаный шлем и оставался виден тем, кто уже не в клане.
     */
    public void startSyncTask(Plugin plugin) {
        if (plugin == null || syncTaskId != -1) return;
        syncTaskId = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override
            public void run() {
                if (clanColors.isEmpty()) return;
                Main main = Main.getInstance();
                if (main == null || main.getClanManager() == null) return;
                for (Clan clan : main.getClanManager().getClans()) {
                    if (clan == null || !hasGlow(clan)) continue;
                    for (ClanMember member : clan.getMemberList()) {
                        if (member == null) continue;
                        Player player = member.getPlayer();
                        if (player == null || !player.isOnline()) continue;
                        try {
                            syncHelmetFor(player);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }, 200L, 200L).getTaskId();
    }

    public void stopSyncTask() {
        if (syncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(syncTaskId);
            syncTaskId = -1;
        }
    }

    private ItemStack createColoredHelmet(Color color) {
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            helmet.setItemMeta(meta);
        }
        return helmet;
    }

    private void sendEquipmentPacket(Player viewer, int entityId, EnumWrappers.ItemSlot slot, ItemStack item) {
        try {
            PacketContainer packet = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            packet.getIntegers().write(0, entityId);
            List<Pair<EnumWrappers.ItemSlot, ItemStack>> pairs = new ArrayList<>();
            pairs.add(new Pair<>(slot, item));
            packet.getSlotStackPairLists().write(0, pairs);
            protocolManager.sendServerPacket(viewer, packet);
        } catch (Exception e) {
            Main.getInstance().getLogger().warning("Failed to send equipment packet: " + e.getMessage());
        }
    }

    public static Color colorFromHex(String hex) {
        Color color = colorFromHexOrNull(hex);
        return color != null ? color : Color.WHITE;
    }

    /** Тот же разбор hex, но без исключений на мусорном вводе (значения приходят из NBT предметов). */
    public static Color colorFromHexOrNull(String hex) {
        if (hex == null) return null;
        String h = hex.replace("#", "");
        if (h.length() < 6) return null;
        try {
            int r = Integer.parseInt(h.substring(0, 2), 16);
            int g = Integer.parseInt(h.substring(2, 4), 16);
            int b = Integer.parseInt(h.substring(4, 6), 16);
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
