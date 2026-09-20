package ru.rooyzee.elytrixclans.function.impl.glow;

import java.util.Arrays;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.permission.Permissions;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

public class GlowFunction implements InventoryHolder {

    private final Inventory inventory;

    private static final String[] COLORS_HEX = {
            "#FF0000", "#FF7A00", "#FFEA00", "#00FF3C",
            "#00FFC8", "#00A2FF", "#3C00FF", "#B400FF",
            "#FF00E1", "#FF6EC7", "#FFFFFF", "#000000",
            "#8B4513", "#4B0082", "#FFD700", "#00FF00",
            "#1E90FF", "#DC143C", "#00CED1", "#FF69B4",
            "#7FFF00", "#FF4500", "#DA70D6", "#40E0D0"
    };

    /** Названия цветов — в том же порядке, что и COLORS_HEX; идут в меню и в сообщение игроку. */
    private static final String[] COLORS_NAME = {
            "Красный", "Оранжевый", "Жёлтый", "Зелёный",
            "Мятный", "Голубой", "Синий", "Фиолетовый",
            "Пурпурный", "Розовый", "Белый", "Чёрный",
            "Коричневый", "Индиго", "Золотой", "Лаймовый",
            "Небесный", "Малиновый", "Бирюзовый", "Ярко-розовый",
            "Салатовый", "Огненный", "Орхидея", "Аквамарин"
    };

    /** Название цвета по hex; если цвета нет в палитре — сам hex. */
    public static String colorName(String hex) {
        if (hex == null) return "";
        for (int i = 0; i < COLORS_HEX.length && i < COLORS_NAME.length; i++) {
            if (COLORS_HEX[i].equalsIgnoreCase(hex)) return COLORS_NAME[i];
        }
        return hex;
    }

    public GlowFunction() {
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lПодсветка союзников"));

        MenuUtil.applyLayout(inventory);

        int[] colorSlots = {
                11, 12, 13, 14, 15,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                38, 39, 40, 41, 42
        };

        for (int i = 0; i < COLORS_HEX.length && i < colorSlots.length; i++) {
            String hex = COLORS_HEX[i];
            Color color = GlowManager.colorFromHex(hex);
            ItemStack armor = new ItemStack(Material.LEATHER_CHESTPLATE);
            LeatherArmorMeta armorMeta = (LeatherArmorMeta) armor.getItemMeta();
            String colorName = i < COLORS_NAME.length ? COLORS_NAME[i] : hex;
            if (armorMeta != null) {
                armorMeta.setDisplayName(HexUtil.translateHexColorCodes("&7« &" + hex + colorName + " &7»"));
                armorMeta.setColor(color);
                armorMeta.setLore(Arrays.asList(
                        HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                        HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЦвет свечения: &" + hex + colorName),
                        HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                        HexUtil.translateHexColorCodes("&7● &fНажмите для установки")
                ));
                armor.setItemMeta(armorMeta);
            }
            NBTUtil.addItemNBT(armor, "glow_color", hex);
            inventory.setItem(colorSlots[i], armor);
        }

        ItemStack removeGlow = new ItemStack(Material.BARRIER);
        ItemMeta removeMeta = removeGlow.getItemMeta();
        if (removeMeta != null) {
            removeMeta.setDisplayName(HexUtil.translateHexColorCodes("&7« &cУдалить подсветку &7»"));
            removeMeta.setLore(Arrays.asList(
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСбросить свечение: &cУдалить"),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&7● &fНажмите для сброса")
            ));
            removeGlow.setItemMeta(removeMeta);
        }
        NBTUtil.addItemNBT(removeGlow, "remove_glow", "");
        inventory.setItem(49, removeGlow);

        inventory.setItem(45, MenuUtil.createBackButton());
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();

        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan == null) return;
        if (Main.getInstance().getGlowManager() == null) return;

        // Игрока могли выгнать из клана, пока меню открыто — без проверки был NPE.
        ClanMember clicker = Main.getInstance().getClanManager().getPlayerClanMember(player);
        if (clicker == null || !clicker.getRole().getPermissions().contains(Permissions.GLOW)) {
            ConfigUtil.sendMessage(player, "messages.noPermission", null);
            return;
        }

        if (NBTUtil.hasItemNBT(item, "remove_glow")) {
            Main.getInstance().getGlowManager().removeClanColor(clan);
            ConfigUtil.sendMessage(player, "messages.glowRemoved", null);
            return;
        }

        if (NBTUtil.hasItemNBT(item, "glow_color")) {
            String hex = NBTUtil.getNBTvalue(item, "glow_color");
            if (hex == null) return;

            if (clan.isPvp()) {
                ConfigUtil.sendMessage(player, "messages.glowPvpConflict", null);
                return;
            }

            Color color = GlowManager.colorFromHexOrNull(hex);
            if (color == null) return;
            Main.getInstance().getGlowManager().setGlowColor(clan, color);
            // Сообщаем всему клану, какой цвет теперь у подсветки, — сам цвет виден в тексте.
            Map<String, String> holder = ConfigUtil.setHolder(
                    new String[]{"%color%", "%hex%", "%player%"},
                    new String[]{"&" + hex + colorName(hex), hex, player.getName()});
            for (ClanMember m : clan.getMemberList()) {
                if (m == null) continue;
                Player online = m.getPlayer();
                if (online != null && online.isOnline()) {
                    ConfigUtil.sendMessage(online, "messages.glowColorSet", holder);
                }
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}