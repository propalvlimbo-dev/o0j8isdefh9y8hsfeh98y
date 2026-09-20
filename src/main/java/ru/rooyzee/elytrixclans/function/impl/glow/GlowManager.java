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
        clanColors.put(key(clan), color);
        clan.setGlow(true);
        refreshClanArmor(clan);
    }

    public void enableGlow(Clan clan) {
        if (!clanColors.containsKey(key(clan))) {
            clanColors.put(key(clan), Color.fromRGB(255, 105, 180));
        }
        clan.setGlow(true);
        refreshClanArmor(clan);
    }

    public void disableGlow(Clan clan) {
        clan.setGlow(false);
        for (ClanMember member : clan.getMemberList()) {
            Player player = member.getPlayer();
            if (player == null || !player.isOnline()) continue;
            resetArmorFor(player, clan);
        }
    }

    public void removeClanColor(Clan clan) {
        clanColors.remove(key(clan));
        disableGlow(clan);
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
        if (!clan.isGlow()) return;
        for (ClanMember member : clan.getMemberList()) {
            Player player = member.getPlayer();
            if (player == null || !player.isOnline()) continue;
            sendColoredArmorToAllies(player, clan);
        }
    }

    public void refreshPlayer(Player player) {
        if (player == null) return;
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan == null || !clan.isGlow()) return;
        sendColoredArmorToAllies(player, clan);
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
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(wearer);
        if (clan != null) {
            resetArmorFor(wearer, clan);
        }
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