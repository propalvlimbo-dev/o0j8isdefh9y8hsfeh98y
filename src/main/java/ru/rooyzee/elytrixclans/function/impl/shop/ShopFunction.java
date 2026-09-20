package ru.rooyzee.elytrixclans.function.impl.shop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.function.impl.shop.config.ItemsConfiguration;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitPreviewInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.function.impl.shop.object.ShopItem;
import ru.rooyzee.elytrixclans.function.impl.shop.util.BuyManager;
import ru.rooyzee.elytrixclans.function.impl.shop.util.PurchaseCooldownStorage;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

/**
 * Витрина магазина: /clan shop открывает сразу товары, без промежуточного меню категорий.
 *
 * Раскладка 54 слота: товары в слотах контента, ряды-рамки заняты стеклом
 * (кроме слотов 10, 16, 37, 43 — они отданы под товар), слот 52 — предыдущая страница,
 * слот 53 — следующая страница.
 *
 * Наборы показываются в конце ассортимента отдельными шалкерами: клик по такому предмету
 * (любой кнопкой) открывает меню предпросмотра набора.
 */
public class ShopFunction implements InventoryHolder {

    public static final String NBT_PAGE = "clanShopPage";
    public static final String NBT_KIT = "clanShopKit";
    /** Пометки позиций, у которых в лоре тикает таймер перезарядки. */
    public static final String NBT_COOLDOWN_ITEM = "clanShopCdItem";
    public static final String NBT_COOLDOWN_KIT = "clanShopCdKit";

    /** Слоты под товар. 10, 16, 37 и 43 добавлены сюда — стекла там больше нет. */
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int PREV_PAGE_SLOT = 52;
    private static final int NEXT_PAGE_SLOT = 53;

    private final Inventory inventory;
    private final int page;
    /** Владелец открытого меню — нужен тикеру, который обновляет таймеры перезарядки. */
    private final Player viewer;
    /** Уровень клана на момент сборки: если он вырос, меню пересобирается целиком. */
    private final int builtForLevel;

    public ShopFunction(Player player) {
        this(player, 0);
    }

    public ShopFunction(Player player, int page) {
        List<Object> entries = collectEntries();
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) CONTENT_SLOTS.length));
        // Страницу нормализуем, чтобы «вперёд» с последней страницы не открывало пустое меню.
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;
        this.page = page;

        inventory = Bukkit.createInventory(this, 54, HexUtil.translateHexColorCodes(
                "&#F8BEFB&lМагазин клана &7(" + (page + 1) + "/" + pages + ")"));

        MenuUtil.applyLayout(inventory);
        // Слоты 10, 16, 37 и 43 по требованию отданы под товар — стекла там нет.
        MenuUtil.clearShopFreedSlots(inventory);

        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        int clanLevel = clanLevel(clan);
        double balance = Main.getInstance().getBuyManager().getBalance(player);
        this.viewer = player;
        this.builtForLevel = clanLevel;

        int from = page * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int index = from + i;
            if (index >= entries.size()) break;
            Object entry = entries.get(index);
            if (entry instanceof ShopItem) {
                inventory.setItem(CONTENT_SLOTS[i], buildItemIcon(player, (ShopItem) entry, clanLevel));
            } else if (entry instanceof Kit) {
                inventory.setItem(CONTENT_SLOTS[i], buildKitIcon(player, (Kit) entry, clanLevel));
            }
        }

        inventory.setItem(4, MenuUtil.createInfoItem(clan, balance));
        inventory.setItem(45, MenuUtil.createCloseButton());
        if (page > 0) {
            inventory.setItem(PREV_PAGE_SLOT, pageButton(false, page));
        }
        if (page < pages - 1) {
            inventory.setItem(NEXT_PAGE_SLOT, pageButton(true, page));
        }
    }

    /**
     * Ассортимент витрины: товары и наборы идут вперемешку по уровню клана,
     * внутри уровня сначала товары (по цене), а набор этого уровня — последним.
     * Так набор «Новичок» стоит сразу за товарами 1 уровня, а не в конце магазина.
     */
    private static List<Object> collectEntries() {
        List<Object> entries = new ArrayList<>();
        ItemsConfiguration config = Main.getInstance().getItemsConfiguration();
        if (config != null) entries.addAll(config.getItems());
        if (Main.getInstance().getKitManager() != null) {
            entries.addAll(Main.getInstance().getKitManager().getKits());
        }
        // Явный тип компаратора: в Java 8 цепочка thenComparing* на List<Object> без него
        // не выводится.
        Comparator<Object> order = Comparator
                .<Object>comparingInt(ShopFunction::entryLevel)
                .thenComparingInt(entry -> entry instanceof Kit ? 1 : 0)
                .thenComparingDouble(ShopFunction::entryPrice);
        entries.sort(order);
        return entries;
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

    private static int clanLevel(Clan clan) {
        if (clan == null) return 0;
        Level level = LevelUtil.getClanLevel(clan.getExp());
        return level != null ? level.getLevel() : 1;
    }

    private ItemStack buildItemIcon(Player player, ShopItem shopItem, int clanLevel) {
        boolean locked = clanLevel < shopItem.getRequiredLevel();
        long cooldown = locked ? 0L
                : Main.getInstance().getBuyManager().remainingCooldown(player, BuyManager.itemKey(shopItem));

        // Позиция ещё не открыта: ни названия, ни цены, ни количества — только нужный уровень.
        // Так витрина не спойлерит содержимое будущих уровней.
        if (locked) {
            return lockedIcon(shopItem.getRequiredLevel());
        }

        List<String> lore = new ArrayList<>();
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        lore.addAll(shopItem.getLore());
        if (!shopItem.getLore().isEmpty()) {
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        }
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fКоличество: &#F8BEFB" + shopItem.getAmount()));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтоимость: &#F8BEFB"
                + MenuUtil.money(shopItem.getPrice()) + " монет"));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fТребуется уровень: &#F8BEFB"
                + shopItem.getRequiredLevel()));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));

        if (cooldown > 0) {
            ItemStack barrier = cooldownIcon(shopItem.getName(), lore, cooldown);
            // Кулдаун тикает: помечаем позицию, чтобы таймер обновлялся прямо в открытом меню.
            NBTUtil.addItemNBT(barrier, NBT_COOLDOWN_ITEM, shopItem.getId());
            return barrier;
        }
        lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для покупки"));
        return ItemsConfiguration.buildDisplayItem(shopItem, lore);
    }

    private ItemStack buildKitIcon(Player player, Kit kit, int clanLevel) {
        boolean locked = clanLevel < kit.getRequiredLevel();
        long cooldown = locked ? 0L
                : Main.getInstance().getBuyManager().remainingCooldown(player, BuyManager.kitKey(kit));

        // Как и с товарами: закрытый уровнем набор не показывает ни названия, ни состава.
        if (locked) {
            return lockedIcon(kit.getRequiredLevel());
        }

        List<String> lore = new ArrayList<>();
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНабор предметов: &#F8BEFB"
                + kit.getItems().size() + " шт."));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтоимость: &#F8BEFB"
                + MenuUtil.money(kit.getPrice()) + " монет"));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fТребуется уровень: &#F8BEFB"
                + kit.getRequiredLevel()));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));

        if (cooldown > 0) {
            ItemStack barrier = cooldownIcon(kit.getDisplayName(), lore, cooldown);
            NBTUtil.addItemNBT(barrier, NBT_KIT, kit.getId());
            NBTUtil.addItemNBT(barrier, NBT_COOLDOWN_KIT, kit.getId());
            return barrier;
        }

        Material material = Material.matchMaterial(kit.getIconMaterial());
        if (material == null) material = Material.SHULKER_BOX;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(kit.getDisplayName());
            List<String> kitLore = new ArrayList<>(lore);
            kitLore.add(HexUtil.translateHexColorCodes("&7● &fНажмите, чтобы посмотреть состав"));
            meta.setLore(kitLore);
            stack.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(stack, NBT_KIT, kit.getId());
        return stack;
    }

    /**
     * Недоступная по уровню позиция. Ни название, ни цена, ни количество не показываются:
     * игрок видит только то, на каком уровне клана здесь что-то появится.
     */
    private ItemStack lockedIcon(int requiredLevel) {
        List<String> lore = new ArrayList<>();
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fОткроется на &#F8BEFB"
                + requiredLevel + " &fуровне клана"));
        lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
        return barrier(HexUtil.translateHexColorCodes("&7« &cЗакрыто &7»"), lore);
    }

    /**
     * Позиция на перезарядке — зачарованные часы: стрелка крутится сама, поэтому слот
     * читается как «идёт отсчёт». Барьер тут смотрелся как «запрещено навсегда».
     */
    private ItemStack cooldownIcon(String displayName, List<String> baseLore, long cooldownMillis) {
        List<String> lore = new ArrayList<>(baseLore);
        lore.add(HexUtil.translateHexColorCodes("&c● Перезарядка покупки"));
        lore.add(HexUtil.translateHexColorCodes("&c● Доступно через &#F8BEFB"
                + PurchaseCooldownStorage.format(cooldownMillis)));

        ItemStack stack = new ItemStack(Material.CLOCK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            // Блеск без надписи о зачаровании.
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            stack.setItemMeta(meta);
        }
        stack.addUnsafeEnchantment(Enchantment.DURABILITY, 1);
        return stack;
    }

    private ItemStack barrier(String displayName, List<String> lore) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack pageButton(boolean next, int currentPage) {
        // Обе кнопки — чёрный краситель: так они смотрятся как одна пара элементов навигации.
        ItemStack item = new ItemStack(Material.BLACK_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(next
                    ? "&7« &#F8BEFBСледующая страница &7»"
                    : "&7« &#F8BEFBПредыдущая страница &7»"));
            meta.setLore(Collections.singletonList(
                    HexUtil.translateHexColorCodes("&7● &fНажмите для перехода")));
            item.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(item, NBT_PAGE, String.valueOf(next ? currentPage + 1 : currentPage - 1));
        return item;
    }

    /**
     * Обновление таймеров в уже открытом меню — вызывается раз в секунду из ShopTicker.
     *
     * Раньше, чтобы увидеть новое время (или дождаться, когда позиция снова станет
     * доступной), меню приходилось закрывать и открывать. Теперь лор барьера перерисовывается
     * на месте, а как только перезарядка кончилась — на слот возвращается сам товар.
     */
    public void tickCooldowns() {
        if (viewer == null || !viewer.isOnline()) return;
        Main main = Main.getInstance();
        if (main == null || main.getBuyManager() == null) return;

        Clan clan = main.getClanManager() != null ? main.getClanManager().getPlayerClan(viewer) : null;
        int clanLevel = clanLevel(clan);
        // Клан взял новый уровень, пока меню открыто: перерисовываем всю страницу,
        // иначе только что открывшиеся позиции остались бы барьерами.
        if (clanLevel != builtForLevel) {
            viewer.openInventory(new ShopFunction(viewer, page).getInventory());
            return;
        }

        for (int slot : CONTENT_SLOTS) {
            ItemStack current = inventory.getItem(slot);
            if (current == null) continue;

            String itemId = NBTUtil.getNBTvalue(current, NBT_COOLDOWN_ITEM);
            if (itemId != null) {
                ShopItem shopItem = main.getItemsConfiguration() != null
                        ? main.getItemsConfiguration().getItem(itemId) : null;
                if (shopItem != null) {
                    inventory.setItem(slot, buildItemIcon(viewer, shopItem, clanLevel));
                }
                continue;
            }

            String kitId = NBTUtil.getNBTvalue(current, NBT_COOLDOWN_KIT);
            if (kitId != null) {
                Kit kit = main.getKitManager() != null ? main.getKitManager().getKit(kitId) : null;
                if (kit != null) {
                    inventory.setItem(slot, buildKitIcon(viewer, kit, clanLevel));
                }
            }
        }
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (NBTUtil.hasItemNBT(item, NBT_PAGE)) {
            int target = parseInt(NBTUtil.getNBTvalue(item, NBT_PAGE), this.page);
            player.openInventory(new ShopFunction(player, target).getInventory());
            return;
        }

        if (NBTUtil.hasItemNBT(item, NBT_KIT)) {
            // ЛКМ и ПКМ одинаково открывают предпросмотр набора.
            String kitId = NBTUtil.getNBTvalue(item, NBT_KIT);
            Kit kit = Main.getInstance().getKitManager().getKit(kitId);
            if (kit == null) return;
            player.openInventory(new KitPreviewInventory(player, kit, this.page).getInventory());
            return;
        }

        // Барьер заблокированной позиции намеренно без NBT товара: клик по нему ничего не делает.

        if (NBTUtil.hasItemNBT(item, ItemsConfiguration.NBT_SHOP_ITEM)) {
            String id = NBTUtil.getNBTvalue(item, ItemsConfiguration.NBT_SHOP_ITEM);
            ShopItem shopItem = Main.getInstance().getItemsConfiguration().getItem(id);
            if (shopItem == null) return;
            Main.getInstance().getBuyManager().buyItem(player, shopItem);
            // Обновляем страницу: изменился баланс, а с новым уровнем могли открыться позиции.
            player.openInventory(new ShopFunction(player, this.page).getInventory());
        }
    }

    private static int parseInt(String raw, int def) {
        if (raw == null) return def;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
