package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.function.impl.shop.object.ShopItem;
import ru.rooyzee.elytrixclans.utils.ShopItemMeta;

/**
 * Чтение и запись ассортимента для админ-меню /elytrixclan edititem.
 *
 * Меню показывает настоящую витрину магазина, поэтому здесь два направления работы:
 *  - entries() собирает то же, что видит игрок в /clan shop: товары и наборы в том же
 *    порядке, но товары — «живыми» предметами, которые можно забрать мышкой.
 *  - save() принимает содержимое страницы и переписывает секцию items.
 *
 * Позиция привязана к своему id, а не к номеру клетки: предмет можно перекладывать
 * между слотами, дубликатов от этого не возникает. Цена, уровень и кулдаун живут в
 * конфиге и переживают редактирование предмета — из меню меняется только сам предмет.
 *
 * Новым позициям id выдаётся по материалу (diamond_sword, diamond_sword_2 и так далее),
 * чтобы файл оставался читаемым при ручной правке.
 */
public final class ShopEditStorage {

    /** Клетки под товар — те же, что в витрине магазина. */
    public static final int[] EDIT_SLOTS = ShopFunction.CONTENT_SLOTS;

    /** Флаг «позиция создана в меню редактора». Оставлен для старых записей edit_sNN. */
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

    /**
     * Одна строка витрины редактора: либо товар (живой предмет), либо набор (иконка).
     */
    public static final class Entry {
        private final String id;
        private final ItemStack item;
        private final Kit kit;

        private Entry(String id, ItemStack item, Kit kit) {
            this.id = id;
            this.item = item;
            this.kit = kit;
        }

        public String getId() { return id; }
        public ItemStack getItem() { return item; }
        public Kit getKit() { return kit; }
        public boolean isKit() { return kit != null; }
    }

    /**
     * Ассортимент для редактора: тот же порядок, что в витрине магазина
     * (уровень, затем товары перед наборами, затем цена).
     */
    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        Main main = Main.getInstance();
        if (main == null) return out;

        List<Object> mixed = new ArrayList<>();
        if (main.getItemsConfiguration() != null) {
            mixed.addAll(main.getItemsConfiguration().getItems());
        }
        if (main.getKitManager() != null) {
            mixed.addAll(main.getKitManager().getKits());
        }
        Comparator<Object> order = Comparator
                .<Object>comparingInt(ShopEditStorage::entryLevel)
                .thenComparingInt(entry -> entry instanceof Kit ? 1 : 0)
                .thenComparingDouble(ShopEditStorage::entryPrice);
        mixed.sort(order);

        for (Object entry : mixed) {
            if (entry instanceof Kit) {
                out.add(new Entry(((Kit) entry).getId(), null, (Kit) entry));
            } else if (entry instanceof ShopItem) {
                ShopItem shopItem = (ShopItem) entry;
                out.add(new Entry(shopItem.getId(), rawItem(shopItem), null));
            }
        }
        return out;
    }

    private static int entryLevel(Object entry) {
        if (entry instanceof ShopItem) return ((ShopItem) entry).getRequiredLevel();
        if (entry instanceof Kit) return ((Kit) entry).getRequiredLevel();
        return Integer.MAX_VALUE;
    }

    private static double entryPrice(Object entry) {
        if (entry instanceof ShopItem) return ((ShopItem) entry).getPrice();
        if (entry instanceof Kit) return ((Kit) entry).getPrice();
        return 0.0;
    }

    /**
     * Предмет для клетки редактора: ровно тот, что получит покупатель, плюс название
     * позиции. Лор витрины (цена, уровень) намеренно не добавляем — иначе он въелся бы
     * в предмет при сохранении.
     */
    private static ItemStack rawItem(ShopItem shopItem) {
        Material material = Material.matchMaterial(shopItem.getMaterial().toUpperCase());
        if (material == null || material == Material.AIR) material = Material.STONE;
        int amount = Math.max(1, Math.min(material.getMaxStackSize(), shopItem.getAmount()));
        ItemStack stack = new ItemStack(material, amount);
        ShopItemMeta.apply(stack, shopItem.getMeta());
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(shopItem.getName());
            if (!shopItem.getLore().isEmpty()) meta.setLore(new ArrayList<>(shopItem.getLore()));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * Сохраняет страницу редактора.
     *
     * @param contents  слот → предмет, как он лежит сейчас
     * @param slotIds   слот → id позиции, которая была в этом слоте при открытии
     * @param pageIds   все id, отданные этой странице: чего не осталось — то удалено
     * @param originals id → исходный предмет: по нему узнаём позицию, если её переложили
     */
    public static boolean save(Map<Integer, ItemStack> contents,
                               Map<Integer, String> slotIds,
                               List<String> pageIds,
                               Map<String, ItemStack> originals) {
        YamlConfiguration config = loadYaml();
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items == null) items = config.createSection("items");

        Map<String, ItemStack> survived = new LinkedHashMap<>();
        List<ItemStack> unmatched = new ArrayList<>();

        // Шаг 1: клетка не тронута — предмет там же и такой же, каким был.
        List<Map.Entry<Integer, ItemStack>> rest = new ArrayList<>();
        for (Map.Entry<Integer, ItemStack> cell : contents.entrySet()) {
            ItemStack item = cell.getValue();
            if (item == null || item.getType() == Material.AIR) continue;
            String id = slotIds.get(cell.getKey());
            ItemStack original = id == null ? null : originals.get(id);
            if (id != null && original != null && original.isSimilar(item)
                    && !survived.containsKey(id)) {
                survived.put(id, item);
            } else {
                rest.add(cell);
            }
        }

        // Шаг 2: предмет переложили в другую клетку — узнаём его по содержимому.
        // Без этого перекладывание выглядело бы как «удалили одну позицию, создали другую»,
        // и товар терял бы цену, уровень и кулдаун.
        for (Map.Entry<Integer, ItemStack> cell : rest) {
            ItemStack item = cell.getValue();
            String matched = null;
            for (String id : pageIds) {
                if (survived.containsKey(id)) continue;
                ItemStack original = originals.get(id);
                if (original != null && original.isSimilar(item)) {
                    matched = id;
                    break;
                }
            }
            if (matched != null) survived.put(matched, item);
            else unmatched.add(item);
        }

        // Шаг 3: клетка, где лежала позиция, освободилась или занята чужим предметом —
        // старую позицию удаляем.
        for (String id : pageIds) {
            if (!survived.containsKey(id)) items.set(id, null);
        }

        // Шаг 4: изменённые и новые позиции.
        for (Map.Entry<String, ItemStack> entry : survived.entrySet()) {
            writeItem(items, entry.getKey(), entry.getValue(), false);
        }
        for (ItemStack item : unmatched) {
            writeItem(items, freeId(items, item), item, true);
        }
        return write(config);
    }

    /**
     * Пишет позицию в секцию items.
     *
     * Цена, уровень и кулдаун существующей позиции сохраняются: они правятся в конфиге,
     * а не мышкой. У новой позиции берутся значения по умолчанию.
     */
    private static void writeItem(ConfigurationSection items, String id, ItemStack item,
                                  boolean isNew) {
        ConfigurationSection existing = items.getConfigurationSection(id);
        double price = existing != null ? existing.getDouble("price", DEFAULT_PRICE) : DEFAULT_PRICE;
        int level = existing != null ? existing.getInt("level", DEFAULT_LEVEL) : DEFAULT_LEVEL;
        int cooldown = existing != null ? existing.getInt("cooldown", 0) : 0;
        List<String> commands = existing != null ? existing.getStringList("commands") : null;

        ConfigurationSection entry = items.createSection(id);
        entry.set("name", displayName(item));
        entry.set("material", item.getType().name());
        entry.set("amount", item.getAmount());
        entry.set("price", price);
        entry.set("level", level);
        if (cooldown > 0) entry.set("cooldown", cooldown);
        if (commands != null && !commands.isEmpty()) entry.set("commands", commands);
        if (isNew) entry.set(EDITOR_FLAG, true);
        else if (existing != null && existing.getBoolean(EDITOR_FLAG, false)) {
            entry.set(EDITOR_FLAG, true);
        }

        // Лор предмета переносим как есть: администратор мог подписать товар вручную.
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasLore()) {
            List<String> lore = new ArrayList<>();
            for (String line : meta.getLore()) {
                if (line != null) lore.add(line.replace('\u00a7', '&'));
            }
            if (!lore.isEmpty()) entry.set("lore", lore);
        }

        // Зачарования, эффекты зелий, прочность, флаги и цвет кожи — что положили,
        // то покупатель и получит, включая «невозможные» сочетания уникальных предметов.
        Map<String, Object> values = new LinkedHashMap<>();
        ShopItemMeta.write(values, item);
        for (Map.Entry<String, Object> value : values.entrySet()) {
            entry.set(value.getKey(), value.getValue());
        }
    }

    /** Свободный id для новой позиции: читаемый, по материалу предмета. */
    private static String freeId(ConfigurationSection items, ItemStack item) {
        String base = item.getType().name().toLowerCase(Locale.ROOT);
        if (!items.isSet(base)) return base;
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "_" + i;
            if (!items.isSet(candidate)) return candidate;
        }
        return base + "_" + System.currentTimeMillis();
    }

    /** Имя для витрины: своё, если предмет переименован, иначе — по материалу. */
    private static String displayName(ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            // Своё название сохраняем целиком, только приводим цвет к фирменному.
            // Если игрок сам покрасил предмет — его цвета остаются нетронутыми.
            String own = meta.getDisplayName().replace('\u00a7', '&');
            return hasColor(own) ? own : "&#F8BEFB" + own;
        }
        String raw = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return "&#F8BEFB" + Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    /** Есть ли в строке хоть один код цвета: &a, &#RRGGBB и подобные. */
    private static boolean hasColor(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '&') return true;
        }
        return false;
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
