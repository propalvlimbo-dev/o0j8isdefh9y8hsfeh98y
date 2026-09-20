package ru.rooyzee.elytrixclans.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.rooyzee.elytrixclans.Main;

public class NBTUtil {

    // NamespacedKey проверяет валидатором каждую строку, а его создавали на каждый клик по меню.
    private static final Map<String, NamespacedKey> KEYS = new ConcurrentHashMap<>();

    private static NamespacedKey key(String key) {
        if (key == null) return null;
        Main plugin = Main.getInstance();
        if (plugin == null) return null;
        NamespacedKey existing = KEYS.get(key);
        if (existing != null) return existing;
        try {
            NamespacedKey created = new NamespacedKey(plugin, key);
            KEYS.put(key, created);
            return created;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void addItemNBT(ItemStack item, String key, String value) {
        if (item == null) return;
        NamespacedKey namespacedKey = key(key);
        if (namespacedKey == null) return;
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : Bukkit.getItemFactory().getItemMeta(item.getType());
        if (meta == null) return;
        meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    public static boolean hasItemNBT(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey namespacedKey = key(key);
        if (namespacedKey == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(namespacedKey, PersistentDataType.STRING);
    }

    public static String getNBTvalue(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return null;
        NamespacedKey namespacedKey = key(key);
        if (namespacedKey == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(namespacedKey, PersistentDataType.STRING);
    }
}