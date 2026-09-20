package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.utils.ShopItemMeta;

/**
 * Хранилище позиций, добавленных через админ-меню /elytrixclan edititem.
 *
 * Как это устроено:
 *  - Меню — это три ряда пустых клеток. Что положил в клетку, то и продаётся.
 *  - При закрытии меню содержимое клеток пишется в shop/shop_item.yml в секцию items.
 *  - Id позиции привязан к номеру клетки: edit_s10, edit_s11 и так далее. Он стабилен,
 *    поэтому цена, уровень и кулдаун, однажды прописанные в конфиге, остаются за клеткой
 *    и не слетают при следующем редактировании.
 *  - Цена НЕ задаётся из меню: её вы пишете руками в shop/prices.yml по тому же id.
 *    Пока цена не указана, позиция стоит DEFAULT_PRICE — так новый предмет не может
 *    случайно оказаться бесплатным.
 *
 * Позиции, добавленные из меню, помечаются в файле флагом editor: true. Всё остальное
 * в shop_item.yml редактор не трогает — ваши руками прописанные товары и наборы в
 * безопасности.
 */
public final class ShopEditStorage {

    /** Клетки под предметы: три ряда по семь, ровно как в витрине магазина. */
    public static final int[] EDIT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    /** Флаг «позиция создана в меню редактора». */
    private static final String EDITOR_FLAG = "editor";
    /** Цена новой позиции, пока она не указана в prices.yml. */
    public static final double DEFAULT_PRICE = 100000.0;
    /** Уровень клана для новой позиции. */
    public static final int DEFAULT_LEVEL = 1;

    private ShopEditStorage() {
    }

    public static boolean isEditableSlot(int slot) {
        for (int editable : EDIT_SLOTS) {
            if (editable == slot) return true;
        }
        return false;
    }

    /** Id позиции для клетки: стабилен, чтобы цена из конфига не терялась. */
    public static String slotId(int slot) {
        return "edit_s" + slot;
    }

    public static File file() {
        Main main = Main.getInstance();
        File base = main == null ? new File(".") : main.getDataFolder();
        return new File(new File(base, "shop"), "shop_item.yml");
    }

    public static YamlConfiguration loadYaml() {
        YamlConfiguration config = new YamlConfiguration();
        File target = file();
        if (!target.exists()) {
            Main main = Main.getInstance();
            if (main != null) {
                try {
                    main.saveResource("shop/shop_item.yml", false);
                } catch (Exception ignored) {
                }
            }
        }
        try {
            if (target.exists()) config.load(target);
        } catch (Exception e) {
            log("Не удалось прочитать shop_item.yml: " + e.getMessage());
        }
        return config;
    }

    /**
     * Предметы, которые редактор показывает при открытии: только свои позиции,
     * разложенные по тем же клеткам, откуда их сохранили.
     */
    public static Map<Integer, ItemStack> preview(YamlConfiguration config) {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) return out;
        for (int slot : EDIT_SLOTS) {
            ConfigurationSection entry = items.getConfigurationSection(slotId(slot));
            if (entry == null || !entry.getBoolean(EDITOR_FLAG, false)) continue;
            ItemStack stack = readItem(entry);
            if (stack != null) out.put(slot, stack);
        }
        return out;
    }

    private static ItemStack readItem(ConfigurationSection entry) {
        String materialName = entry.getString("material", "");
        Material material = materialName.isEmpty()
                ? null : Material.matchMaterial(materialName.toUpperCase());
        if (material == null || material == Material.AIR) return null;
        int amount = Math.max(1, entry.getInt("amount", 1));
        ItemStack stack = new ItemStack(material, Math.min(material.getMaxStackSize(), amount));
        ShopItemMeta.apply(stack, ShopItemMeta.of(entry));
        return stack;
    }

    /**
     * Сохраняет содержимое клеток. Цену, уровень и кулдаун уже существующей позиции
     * не трогаем — они правятся в конфиге и должны пережить редактирование предмета.
     */
    public static boolean save(Map<Integer, ItemStack> contents) {
        YamlConfiguration config = loadYaml();
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) items = config.createSection("items");

        for (int slot : EDIT_SLOTS) {
            String id = slotId(slot);
            ItemStack item = contents.get(slot);
            ConfigurationSection existing = items.getConfigurationSection(id);

            if (item == null || item.getType() == Material.AIR) {
                // Предмет убрали из клетки — убираем и позицию, но только нашу.
                if (existing != null && existing.getBoolean(EDITOR_FLAG, false)) {
                    items.set(id, null);
                }
                continue;
            }
            // Чужую запись с таким же id не перетираем: вдруг её добавили руками.
            if (existing != null && !existing.getBoolean(EDITOR_FLAG, false)) continue;

            double price = existing != null ? existing.getDouble("price", DEFAULT_PRICE) : DEFAULT_PRICE;
            int level = existing != null ? existing.getInt("level", DEFAULT_LEVEL) : DEFAULT_LEVEL;
            int cooldown = existing != null ? existing.getInt("cooldown", 0) : 0;

            ConfigurationSection entry = items.createSection(id);
            entry.set(EDITOR_FLAG, true);
            entry.set("name", displayName(item));
            entry.set("material", item.getType().name());
            entry.set("amount", item.getAmount());
            entry.set("price", price);
            entry.set("level", level);
            if (cooldown > 0) entry.set("cooldown", cooldown);

            // Зачарования, эффекты зелий, прочность и флаги сохраняем как есть:
            // что положили в клетку, то покупатель и получит.
            Map<String, Object> meta = new LinkedHashMap<>();
            ShopItemMeta.write(meta, item);
            for (Map.Entry<String, Object> value : meta.entrySet()) {
                entry.set(value.getKey(), value.getValue());
            }
        }
        return write(config);
    }

    /** Имя для витрины: своё, если предмет переименован, иначе — по материалу. */
    private static String displayName(ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName().replace('\u00a7', '&');
        }
        String raw = item.getType().name().toLowerCase().replace('_', ' ');
        return "&#F8BEFB&l" + Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /**
     * Сохраняет содержимое набора в секцию kits.&lt;id&gt;.items.
     *
     * Меняем ТОЛЬКО список предметов: имя, материал иконки, цена, уровень и кулдаун
     * остаются такими, какими их задали в конфиге.
     */
    public static boolean saveKit(String kitId, List<ItemStack> items) {
        if (kitId == null || kitId.isEmpty()) return false;
        YamlConfiguration config = loadYaml();
        ConfigurationSection kits = config.getConfigurationSection("kits");
        if (kits == null) return false;
        ConfigurationSection entry = kits.getConfigurationSection(kitId);
        if (entry == null) return false;

        List<Map<String, Object>> list = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("material", item.getType().name());
            values.put("amount", item.getAmount());
            ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
            if (meta != null && meta.hasDisplayName()) {
                values.put("name", meta.getDisplayName().replace('\u00a7', '&'));
            }
            // Зачарования, зелья, прочность и флаги — тем же форматом, что читает KitManager.
            ShopItemMeta.write(values, item);
            list.add(values);
        }
        entry.set("items", list);
        return write(config);
    }

    /** Атомарная запись: временный файл + замена, плюс копия .bak на случай сбоя. */
    private static boolean write(YamlConfiguration config) {
        File target = file();
        File folder = target.getParentFile();
        if (folder != null && !folder.exists() && !folder.mkdirs()) {
            log("Не удалось создать папку shop/");
            return false;
        }
        File temp = new File(folder, target.getName() + ".edit.tmp");
        try {
            config.save(temp);
            File backup = new File(folder, target.getName() + ".bak");
            if (target.exists() && target.length() > 0) {
                try {
                    Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException | UnsupportedOperationException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            reloadShop();
            return true;
        } catch (Exception e) {
            log("Не удалось сохранить shop_item.yml: " + e.getMessage());
            try {
                if (temp.exists() && !temp.delete()) temp.deleteOnExit();
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    /** Чтобы магазин сразу показывал новые позиции и наборы, без рестарта сервера. */
    private static void reloadShop() {
        Main main = Main.getInstance();
        if (main == null) return;
        if (main.getPriceConfiguration() != null) main.getPriceConfiguration().reloadYml();
        if (main.getItemsConfiguration() != null) main.getItemsConfiguration().reloadYml();
        if (main.getKitManager() != null) main.getKitManager().load();
    }

    private static void log(String message) {
        Bukkit.getLogger().warning("[ElytrixClans] " + message);
    }
}
