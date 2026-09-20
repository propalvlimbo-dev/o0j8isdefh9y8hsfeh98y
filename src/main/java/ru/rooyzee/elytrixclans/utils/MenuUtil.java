package ru.rooyzee.elytrixclans.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.level.Level;

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

    /**
     * Слоты, которые магазин освобождает от стекла под товар.
     * В остальных меню рамка остаётся прежней.
     */
    public static final int[] SHOP_FREED_SLOTS = {10, 16, 37, 43};

    /** Убирает стекло со слотов витрины магазина. */
    public static void clearShopFreedSlots(Inventory inventory) {
        for (int slot : SHOP_FREED_SLOTS) {
            if (slot < inventory.getSize()) inventory.setItem(slot, null);
        }
    }

    private static final ThreadLocal<DecimalFormat> MONEY_FORMAT = new ThreadLocal<DecimalFormat>() {
        @Override
        protected DecimalFormat initialValue() {
            DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
            symbols.setGroupingSeparator(' ');
            return new DecimalFormat("#,##0.##", symbols);
        }
    };

    /** Человекочитаемая сумма монет: 1 250 000, 99.5 и т.п. */
    public static String money(double value) {
        return MONEY_FORMAT.get().format(value);
    }

    /**
     * Количество опыта для показа игроку.
     *
     * Опыт внутри хранится как double, поэтому целые значения печатались как «0.0»
     * и «250.0». Целое число выводим без дробной части, дробное — с одним знаком.
     */
    public static String exp(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        double rounded = Math.round(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) return String.valueOf((long) rounded);
        return MONEY_FORMAT.get().format(rounded);
    }

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

    /**
     * Шапка меню магазина: баланс покупателя в монетах Vault, уровень и опыт клана.
     * Поинтов в плагине больше нет — валюта одна, обычные монеты.
     */
    public static ItemStack createInfoItem(Clan clan, double balance) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBИнформация &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fВаш баланс: &#F8BEFB" + money(balance) + " монет"));
            if (clan != null) {
                Level level = LevelUtil.getClanLevel(clan.getExp());
                int levelNumber = level != null ? level.getLevel() : 1;
                lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУровень клана: &#F8BEFB" + levelNumber));
                lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fОпыт клана: &#F8BEFB" + exp(clan.getExp())));
            }
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}