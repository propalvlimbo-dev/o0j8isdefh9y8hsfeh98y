package ru.rooyzee.elytrixclans.function.impl.shop.config;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

public class ShopMenuConfiguration {

    private File file;
    private FileConfiguration config;

    public ShopMenuConfiguration() {
        load();
    }

    public void load() {
        File shopFolder = new File(Main.getInstance().getDataFolder(), "shop");
        if (!shopFolder.exists()) shopFolder.mkdirs();

        file = new File(shopFolder, "shop_menu.yml");
        if (!file.exists()) {
            try {
                Main.getInstance().saveResource("shop/shop_menu.yml", false);
            } catch (Exception ignored) {
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        load();
    }

    public String getTitle(String menuKey) {
        return HexUtil.translateHexColorCodes(config.getString("menus." + menuKey + ".title", "&#F8BEFB&lМеню"));
    }

    public List<CategoryButton> getButtons(String menuKey) {
        List<CategoryButton> result = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("menus." + menuKey + ".buttons");
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            String path = "menus." + menuKey + ".buttons." + key;
            String materialName = config.getString(path + ".material", "STONE");
            int slot = config.getInt(path + ".slot", 22);
            String displayName = HexUtil.translateHexColorCodes(config.getString(path + ".name", key));
            List<String> loreRaw = config.getStringList(path + ".lore");
            List<String> lore = new ArrayList<>();
            for (String line : loreRaw) {
                lore.add(HexUtil.translateHexColorCodes(line));
            }
            String action = config.getString(path + ".action", "");

            Material material;
            try {
                material = Material.valueOf(materialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                material = Material.STONE;
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            NBTUtil.addItemNBT(item, "shop_action", action);

            result.add(new CategoryButton(slot, item, action));
        }
        return result;
    }

    public static class CategoryButton {
        private final int slot;
        private final ItemStack item;
        private final String action;

        public CategoryButton(int slot, ItemStack item, String action) {
            this.slot = slot;
            this.item = item;
            this.action = action;
        }

        public int getSlot() { return slot; }
        public ItemStack getItem() { return item; }
        public String getAction() { return action; }
    }
}