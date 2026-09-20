package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

/**
 * Меню выбора: какую часть магазина редактировать — категорию предметов или набор.
 * Оформление — как у обычного магазина (рамка, info-часы, кнопка выхода).
 */
public class EditMenuInventory implements InventoryHolder {

    private final Inventory inventory;

    public EditMenuInventory(Player player) {
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lРедактор магазина"));

        MenuUtil.applyLayout(inventory);

        YamlConfiguration config = ShopEditStorage.loadYaml();

        List<String> categories = ShopEditStorage.categories(config);

        int categorySlot = 20;
        for (String category : categories) {
            if (categorySlot > 24) break;
            inventory.setItem(categorySlot, createButton(categoryButtonMaterial(category),
                    "&7« &#F8BEFB" + ShopEditStorage.label(category) + " &7»",
                    "&#F8BEFB&l┃ &fПозиций в конфиге: &#F8BEFB" + ShopEditStorage.previewCategory(config, category).size(),
                    "&#F8BEFB&l┃ &fЦены новых позиций: &#F8BEFB" + ShopEditStorage.defaultPrice() + " &7поинтов",
                    "edit_category", category));
            categorySlot += 2;
        }

        int kitSlot = 28;
        int shown = 0;
        int total = 0;
        if (Main.getInstance() != null && Main.getInstance().getKitManager() != null) {
            List<Kit> kits = new ArrayList<>();
            for (Kit kit : Main.getInstance().getKitManager().getKits()) {
                if (kit != null) kits.add(kit);
            }
            total = kits.size();
            for (Kit kit : kits) {
                if (kitSlot > 42) break;
                Material icon = matchMaterial(kit.getIconMaterial());
                inventory.setItem(kitSlot, createButton(icon,
                        "&7« &#F8BEFB" + kit.getDisplayName() + " &7»",
                        "&#F8BEFB&l┃ &fПредметов в наборе: &#F8BEFB" + kit.getItems().size(),
                        "&#F8BEFB&l┃ &fЦена набора: &#F8BEFB" + kit.getPrice() + " &7поинтов",
                        "edit_kit", kit.getId()));
                kitSlot++;
                shown++;
            }
        }

        inventory.setItem(4, createInfoItem(categories.size(), shown, total));
        inventory.setItem(45, MenuUtil.createCloseButton());
    }

    private static Material matchMaterial(String name) {
        if (name == null || name.isEmpty()) return Material.CHEST;
        try {
            Material material = Material.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
            return material == Material.AIR ? Material.CHEST : material;
        } catch (IllegalArgumentException e) {
            return Material.CHEST;
        }
    }

    private static Material categoryButtonMaterial(String category) {
        if (category == null) return Material.CHEST;
        if ("shulker".equalsIgnoreCase(category)) return Material.WHITE_SHULKER_BOX;
        if ("spawner".equalsIgnoreCase(category)) return Material.SPAWNER;
        if ("things".equalsIgnoreCase(category)) return Material.END_CRYSTAL;
        return Material.CHEST;
    }

    private ItemStack createButton(Material material, String name, String line1, String line2,
                                   String nbtKey, String nbtValue) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(name));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes(line1));
            lore.add(HexUtil.translateHexColorCodes(line2));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите, чтобы открыть редактор"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(item, nbtKey, nbtValue);
        return item;
    }

    private ItemStack createInfoItem(int categories, int kitsShown, int kitsTotal) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBРедактор магазина &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fКатегории: &#F8BEFB" + categories));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНаборов: &#F8BEFB" + kitsTotal
                    + (kitsShown < kitsTotal ? " &7(показано " + kitsShown + ")" : "")));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fВыбери категорию или набор"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fПравки сохраняются при закрытии окна"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
