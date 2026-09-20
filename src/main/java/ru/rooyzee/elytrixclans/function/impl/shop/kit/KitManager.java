package ru.rooyzee.elytrixclans.function.impl.shop.kit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
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

    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager() {
        load();
    }

    public void load() {
        kits.clear();
        File shopFolder = new File(Main.getInstance().getDataFolder(), "shop");
        if (!shopFolder.exists() && !shopFolder.mkdirs()) {
            Bukkit.getLogger().warning("[ElytrixClans] Не удалось создать папку shop/");
        }

        File file = new File(shopFolder, "shop_item.yml");
        if (!file.exists()) {
            try {
                Main.getInstance().saveResource("shop/shop_item.yml", false);
            } catch (Exception ignored) {
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection kitsSection = config.getConfigurationSection("kits");
        if (kitsSection == null) return;

        List<Kit> loaded = new ArrayList<>();
        for (String key : kitsSection.getKeys(false)) {
            ConfigurationSection entry = kitsSection.getConfigurationSection(key);
            if (entry == null) continue;
            String displayName = HexUtil.translateHexColorCodes(entry.getString("name", key));
            String iconMaterial = entry.getString("material", "SHULKER_BOX").toUpperCase(Locale.ROOT);
            double price = entry.getDouble("price", 100000.0);
            int requiredLevel = entry.getInt("level", 1);

            List<ItemStack> items = new ArrayList<>();
            for (Map<?, ?> map : entry.getMapList("items")) {
                ItemStack itemStack = readItem(map);
                if (itemStack != null) items.add(itemStack);
            }

            loaded.add(new Kit(key.toLowerCase(Locale.ROOT), displayName, iconMaterial, price,
                    requiredLevel, items, entry.getStringList("commands")));
        }

        // Наборы идут в витрине после обычных товаров, по возрастанию требуемого уровня.
        loaded.sort(Comparator.comparingInt(Kit::getRequiredLevel).thenComparingDouble(Kit::getPrice));
        for (Kit kit : loaded) kits.put(kit.getId(), kit);
    }

    private ItemStack readItem(Map<?, ?> map) {
        try {
            Object rawMaterial = map.get("material");
            if (rawMaterial == null) return null;
            Material material = Material.matchMaterial(String.valueOf(rawMaterial).toUpperCase(Locale.ROOT));
            if (material == null) {
                Bukkit.getLogger().warning("[ElytrixClans] Набор: неизвестный материал " + rawMaterial);
                return null;
            }
            int amount = map.get("amount") instanceof Number ? ((Number) map.get("amount")).intValue() : 1;
            ItemStack itemStack = new ItemStack(material, Math.max(1, amount));

            Object name = map.get("name");
            Object lore = map.get("lore");
            if (name != null || lore != null) {
                ItemMeta meta = itemStack.getItemMeta();
                if (meta != null) {
                    if (name != null) {
                        meta.setDisplayName(HexUtil.translateHexColorCodes(String.valueOf(name)));
                    }
                    if (lore instanceof List) {
                        List<String> lines = new ArrayList<>();
                        for (Object line : (List<?>) lore) {
                            lines.add(HexUtil.translateHexColorCodes(String.valueOf(line)));
                        }
                        meta.setLore(lines);
                    }
                    itemStack.setItemMeta(meta);
                }
            }
            // Зачарования/эффекты/прочность предмета набора (см. ShopItemMeta).
            ShopItemMeta.apply(itemStack, ShopItemMeta.of(map));
            return itemStack;
        } catch (Exception e) {
            Bukkit.getLogger().warning("[ElytrixClans] Предмет набора пропущен: " + e.getMessage());
            return null;
        }
    }

    public Kit getKit(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Kit> getKits() {
        return Collections.unmodifiableCollection(kits.values());
    }

    /** Выдача набора: предметы в инвентарь (лишнее — на землю) + консольные команды. */
    public void giveKit(Player player, Kit kit) {
        for (ItemStack item : kit.getItems()) {
            if (player.getInventory().firstEmpty() == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
            } else {
                player.getInventory().addItem(item.clone());
            }
        }
        for (String command : kit.getCommands()) {
            if (command == null || command.trim().isEmpty()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("%player%", player.getName()));
        }
    }
}
