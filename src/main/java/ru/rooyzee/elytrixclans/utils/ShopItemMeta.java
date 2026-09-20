package ru.rooyzee.elytrixclans.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * Перенос «настоящих» свойств предмета (зачарования, эффекты зелий, прочность, флаги)
 * в конфиг магазина и обратно. Имя и лор здесь не обрабатываются: это оформление витрины,
 * оно живёт своими ключами (name/lore), а покупателю и так не отдаётся.
 *
 * Ключи в конфиге (все необязательные):
 *   enchant: ['sharpness:5']        ванидный ключ реестра:уровень
 *   stored-enchant: ['sharpness:5'] зачарования книги зачарований
 *   leather-color: '160,80,0'        цвет кожаной брони
 *   unbreakable: true
 *   damage: 12                       использованная прочность в единицах урона
 *   flags: ['HIDE_ENCHANTS']
 *   potion-effects: ['SLOW:0:1200']  точный список: тип:усилитель:тики
 *   potion-type / potion-extended / potion-upgraded   «базовое» зелье из ванильного рецепта
 *   potion-color: '255,0,0'
 *   effect / amplifier / duration     легаси-формат стрел (duration в тиках / 200)
 */
public final class ShopItemMeta {

    /** Ключи, которыми мы владеем: при перечитывании позиции они пишутся заново. */
    public static final String[] KEYS = {
            "enchant", "stored-enchant", "unbreakable", "damage", "flags",
            "potion-effects", "potion-type", "potion-extended", "potion-upgraded", "potion-color",
            "leather-color", "effect", "amplifier", "duration"
    };

    /** Тиков на одну единицу legacy-duration (магазин хранит duration именно так). */
    private static final int TICKS_PER_UNIT = 200;

    private ShopItemMeta() {
    }

    /** Источник значений: секция конфига либо raw-map из списка наборов. */
    public interface Reader {
        String string(String key);

        int number(String key, int def);

        boolean bool(String key);

        List<String> list(String key);

        boolean has(String key);
    }

    public static Reader of(final ConfigurationSection section) {
        if (section == null) return EMPTY;
        return new Reader() {
            @Override public String string(String key) { return section.getString(key); }
            @Override public int number(String key, int def) { return section.getInt(key, def); }
            @Override public boolean bool(String key) { return section.getBoolean(key); }
            @Override public List<String> list(String key) { return section.getStringList(key); }
            @Override public boolean has(String key) { return section.isSet(key); }
        };
    }

    public static Reader of(final Map<?, ?> map) {
        if (map == null || map.isEmpty()) return EMPTY;
        return new Reader() {
            @Override public String string(String key) {
                Object v = map.get(key);
                return v == null ? null : String.valueOf(v);
            }
            @Override public int number(String key, int def) {
                Object v = map.get(key);
                if (v instanceof Number) return ((Number) v).intValue();
                try {
                    return Integer.parseInt(String.valueOf(v));
                } catch (Exception e) {
                    return def;
                }
            }
            @Override public boolean bool(String key) {
                Object v = map.get(key);
                if (v instanceof Boolean) return (Boolean) v;
                return v != null && Boolean.parseBoolean(String.valueOf(v));
            }
            @Override public List<String> list(String key) {
                Object v = map.get(key);
                List<String> out = new ArrayList<>();
                if (v instanceof List) {
                    for (Object o : (List<?>) v) {
                        if (o != null) out.add(String.valueOf(o));
                    }
                } else if (v != null) {
                    out.add(String.valueOf(v));
                }
                return out;
            }
            @Override public boolean has(String key) { return map.get(key) != null; }
        };
    }

    /** Пустой источник: apply() с ним ничего не найдёт и ничего не сломает. */
    public static Reader empty() {
        return EMPTY;
    }

    private static final Reader EMPTY = new Reader() {
        @Override public String string(String key) { return null; }
        @Override public int number(String key, int def) { return def; }
        @Override public boolean bool(String key) { return false; }
        @Override public List<String> list(String key) { return Collections.emptyList(); }
        @Override public boolean has(String key) { return false; }
    };

    /** Убирает наши ключи из записи — дальше write() положит актуальные значения. */
    public static void clear(Map<String, Object> values) {
        for (String key : KEYS) values.remove(key);
    }

    /** Есть ли у предмета что сохранять, кроме материала и количества. */
    public static boolean hasAnything(ItemStack item) {
        Map<String, Object> probe = new java.util.LinkedHashMap<>();
        write(probe, item);
        return !probe.isEmpty();
    }

    /** Собирает из предмета всё, что магазин умеет хранить. */
    public static void write(Map<String, Object> values, ItemStack item) {
        if (item == null || item.getType() == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        if (meta.hasEnchants()) {
            Set<Enchantment> enchants = new LinkedHashSet<>(meta.getEnchants().keySet());
            List<String> lines = new ArrayList<>();
            for (Enchantment ench : enchants) {
                if (ench == null) continue;
                lines.add(enchantId(ench) + ":" + Math.max(1, meta.getEnchantLevel(ench)));
            }
            if (!lines.isEmpty()) {
                Collections.sort(lines); // стабильный порядок — меньше шума в файле и в diff
                values.put("enchant", lines);
            }
        }

        // Зачарованные книги хранят зачарования отдельно от самого предмета.
        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta book = (EnchantmentStorageMeta) meta;
            if (book.hasStoredEnchants()) {
                List<String> lines = new ArrayList<>();
                for (Map.Entry<Enchantment, Integer> stored : book.getStoredEnchants().entrySet()) {
                    if (stored.getKey() == null) continue;
                    lines.add(enchantId(stored.getKey()) + ":" + Math.max(1, stored.getValue()));
                }
                if (!lines.isEmpty()) {
                    Collections.sort(lines);
                    values.put("stored-enchant", lines);
                }
            }
        }

        if (meta.isUnbreakable()) values.put("unbreakable", true);

        if (meta instanceof Damageable) {
            int damage = ((Damageable) meta).getDamage();
            if (damage > 0) values.put("damage", damage);
        }

        Set<ItemFlag> flags = meta.getItemFlags();
        if (flags != null && !flags.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (ItemFlag flag : flags) {
                if (flag == null) continue;
                // Стрелы магазин помечает этим флагом сам (чтобы не показывать эффект в витрине).
                // Сохранять его как свойство предмета не нужно.
                if (flag == ItemFlag.HIDE_POTION_EFFECTS && item.getType() == Material.TIPPED_ARROW) continue;
                lines.add(flag.name());
            }
            if (!lines.isEmpty()) values.put("flags", lines);
        }

        if (meta instanceof PotionMeta) {
            PotionMeta potion = (PotionMeta) meta;
            List<PotionEffect> custom = potion.getCustomEffects();
            if (custom != null && !custom.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (PotionEffect effect : custom) {
                    if (effect == null || effect.getType() == null) continue;
                    lines.add(effect.getType().getName() + ":" + effect.getAmplifier() + ":" + effect.getDuration());
                }
                if (!lines.isEmpty()) {
                    values.put("potion-effects", lines);
                    // Легаси-ключи: их читает магазин для стрел (serializeArrowItems).
                    PotionEffect first = custom.get(0);
                    if (first != null && first.getType() != null) {
                        values.put("effect", first.getType().getName());
                        // В конфиге amplifier — это уровень (2 = II): магазин при чтении
                        // вычитает единицу (см. конструктор ArrowItem). Держимся того же.
                        values.put("amplifier", Math.max(1, first.getAmplifier() + 1));
                        values.put("duration", ticksToUnits(first.getDuration()));
                    }
                }
            }
            PotionData base = potion.getBasePotionData();
            if (base != null && base.getType() != null && base.getType() != PotionType.WATER) {
                // У PotionType в 1.16.5 нет getName() — берём имя константы enum'а,
                // его же читает обратно readPotionType() через PotionType.valueOf().
                values.put("potion-type", base.getType().name());
                values.put("potion-extended", base.isExtended());
                values.put("potion-upgraded", base.isUpgraded());
            }
            Color color = potion.getColor();
            if (color != null) {
                values.put("potion-color", color.getRed() + "," + color.getGreen() + "," + color.getBlue());
            }
        }

        if (meta instanceof LeatherArmorMeta) {
            Color color = ((LeatherArmorMeta) meta).getColor();
            if (color != null) {
                values.put("leather-color", color.getRed() + "," + color.getGreen() + "," + color.getBlue());
            }
        }
    }

    /** Накладывает сохранённые свойства на предмет. Имя/лор/количество не трогает. */
    public static void apply(ItemStack item, Reader values) {
        try {
            applyMeta(item, values);
        } catch (Exception ignored) {
            // Кривая запись в конфиге не должна ронять открытие меню или выдачу предмета.
        }
    }

    private static void applyMeta(ItemStack item, Reader values) {
        if (item == null || values == null || item.getType() == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        boolean changed = false;

        for (Enchantment ench : new ArrayList<>(meta.getEnchants().keySet())) {
            meta.removeEnchant(ench);
            changed = true;
        }
        // Список «что наложить» копим и вешаем на сам стак через addUnsafeEnchantment():
        // только он не режет «нелегальные» сочетания и уровни выше максимального, а нам нужно
        // отдать предмет ровно таким, каким его положили в клетку.
        List<Enchantment> pending = new ArrayList<>();
        List<Integer> pendingLevels = new ArrayList<>();
        int listed = 0;
        for (String line : values.list("enchant")) {
            listed++;
            Enchantment ench = readEnchant(line);
            if (ench == null) continue;
            pending.add(ench);
            pendingLevels.add(readLevel(line));
        }

        if (meta instanceof EnchantmentStorageMeta) {
            EnchantmentStorageMeta book = (EnchantmentStorageMeta) meta;
            for (Enchantment stored : new ArrayList<>(book.getStoredEnchants().keySet())) {
                book.removeStoredEnchant(stored);
                changed = true;
            }
            for (String line : values.list("stored-enchant")) {
                Enchantment ench = readEnchant(line);
                if (ench == null) continue;
                try {
                    book.addStoredEnchant(ench, readLevel(line), false);
                    changed = true;
                } catch (Exception e) {
                    warn("не удалось записать зачарование книги '" + line + "': " + e.getMessage());
                }
            }
        }

        if (values.has("unbreakable")) {
            boolean unbreakable = values.bool("unbreakable");
            if (meta.isUnbreakable() != unbreakable) {
                meta.setUnbreakable(unbreakable);
                changed = true;
            }
        }

        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
            int max = item.getType().getMaxDurability();
            int damage = Math.max(0, values.number("damage", 0));
            // У материала без прочности setDamage() кидается — трогаем только durability-материалы.
            if (max > 0) {
                if (damage >= max) damage = max - 1;
                if (damageable.getDamage() != damage) {
                    damageable.setDamage(damage);
                    changed = true;
                }
            }
        }

        if (values.has("flags")) {
            for (String line : values.list("flags")) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(line.trim().toUpperCase(Locale.ROOT)));
                    changed = true;
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (meta instanceof PotionMeta) {
            PotionMeta potion = (PotionMeta) meta;
            if (values.has("potion-effects")) {
                potion.clearCustomEffects();
                for (String line : values.list("potion-effects")) {
                    PotionEffect effect = readEffect(line);
                    if (effect == null) continue;
                    potion.addCustomEffect(effect, true);
                }
                changed = true;
            }
            String base = values.string("potion-type");
            PotionType type = base == null ? null : readPotionType(base);
            if (type != null) {
                potion.setBasePotionData(new PotionData(type, values.bool("potion-extended"), values.bool("potion-upgraded")));
                changed = true;
            }
            String color = values.string("potion-color");
            Color parsed = readColor(color);
            if (parsed != null) {
                potion.setColor(parsed);
                changed = true;
            }
        }

        if (meta instanceof LeatherArmorMeta && values.has("leather-color")) {
            Color leather = readColor(values.string("leather-color"));
            if (leather != null) {
                ((LeatherArmorMeta) meta).setColor(leather);
                changed = true;
            }
        }

        if (changed || !pending.isEmpty()) item.setItemMeta(meta);

        for (int i = 0; i < pending.size(); i++) {
            try {
                item.addUnsafeEnchantment(pending.get(i), pendingLevels.get(i));
            } catch (Exception e) {
                warn("не удалось наложить '" + enchantId(pending.get(i)) + ":" + pendingLevels.get(i)
                        + "' на " + item.getType() + ": " + e.getMessage());
            }
        }
        if (listed > item.getEnchantments().size()) {
            warn("в записи позиции зачарований: " + listed + ", на предмете оказалось: "
                    + item.getEnchantments().size() + " (" + item.getType() + ")");
        }
    }

    private static void warn(String what) {
        Bukkit.getLogger().warning("[ElytrixClans] ShopItemMeta: " + what);
    }

    /** Сколько тиков отдаём покупателю по legacy-duration. */
    public static int unitsToTicks(int units) {
        return Math.max(1, units * TICKS_PER_UNIT);
    }

    private static int ticksToUnits(int ticks) {
        return Math.max(1, (ticks + TICKS_PER_UNIT - 1) / TICKS_PER_UNIT);
    }

    /** Идентификатор зачарования для конфига: ванидный ключ реестра (sharpness, mending). */
    private static String enchantId(Enchantment ench) {
        try {
            org.bukkit.NamespacedKey key = ench.getKey();
            if (key != null) {
                return key.getNamespace() == null || "minecraft".equals(key.getNamespace())
                        ? key.getKey()
                        : key.getNamespace() + ":" + key.getKey();
            }
        } catch (Throwable ignored) {
            // Нет Keyed (кастомная обёртка) — уходим на legacy-имя.
        }
        return ench.getName();
    }

    private static Enchantment readEnchant(String line) {
        if (line == null) return null;
        int cut = line.lastIndexOf(':');
        String raw = (cut > 0 ? line.substring(0, cut) : line).trim();
        if (raw.isEmpty()) return null;

        // 1) То, что пишем сами: ванидный ключ реестра.
        Enchantment enchantment = null;
        String ns = "minecraft";
        String key = raw;
        int colon = raw.indexOf(':');
        if (colon > 0 && colon < raw.length() - 1) {
            ns = raw.substring(0, colon);
            key = raw.substring(colon + 1);
        }
        try {
            // Ключи Minecraft всегда в нижнем регистре; чужие неймспейсы не трогаем —
            // валидатор NamespacedKey ругнётся на потерянный верхний регистр.
            String lookupKey = "minecraft".equalsIgnoreCase(ns) ? key.toLowerCase(Locale.ROOT) : key;
            enchantment = "minecraft".equalsIgnoreCase(ns)
                    ? Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(lookupKey))
                    : Enchantment.getByKey(new org.bukkit.NamespacedKey(ns, lookupKey));
        } catch (Exception ignored) {
        }
        // 2) Файлы, подписанные Bukkit-именами (DAMAGE_ALL, DURABILITY), и ручные правки.
        if (enchantment == null) {
            try {
                enchantment = Enchantment.getByName(raw.toUpperCase(Locale.ROOT).replace(' ', '_'));
            } catch (Exception ignored) {
            }
        }
        if (enchantment == null) {
            warn("неизвестное зачарование '" + raw + "' — оно пропущено");
        }
        return enchantment;
    }

    private static int readLevel(String line) {
        try {
            // Уровень — после ДВОЙТОЧИЯ: ключ может быть с неймспейсом (minecraft:sharpness:5).
            int idx = line.lastIndexOf(':');
            if (idx < 0) return 1;
            return Math.max(1, Integer.parseInt(line.substring(idx + 1).trim()));
        } catch (Exception e) {
            return 1;
        }
    }

    private static PotionEffect readEffect(String line) {
        if (line == null) return null;
        String[] parts = line.split(":");
        if (parts.length == 0) return null;
        String typeName = parts[0].trim().toUpperCase(Locale.ROOT);
        PotionEffectType type = null;
        try {
            type = PotionEffectType.getByName(typeName);
        } catch (Exception ignored) {
        }
        if (type == null) {
            warn("неизвестный эффект зелья '" + typeName + "' — пропущен");
            return null;
        }
        int amplifier = 0;
        int ticks = unitsToTicks(1);
        try {
            if (parts.length > 1) amplifier = Math.max(0, Integer.parseInt(parts[1].trim()));
            if (parts.length > 2) ticks = Math.max(1, Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException ignored) {
        }
        return new PotionEffect(type, ticks, amplifier);
    }

    private static PotionType readPotionType(String name) {
        if (name == null) return null;
        String key = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        try {
            return PotionType.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Color readColor(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String[] parts = value.split(",");
        if (parts.length != 3) return null;
        try {
            int r = clamp(Integer.parseInt(parts[0].trim()));
            int g = clamp(Integer.parseInt(parts[1].trim()));
            int b = clamp(Integer.parseInt(parts[2].trim()));
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
