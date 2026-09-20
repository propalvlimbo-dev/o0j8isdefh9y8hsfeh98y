package ru.rooyzee.elytrixclans.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MenuUtil {

    private static final int[] BLACK_PANE_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 16, 17, 18, 26
    };

    private static final int[] PURPLE_PANE_SLOTS = {
            27, 35,
            36, 37, 43, 44,
            46, 47, 48, 49, 50, 51, 52, 53
    };

    public static void applyLayout(Inventory inventory) {
        ItemStack blackPane = createPane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack purplePane = createPane(Material.PURPLE_STAINED_GLASS_PANE);
        for (int slot : BLACK_PANE_SLOTS) {
            if (slot < inventory.getSize()) inventory.setItem(slot, blackPane);
        }
        for (int slot : PURPLE_PANE_SLOTS) {
            if (slot < inventory.getSize()) inventory.setItem(slot, purplePane);
        }
    }

    public static ItemStack createPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.setLore(Collections.emptyList());
            pane.setItemMeta(meta);
        }
        return pane;
    }

    public static ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BLACK_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBНазад &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fВернуться в: &#F8BEFBПредыдущее меню"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для перехода"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(item, "arrowItem", "");
        return item;
    }

    public static ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.RED_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &cЗакрыть меню &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fВыход в игровой мир"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для закрытия"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(item, "closeItem", "");
        return item;
    }

    public static ItemStack createInfoItem(String pointsStr) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBИнформация &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПоинты клана: &#F8BEFB" + pointsStr));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}