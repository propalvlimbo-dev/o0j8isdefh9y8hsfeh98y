package ru.rooyzee.elytrixclans.function.impl.shop.kit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.ShopItemMeta;

public class KitManager {

    private final Map<String, Kit> kits = new HashMap<>();
    private File file;
    private FileConfiguration config;

    public KitManager() {
        load();
    }

    public void load() {
        kits.clear();
        File shopFolder = new File(Main.getInstance().getDataFolder(), "shop");
        if (!shopFolder.exists()) shopFolder.mkdirs();

        file = new File(shopFolder, "shop_item.yml");
        if (!file.exists()) {
            try {
                Main.getInstance().saveResource("shop/shop_item.yml", false);
            } catch (Exception ignored) {
            }
        }

        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection kitsSection = config.getConfigurationSection("kits");
        if (kitsSection == null) return;

        for (String key : kitsSection.getKeys(false)) {
            String path = "kits." + key;
            String displayName = HexUtil.translateHexColorCodes(config.getString(path + ".name", key));
            String iconMaterial = config.getString(path + ".material", "CHEST");
            int slot = config.getInt(path + ".slot", 22);
            int price = config.getInt(path + ".price", 100);

            List<ItemStack> items = new ArrayList<>();
            List<Map<?, ?>> itemsList = config.getMapList(path + ".items");
            for (Map<?, ?> map : itemsList) {
                try {
                    String materialName = (String) map.get("material");
                    int amount = map.get("amount") != null ? ((Number) map.get("amount")).intValue() : 1;
                    Material material = Material.valueOf(materialName.toUpperCase());
                    ItemStack itemStack = new ItemStack(material, amount);

                    if (map.get("name") != null) {
                        ItemMeta meta = itemStack.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(HexUtil.translateHexColorCodes((String) map.get("name")));
                            itemStack.setItemMeta(meta);
                        }
                    }
                    // Зачарования/эффекты/прочность предмета набора (см. ShopItemMeta).
                    ShopItemMeta.apply(itemStack, ShopItemMeta.of(map));

                    items.add(itemStack);
                } catch (Exception ignored) {
                }
            }

            kits.put(key.toLowerCase(), new Kit(key.toLowerCase(), displayName, iconMaterial, slot, price, items));
        }
    }

    public Kit getKit(String id) {
        return kits.get(id.toLowerCase());
    }

    public Collection<Kit> getKits() {
        return Collections.unmodifiableCollection(kits.values());
    }

    public void giveKit(Player player, Kit kit) {
        for (ItemStack item : kit.getItems()) {
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
            } else {
                player.getInventory().addItem(item.clone());
            }
        }
    }
}