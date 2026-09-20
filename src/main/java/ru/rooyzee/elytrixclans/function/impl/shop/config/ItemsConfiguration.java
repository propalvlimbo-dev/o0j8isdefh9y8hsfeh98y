package ru.rooyzee.elytrixclans.function.impl.shop.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.function.impl.shop.object.ShopItem;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;
import ru.rooyzee.elytrixclans.utils.ShopItemMeta;

/**
 * Загрузка ассортимента магазина из shop/shop_item.yml.
 *
 * Структура файла плоская: секция items, внутри — записи с полями
 * name / material / amount / price / level / lore / commands + свойства ShopItemMeta.
 * Слоты не указываются: порядок позиций определяется сортировкой (уровень, затем цена),
 * а раскладка по страницам считается автоматически в ShopFunction.
 */
public class ItemsConfiguration {

    /** NBT-ключ, по которому BuyManager узнаёт позицию магазина в витрине. */
    public static final String NBT_SHOP_ITEM = "clanShopItem";

    private File configFile;
    private FileConfiguration fileConfiguration;
    private List<ShopItem> items = Collections.emptyList();

    public ItemsConfiguration() {
        setupYml();
    }

    public void reloadYml() {
        fileConfiguration = YamlConfiguration.loadConfiguration(configFile);
        loadItems();
    }

    private void setupYml() {
        File shopFolder = new File(Main.getInstance().getDataFolder(), "shop");
        if (!shopFolder.exists() && !shopFolder.mkdirs()) {
            Bukkit.getLogger().warning("[ElytrixClans] Не удалось создать папку shop/");
        }

        configFile = new File(shopFolder, "shop_item.yml");
        if (!configFile.exists()) {
            try {
                Main.getInstance().saveResource("shop/shop_item.yml", false);
            } catch (Exception ignored) {
            }
        }
        fileConfiguration = YamlConfiguration.loadConfiguration(configFile);
        loadItems();
    }

    private void loadItems() {
        List<ShopItem> loaded = new ArrayList<>();
        ConfigurationSection section = fileConfiguration.getConfigurationSection("items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) continue;
                try {
                    loaded.add(readItem(key, entry));
                } catch (Exception e) {
                    Bukkit.getLogger().warning("[ElytrixClans] Позиция магазина '" + key
                            + "' пропущена: " + e.getMessage());
                }
            }
        }
        // Порядок витрины: сначала доступное на низких уровнях, внутри уровня — от дешёвого
        // к дорогому. Так «сначала предметы хуже, дальше открываются новые» выполняется само.
        loaded.sort(Comparator
                .comparingInt(ShopItem::getRequiredLevel)
                .thenComparingDouble(ShopItem::getPrice)
                .thenComparing(ShopItem::getId));
        items = Collections.unmodifiableList(loaded);
    }

    private ShopItem readItem(String key, ConfigurationSection entry) {
        String material = entry.getString("material", "STONE");
        if (Material.matchMaterial(material.toUpperCase()) == null) {
            throw new IllegalArgumentException("неизвестный материал " + material);
        }
        String name = HexUtil.translateHexColorCodes(entry.getString("name", key));
        double price = entry.getDouble("price", 0.0);
        int amount = entry.getInt("amount", 1);
        int level = entry.getInt("level", 1);
        List<String> lore = new ArrayList<>();
        for (String line : entry.getStringList("lore")) {
            lore.add(HexUtil.translateHexColorCodes(line));
        }
        List<String> commands = entry.getStringList("commands");
        int cooldown = entry.getInt("cooldown", 0);
        return new ShopItem(key, name, material.toUpperCase(), lore, price, amount, level,
                commands, cooldown, ShopItemMeta.of(entry));
    }

    /** Весь ассортимент в порядке витрины. */
    public List<ShopItem> getItems() {
        return items;
    }

    public ShopItem getItem(String id) {
        if (id == null) return null;
        for (ShopItem item : items) {
            if (item.getId().equalsIgnoreCase(id)) return item;
        }
        return null;
    }

    /**
     * Строит «чистый» предмет позиции — ровно то, что получит покупатель.
     * Для командных позиций это только иконка: сама выдача идёт консольными командами.
     */
    public static ItemStack buildRawItem(ShopItem shopItem) {
        Material material = Material.matchMaterial(shopItem.getMaterial());
        if (material == null) material = Material.STONE;
        ItemStack stack = new ItemStack(material, Math.min(material.getMaxStackSize(), shopItem.getAmount()));
        ShopItemMeta.apply(stack, shopItem.getMeta());
        return stack;
    }

    /** Предмет-витрина: тот же предмет плюс оформление магазина и NBT с id позиции. */
    public static ItemStack buildDisplayItem(ShopItem shopItem, List<String> lore) {
        ItemStack stack = buildRawItem(shopItem);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(shopItem.getName());
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(stack, NBT_SHOP_ITEM, shopItem.getId());
        return stack;
    }
}
