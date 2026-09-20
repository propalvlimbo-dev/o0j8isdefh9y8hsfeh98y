package ru.rooyzee.elytrixclans.function.impl.shop.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.function.impl.shop.object.ArrowItem;
import ru.rooyzee.elytrixclans.function.impl.shop.object.ShopItem;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;
import ru.rooyzee.elytrixclans.utils.ShopItemMeta;

public class ItemsConfiguration {

    private File configFile;
    private FileConfiguration fileConfiguration;

    public ItemsConfiguration() {
        setupYml();
    }

    public void reloadYml() {
        fileConfiguration = YamlConfiguration.loadConfiguration(configFile);
    }

    private void setupYml() {
        File shopFolder = new File(Main.getInstance().getDataFolder(), "shop");
        if (!shopFolder.exists()) shopFolder.mkdirs();

        configFile = new File(shopFolder, "shop_item.yml");
        if (!configFile.exists()) {
            try {
                Main.getInstance().saveResource("shop/shop_item.yml", false);
            } catch (Exception ignored) {
            }
        }
        fileConfiguration = YamlConfiguration.loadConfiguration(configFile);
    }

    public List<ArrowItem> serializeArrowItems(String category) {
        List<ArrowItem> items = new ArrayList<>();
        ConfigurationSection section = fileConfiguration.getConfigurationSection(category);
        if (section == null) return items;
        for (String key : section.getKeys(false)) {
            String path = category + "." + key;
            String material = fileConfiguration.getString(path + ".material", "");
            if (!material.equalsIgnoreCase("TIPPED_ARROW")) continue;
            String effect = fileConfiguration.getString(path + ".effect", "");
            if (PotionEffectType.getByName(effect) == null) {
                Bukkit.getLogger().warning("[ElytrixClans] Неизвестный эффект стрелы: " + effect);
                continue;
            }
            String name = HexUtil.translateHexColorCodes(fileConfiguration.getString(path + ".name", ""));
            int price = fileConfiguration.getInt(path + ".price");
            int amplifier = fileConfiguration.getInt(path + ".amplifier");
            int slot = fileConfiguration.getInt(path + ".slot");
            int count = fileConfiguration.getInt(path + ".count");
            int duration = fileConfiguration.getInt(path + ".duration");
            List<String> lores = new ArrayList<>();
            for (String line : fileConfiguration.getStringList(path + ".lore")) {
                lores.add(HexUtil.translateHexColorCodes(line));
            }
            items.add(new ArrowItem(name, material, lores, price, count, slot, amplifier, duration, effect, path));
        }
        return items;
    }

    public List<ShopItem> serializeItems(String category) {
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection section = fileConfiguration.getConfigurationSection(category);
        if (section == null) return items;
        for (String key : section.getKeys(false)) {
            String path = category + "." + key;
            String material = fileConfiguration.getString(path + ".material", "");
            // TIPPED_ARROW с распознанным legacy-эффектом рисует стрелочная ветка (она же
            // добавляет цвет и подпись эффекта). Стрелы «из креатива» держат эффект в базовых
            // данных зелья (effect в конфиге пуст) — их ведём общей веткой, иначе позиция
            // вообще не появилась бы в меню.
            if (material.equalsIgnoreCase("TIPPED_ARROW")
                    && PotionEffectType.getByName(fileConfiguration.getString(path + ".effect", "")) != null) {
                continue;
            }
            String name = HexUtil.translateHexColorCodes(fileConfiguration.getString(path + ".name", ""));
            int price = fileConfiguration.getInt(path + ".price");
            int slot = fileConfiguration.getInt(path + ".slot");
            int count = fileConfiguration.getInt(path + ".count");
            List<String> lores = new ArrayList<>();
            for (String line : fileConfiguration.getStringList(path + ".lore")) {
                lores.add(HexUtil.translateHexColorCodes(line));
            }
            items.add(new ShopItem(name, material, lores, price, count, slot, path));
        }
        return items;
    }

    public void setItems(Inventory inventory, String category) {
        for (ArrowItem arrow : serializeArrowItems(category)) {
            try {
                ItemStack itemStack = new ItemStack(Material.TIPPED_ARROW);
                PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(arrow.getName());
                    meta.setColor(PotionEffectType.getByName(arrow.getEffect()).getColor());
                    meta.addCustomEffect(new PotionEffect(
                            PotionEffectType.getByName(arrow.getEffect()),
                            arrow.getDuration() * 20 * 10,
                            arrow.getAmplifier()), true);
                    meta.setLore(arrow.getLore());
                    meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                    itemStack.setItemMeta(meta);
                }
                itemStack.setAmount(safeAmount(itemStack, arrow.getCount()));
                ShopItemMeta.apply(itemStack, metaOf(arrow.getMetaPath()));
                NBTUtil.addItemNBT(itemStack, "shopItem",
                        "shopItem_wtf_" + arrow.getMaterial() + "_wtf_" + itemStack.getAmount() + "_wtf_" + arrow.getPrice() + "_wtf_" + arrow.getEffect());
                if (isValidSlot(inventory, arrow.getSlot())) inventory.setItem(arrow.getSlot(), itemStack);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[ElytrixClans] Не удалось построить стрелу '" + arrow.getName() + "': " + e.getMessage());
            }
        }

        for (ShopItem item : serializeItems(category)) {
            try {
                ItemStack itemStack = new ItemStack(Material.valueOf(item.getMaterial()));
                ItemMeta meta = itemStack.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(item.getName());
                    meta.setLore(item.getLore());
                    itemStack.setItemMeta(meta);
                }
                itemStack.setAmount(safeAmount(itemStack, item.getCount()));
                ShopItemMeta.apply(itemStack, metaOf(item.getMetaPath()));
                NBTUtil.addItemNBT(itemStack, "shopItem",
                        "shopItem_wtf_" + item.getMaterial() + "_wtf_" + itemStack.getAmount() + "_wtf_" + item.getPrice());
                if (isValidSlot(inventory, item.getSlot())) inventory.setItem(item.getSlot(), itemStack);
            } catch (Exception e) {
                Bukkit.getLogger().warning("[ElytrixClans] Не удалось построить предмет магазина '"
                        + item.getMaterial() + "': " + e.getMessage());
            }
        }
    }

    /** Зачарования, эффекты зелий, прочность и флаги позиции — из той же записи конфига. */
    private ShopItemMeta.Reader metaOf(String path) {
        if (path == null) return ShopItemMeta.empty();
        return ShopItemMeta.of(fileConfiguration.getConfigurationSection(path));
    }

    /** Количество не должно превышать макс. стак материала и не может быть нулевым. */
    private static int safeAmount(ItemStack item, int count) {
        int max = item.getMaxStackSize();
        int amount = Math.max(1, count);
        return max > 0 ? Math.min(amount, max) : amount;
    }

    /** Слот вне инвентаря (опечатка в конфиге) — это исключение на каждом открытии меню. */
    private static boolean isValidSlot(Inventory inventory, int slot) {
        return slot >= 0 && slot < inventory.getSize();
    }
}