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
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;

public class GlowManager {

    // Пакетный слушатель читает эту карту, поэтому она конкурентная.
    private final Map<String, Color> clanColors = new ConcurrentHashMap<>();
    private final ProtocolManager protocolManager;

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
        for (ClanMember member : clan.getMemberList()) {
            Player player = member.getPlayer();
            if (player == null || !player.isOnline()) continue;
            resetArmorFor(player, clan);
        }
    }

    public void removeClanColor(Clan clan) {
        if (clan == null) return;
        clanColors.remove(key(clan));
        clan.setGlowColor(null);
        disableGlow(clan);
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

    public void refreshClanArmor(Clan clan) {
        if (clan == null || !clan.isGlow()) return;
        sendClanArmorNow(clan);
        // Повтор через тик: сразу после клика по цвету клиент ещё может получить от сервера
        // «настоящую» экипировку (обновление инвентаря после закрытия меню) и перекрыть наш
        // пакет — из-за этого шлем у союзников появлялся не сразу, а только после перезахода
        // или смены брони.
        scheduleRefresh(clan, 1L);
        scheduleRefresh(clan, 5L);
    }

    private void sendClanArmorNow(Clan clan) {
        if (clan == null || !clan.isGlow()) return;
        for (ClanMember member : clan.getMemberList()) {
            if (member == null) continue;
            Player player = member.getPlayer();
            if (player == null || !player.isOnline()) continue;
            sendColoredArmorToAllies(player, clan);
        }
    }

    private void scheduleRefresh(final Clan clan, long delayTicks) {
        final Main main = Main.getInstance();
        if (main == null || !main.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTaskLater(main, new Runnable() {
                @Override
                public void run() {
                    if (main.getGlowManager() == null) return;
                    sendClanArmorNow(clan);
                }
            }, delayTicks);
        } catch (IllegalStateException ignored) {
            // Плагин выключается — повторять уже некому.
        }
    }

    public void refreshPlayer(Player player) {
        if (player == null) return;
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;
        Clan clan = main.getClanManager().getPlayerClan(player);
        if (clan == null || !clan.isGlow()) return;
        // Обе стороны: и союзники видят цветной шлем игрока, и сам игрок — цветные шлемы союзников.
        sendColoredArmorToAllies(player, clan);
        sendAlliesArmorTo(player, clan);
    }

    /** Показывает игроку подкрашенные шлемы его союзников (вход в игру, респавн, смена мира). */
    private void sendAlliesArmorTo(Player viewer, Clan clan) {
        if (viewer == null || clan == null) return;
        Color color = clanColors.get(key(clan));
        if (color == null) return;
        ItemStack coloredHelmet = createColoredHelmet(color);
        for (ClanMember member : clan.getMemberList()) {
            if (member == null) continue;
            Player ally = member.getPlayer();
            if (ally == null || !ally.isOnline()) continue;
            if (ally.equals(viewer)) continue;
            sendEquipmentPacket(viewer, ally.getEntityId(), EnumWrappers.ItemSlot.HEAD, coloredHelmet);
        }
    }

    private void sendColoredArmorToAllies(Player wearer, Clan clan) {
        Color color = clanColors.get(key(clan));
        if (color == null) return;

        ItemStack coloredHelmet = createColoredHelmet(color);

        for (ClanMember member : clan.getMemberList()) {
            Player viewer = member.getPlayer();
            if (viewer == null || !viewer.isOnline()) continue;
            if (viewer.equals(wearer)) continue;
            sendEquipmentPacket(viewer, wearer.getEntityId(), EnumWrappers.ItemSlot.HEAD, coloredHelmet);
        }
    }

    public void resetArmorFor(Player wearer, Clan clan) {
        if (wearer == null || clan == null) return;
        ItemStack realHelmet = wearer.getInventory().getHelmet();
        if (realHelmet == null) realHelmet = new ItemStack(Material.AIR);

        for (ClanMember member : clan.getMemberList()) {
            Player viewer = member.getPlayer();
            if (viewer == null || !viewer.isOnline()) continue;
            if (viewer.equals(wearer)) continue;
            sendEquipmentPacket(viewer, wearer.getEntityId(), EnumWrappers.ItemSlot.HEAD, realHelmet);
        }
    }

    public void resetArmorForEveryone(Player wearer) {
        if (wearer == null) return;
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;
        Clan clan = main.getClanManager().getPlayerClan(wearer);
        if (clan != null) {
            resetArmorFor(wearer, clan);
        }
    }

    /**
     * Полный сброс подсветки для игрока, покинувшего клан (выход, кик, роспуск).
     *
     * Клан передаётся явно: к моменту вызова игрока уже могли убрать из memberList, и
     * getPlayerClan() вернул бы null — именно поэтому подкрашенный шлем раньше «застревал»
     * у обеих сторон до перезахода.
     *
     * Сбрасываем в обе стороны:
     *   1) бывшие союзники видят настоящий шлем ушедшего;
     *   2) ушедший видит настоящие шлемы бывших союзников.
     */
    public void resetGlowForLeaver(Player leaver, Clan clan) {
        if (leaver == null || clan == null) return;

        ItemStack leaverHelmet = realHelmet(leaver);
        for (ClanMember member : clan.getMemberList()) {
            if (member == null) continue;
            Player viewer = member.getPlayer();
            if (viewer == null || !viewer.isOnline()) continue;
            if (viewer.equals(leaver)) continue;
            // Бывшему союзнику — настоящий шлем ушедшего.
            sendEquipmentPacket(viewer, leaver.getEntityId(), EnumWrappers.ItemSlot.HEAD, leaverHelmet);
            // Ушедшему — настоящий шлем бывшего союзника.
            sendEquipmentPacket(leaver, viewer.getEntityId(), EnumWrappers.ItemSlot.HEAD, realHelmet(viewer));
        }

        // Повтор через тик: телепорт/респавн/закрытие меню в тот же тик может заново
        // прислать клиенту устаревшую экипировку.
        final Main main = Main.getInstance();
        if (main == null || !main.isEnabled()) return;
        try {
            Bukkit.getScheduler().runTaskLater(main, new Runnable() {
                @Override
                public void run() {
                    if (!leaver.isOnline()) return;
                    ItemStack helmet = realHelmet(leaver);
                    for (ClanMember member : clan.getMemberList()) {
                        if (member == null) continue;
                        Player viewer = member.getPlayer();
                        if (viewer == null || !viewer.isOnline() || viewer.equals(leaver)) continue;
                        sendEquipmentPacket(viewer, leaver.getEntityId(), EnumWrappers.ItemSlot.HEAD, helmet);
                        sendEquipmentPacket(leaver, viewer.getEntityId(), EnumWrappers.ItemSlot.HEAD, realHelmet(viewer));
                    }
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
        for (Clan clan : Main.getInstance().getClanManager().getClans()) {
            if (clan.isGlow()) {
                disableGlow(clan);
            }
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