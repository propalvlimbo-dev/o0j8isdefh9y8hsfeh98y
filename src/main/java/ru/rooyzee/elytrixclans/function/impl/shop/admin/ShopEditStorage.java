package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.ShopItemMeta;

/**
 * Хранилище правок админ-редактора магазина.
 *
 * Читает и пишет тот же файл, из которого живёт магазин и наборы — shop/shop_item.yml,
 * поэтому изменения сразу видны игрокам после /clan shop (файл перезагружается в памяти).
 * Формат записей не меняется: name / material / slot / price / count / lore
 * (для TIPPED_ARROW дополнительно effect / amplifier / duration), наборы — kits.&lt;id&gt;.items.
 */
public final class ShopEditStorage {

    /** Секция наборов внутри shop_item.yml. */
    public static final String KITS = "kits";

    /**
     * Слоты, доступные для редактирования. Это ровно те клетки, которые не заняты
     * рамкой меню {@link ru.rooyzee.elytrixclans.utils.MenuUtil}, — то есть позиция
     * магазина выглядит и стоит на своём месте, как в обычном /clan shop.
     */
    public static final int[] EDIT_SLOTS = {
            11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            38, 39, 40, 41, 42
    };

    private ShopEditStorage() {
    }

    public static boolean isEditableSlot(int slot) {
        for (int editable : EDIT_SLOTS) {
            if (editable == slot) return true;
        }
        return false;
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

    /** Атомарная запись: временный файл + замена, плюс копия .bak на случай сбоя. */
    public static boolean saveYaml(YamlConfiguration config) {
        File target = file();
        File folder = target.getParentFile();
        if (folder != null && !folder.exists()) folder.mkdirs();
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
                if (temp.exists()) temp.delete();
            } catch (Exception ignored) {
            }
            return false;
        }
    }

    /** Чтобы магазин сразу показывал новые позиции без рестарта. */
    public static void reloadShop() {
        Main main = Main.getInstance();
        if (main == null) return;
        if (main.getItemsConfiguration() != null) main.getItemsConfiguration().reloadYml();
        if (main.getKitManager() != null) main.getKitManager().load();
    }

    public static List<String> categories(YamlConfiguration config) {
        List<String> out = new ArrayList<>();
        for (String key : config.getKeys(false)) {
            if (key == null) continue;
            if (KITS.equalsIgnoreCase(key)) continue;
            if (config.getConfigurationSection(key) == null) continue;
            out.add(key);
        }
        return out;
    }

    public static List<String> kitIds(YamlConfiguration config) {
        List<String> out = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection(KITS);
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            if (key != null) out.add(key);
        }
        return out;
    }

    /** Русские подписи для знакомых категорий; для новых — как написано в конфиге. */
    public static String label(String category) {
        if (category == null || category.isEmpty()) return "Магазин";
        if ("things".equalsIgnoreCase(category)) return "Вещи";
        if ("shulker".equalsIgnoreCase(category)) return "Шалкеры";
        if ("spawner".equalsIgnoreCase(category)) return "Спавнеры";
        if ("arrow".equalsIgnoreCase(category) || "arrows".equalsIgnoreCase(category)) return "Стрелы";
        if (KITS.equalsIgnoreCase(category)) return "Наборы";
        return category;
    }

    public static int defaultPrice() {
        Main main = Main.getInstance();
        if (main == null) return 29;
        return Math.max(0, main.getConfig().getInt("edit_item_price", 29));
    }

    /**
     * Предметы позиции категории ровно в том виде, в котором их показывает магазин.
     * Возвращает «слот → предмет».
     */
    public static Map<Integer, ItemStack> previewCategory(YamlConfiguration config, String category) {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        ConfigurationSection section = config.getConfigurationSection(category);
        if (section == null) return out;
        for (String key : section.getKeys(false)) {
            String path = category + "." + key;
            int slot = config.getInt(path + ".slot", -1);
            if (slot < 0) continue;
            Material material = matchMaterial(config.getString(path + ".material", ""));
            if (material == null) continue;
            ItemStack item = new ItemStack(material, safeCount(config, path, material));
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = config.getString(path + ".name", "");
                if (name != null && !name.isEmpty()) meta.setDisplayName(HexUtil.translateHexColorCodes(name));
                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList(path + ".lore")) {
                    lore.add(HexUtil.translateHexColorCodes(line));
                }
                if (!lore.isEmpty()) meta.setLore(lore);
                if (material == Material.TIPPED_ARROW) applyArrowLook(meta, config, path);
                item.setItemMeta(meta);
            }
            // В окне редактора предмет должен выглядеть ровно так, как в магазине,
            // иначе повторное сохранение стёрло бы зачарования и эффекты.
            ShopItemMeta.apply(item, ShopItemMeta.of(config.getConfigurationSection(path)));
            out.put(slot, item);
        }
        return out;
    }

    private static int safeCount(YamlConfiguration config, String path, Material material) {
        int count = Math.max(1, config.getInt(path + ".count", 1));
        int max = material.getMaxStackSize();
        return max > 0 ? Math.min(count, max) : count;
    }

    private static void applyArrowLook(ItemMeta meta, YamlConfiguration config, String path) {
        String effect = config.getString(path + ".effect", "");
        PotionEffectType type = effect == null ? null : PotionEffectType.getByName(effect);
        if (type == null || !(meta instanceof PotionMeta)) return;
        PotionMeta potionMeta = (PotionMeta) meta;
        potionMeta.setColor(type.getColor());
        // amplifier в конфиге — уровень (2 = II), как в ArrowItem: отсюда -1.
        int amplifier = Math.max(0, config.getInt(path + ".amplifier", 1) - 1);
        int durationTicks = Math.max(1, config.getInt(path + ".duration", 1)) * 200;
        potionMeta.addCustomEffect(new PotionEffect(type, durationTicks, amplifier), true);
    }

    /**
     * Применяет правки категории. grid — «слот → предмет» (пустой слот = предметы нет).
     * Существующие записи с тем же материалом сохраняют цену, имя и лор: меняем только
     * количество, если стак другой. Пустой слот = запись удаляется. Новый материал = новая
     * запись с ценой по умолчанию (config.yml: edit_item_price).
     */
    public static Result applyCategory(YamlConfiguration config, String category, Map<Integer, ItemStack> grid) {
        Result result = new Result();
        ConfigurationSection section = config.getConfigurationSection(category);
        Map<String, Map<String, Object>> existing = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) continue;
                existing.put(key, new LinkedHashMap<String, Object>(entry.getValues(false)));
            }
        }

        Map<Integer, String> bySlot = new LinkedHashMap<>();
        Map<String, Map<String, Object>> kept = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : existing.entrySet()) {
            int slot = intOf(entry.getValue().get("slot"), -1);
            // Слот вне сетки редактора (например, позиция в углу рамки) или дубликат слота:
            // не трогаем и не удаляем — просто не показываем в окне.
            if (slot >= 0 && isEditableSlot(slot) && !bySlot.containsKey(slot)) {
                bySlot.put(slot, entry.getKey());
            } else {
                kept.put(entry.getKey(), entry.getValue());
            }
        }

        for (int slot : EDIT_SLOTS) {
            ItemStack item = grid.get(slot);
            String oldKey = bySlot.get(slot);
            boolean empty = item == null || item.getType() == Material.AIR || item.getAmount() <= 0;

            if (empty) {
                if (oldKey != null) {
                    result.removed++;
                    config.set(category + "." + oldKey, null);
                }
                continue;
            }

            String newMaterial = item.getType().name();
            if (oldKey != null) {
                Map<String, Object> values = existing.get(oldKey);
                String oldMaterial = values.get("material") == null ? "" : String.valueOf(values.get("material"));
                if (oldMaterial.equalsIgnoreCase(newMaterial)) {
                    Map<String, Object> before = new LinkedHashMap<>(values);
                    int count = clampAmount(item);
                    int oldCount = Math.max(1, intOf(values.get("count"), 1));
                    // Сравниваем с тем, что реально показывает окно: если в конфиге стоит
                    // count: 1000, а в клетке максимум стака (64) — запись не трогаем.
                    int shown = oldCount;
                    int maxStack = item.getType().getMaxStackSize();
                    if (maxStack > 0 && shown > maxStack) shown = maxStack;
                    if (shown != count) values.put("count", count);
                    values.put("slot", slot);
                    // Зачарования, эффекты, прочность, флаги — какие лежат в клетке, такие и в продаже.
                    ShopItemMeta.clear(values);
                    ShopItemMeta.write(values, item);
                    if (!before.equals(values)) {
                        result.updated++;
                        reportEntry(category + "." + oldKey, item, values);
                    }
                    kept.put(oldKey, values);
                    continue;
                }
                result.removed++;
                config.set(category + "." + oldKey, null);
            }

            String key = uniqueKey(kept.keySet(), newMaterial, slot);
            Map<String, Object> values = newEntry(item, slot);
            kept.put(key, values);
            reportEntry(category + "." + key, item, values);
            result.added++;
        }

        // Пересобираем секцию категории: порядок ключей, как был, новые — в конце.
        config.set(category, null);
        if (kept.isEmpty()) {
            // Не даём категории исчезнуть совсем: иначе её больше не открыть в редакторе.
            config.createSection(category);
        } else {
            for (Map.Entry<String, Map<String, Object>> entry : kept.entrySet()) {
                config.set(category + "." + entry.getKey(), entry.getValue());
            }
        }
        result.entries = kept.size();
        return result;
    }

    /** Правка содержимого набора: имя, иконка, слот и цена набора остаются как были. */
    public static Result applyKit(YamlConfiguration config, String kitId, List<ItemStack> items) {
        Result result = new Result();
        if (kitId == null) return result;
        String path = KITS + "." + kitId;
        if (config.getConfigurationSection(path) == null) {
            result.failed = true;
            return result;
        }
        List<Map<String, Object>> list = new ArrayList<>();
        int removedCount = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
                removedCount++;
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("material", item.getType().name());
            entry.put("amount", clampAmount(item));
            String name = displayName(item);
            if (name != null && !name.isEmpty()) entry.put("name", name);
            ShopItemMeta.write(entry, item);
            list.add(entry);
        }
        config.set(path + ".items", list);
        result.added = list.size();
        result.removed = removedCount;
        result.entries = list.size();
        return result;
    }

    /** В консоль — что именно уехало в конфиг по позиции: сразу видно, если что-то потерялось. */
    private static void reportEntry(String path, ItemStack item, Map<String, Object> values) {
        StringBuilder line = new StringBuilder();
        line.append("[ElytrixClans] ").append(path).append(": на предмете зачарований ")
                .append(item.getEnchantments().size()).append(", в конфиг ушло ").append(values.get("enchant"));
        if (values.get("stored-enchant") != null) {
            line.append(", книге ").append(values.get("stored-enchant"));
        }
        if (values.get("potion-effects") != null) {
            line.append(", эффекты ").append(values.get("potion-effects"));
        }
        org.bukkit.Bukkit.getLogger().info(line.toString());
    }

    private static Map<String, Object> newEntry(ItemStack item, int slot) {
        int price = defaultPrice();
        int count = clampAmount(item);

        Map<String, Object> values = new LinkedHashMap<>();
        String name = displayName(item);
        values.put("name", name != null && !name.isEmpty() ? name : "&#F8BEFB" + prettyName(item.getType().name()));
        values.put("material", item.getType().name());
        values.put("slot", slot);
        values.put("price", price);
        values.put("count", count);

        List<String> lore = new ArrayList<>();
        lore.add("&#F8BEFB&l┃ ");
        if (count > 1) lore.add("&#F8BEFB&l┃ &fКоличество: &#F8BEFB" + count);
        lore.add("&#F8BEFB&l┃ &fЦена: &#F8BEFB" + price + " &7поинтов");
        lore.add("&#F8BEFB&l┃ ");
        lore.add("&7● &fНажмите для покупки");
        values.put("lore", lore);

        ShopItemMeta.write(values, item);
        return values;
    }

    private static String uniqueKey(java.util.Set<String> used, String material, int slot) {
        String base = material.toLowerCase(Locale.ROOT);
        String candidate = base + "_" + slot;
        int index = 1;
        while (used.contains(candidate)) {
            candidate = base + "_" + slot + "_" + (++index);
        }
        return candidate;
    }

    private static int clampAmount(ItemStack item) {
        int amount = Math.max(1, item.getAmount());
        int max = item.getMaxStackSize();
        return max > 0 ? Math.min(amount, max) : amount;
    }

    private static String displayName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return null;
        String name = meta.getDisplayName();
        if (name == null || name.trim().isEmpty()) return null;
        // В конфиге держим исходный вид (с &), чтобы файл оставался читаемым и переносимым.
        return name.replace('§', '&');
    }

    private static Material matchMaterial(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            Material material = Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return material == Material.AIR ? null : material;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int intOf(Object value, int def) {
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static String prettyName(String materialName) {
        String name = materialName.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
        if (name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void log(String message) {
        Main main = Main.getInstance();
        if (main != null) main.getLogger().warning(message);
    }

    /** Итог применения правок — для сообщения админу. */
    public static final class Result {
        public int added;
        public int updated;
        public int removed;
        public int entries;
        public boolean failed;
    }
}
