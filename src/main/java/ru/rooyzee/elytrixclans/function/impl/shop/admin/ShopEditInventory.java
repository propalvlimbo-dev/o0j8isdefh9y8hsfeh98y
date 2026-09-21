package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

/**
 * Админ-меню /elytrixclan edititem — живая витрина магазина.
 *
 * Меню повторяет /clan shop один в один: те же слоты, те же страницы, те же товары в том
 * же порядке. Разница в том, что предметы здесь настоящие, а не «иконки»: их можно забрать
 * мышкой и положить свои. Что осталось в клетке после закрытия окна — то и продаётся.
 *
 * Наборы остаются иконками-шалкерами: по клику открывается отдельное меню с их
 * содержимым (KitEditInventory). Поэтому отдельная команда editkit не нужна.
 *
 * Позиции магазина привязаны не к клетке, а к своему id: перекладывание предмета между
 * слотами не плодит дубликатов, а цена, уровень и кулдаун остаются за позицией.
 */
public class ShopEditInventory implements InventoryHolder {

    /** Клетки под товар — ровно те же, что в витрине магазина. */
    public static final int[] EDIT_SLOTS = ShopFunction.CONTENT_SLOTS;

    /** NBT-пометка страницы на кнопках навигации редактора. */
    public static final String NBT_PAGE = "clanEditPage";
    /** NBT-пометка набора: по ней клик открывает редактор содержимого набора. */
    public static final String NBT_KIT = "clanEditKit";

    private static final int PREV_PAGE_SLOT = 52;
    private static final int NEXT_PAGE_SLOT = 53;

    private final Inventory inventory;
    private final UUID owner;
    private final int page;
    /** Позиции, показанные на этой странице: слот → id в shop_item.yml. */
    private final Map<Integer, String> slotIds = new LinkedHashMap<>();
    /** Id позиций, отданных этой странице: при сохранении сверяем, что исчезло. */
    private final List<String> pageIds = new ArrayList<>();
    /** Каким предмет был при открытии: по нему опознаём позицию, если её переложили. */
    private final Map<String, ItemStack> originalItems = new LinkedHashMap<>();
    private boolean saved;

    public ShopEditInventory(Player player) {
        this(player, 0);
    }

    public ShopEditInventory(Player player, int page) {
        this.owner = player == null ? null : player.getUniqueId();

        List<ShopEditStorage.Entry> entries = ShopEditStorage.entries();
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) EDIT_SLOTS.length));
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;
        this.page = page;

        inventory = Bukkit.createInventory(this, 54, HexUtil.translateHexColorCodes(
                "&#F8BEFB&lРедактор магазина &7(" + (page + 1) + "/" + pages + ")"));

        MenuUtil.applyLayout(inventory);
        MenuUtil.clearShopFreedSlots(inventory);
        for (int slot : EDIT_SLOTS) {
            inventory.setItem(slot, null);
        }

        int from = page * EDIT_SLOTS.length;
        for (int i = 0; i < EDIT_SLOTS.length; i++) {
            int index = from + i;
            if (index >= entries.size()) break;
            ShopEditStorage.Entry entry = entries.get(index);
            int slot = EDIT_SLOTS[i];

            if (entry.isKit()) {
                inventory.setItem(slot, kitIcon(entry.getKit()));
                continue;
            }
            inventory.setItem(slot, entry.getItem());
            slotIds.put(slot, entry.getId());
            pageIds.add(entry.getId());
            originalItems.put(entry.getId(), entry.getItem().clone());
        }

        inventory.setItem(4, infoItem(pages));
        inventory.setItem(45, MenuUtil.createCloseButton());
        if (page > 0) inventory.setItem(PREV_PAGE_SLOT, pageButton(false, page));
        if (page < pages - 1) inventory.setItem(NEXT_PAGE_SLOT, pageButton(true, page));
    }

    /** Иконка набора: кликом открывается редактор его содержимого. */
    private ItemStack kitIcon(Kit kit) {
        Material material = Material.matchMaterial(kit.getIconMaterial());
        if (material == null) material = Material.SHULKER_BOX;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(kit.getDisplayName());
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПредметов: &#F8BEFB"
                    + kit.getItems().size() + " шт."));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтоимость: &#F8BEFB"
                    + MenuUtil.money(kit.getPrice()) + " монет"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУровень: &#F8BEFB"
                    + kit.getRequiredLevel()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите, чтобы изменить состав"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fНабор нельзя забрать из клетки"));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(stack, NBT_KIT, kit.getId());
        return stack;
    }

    private ItemStack infoItem(int pages) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBРедактор магазина &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПеред вами витрина магазина."));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЗаберите предмет — позиция удалена."));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПоложите свой — он появится в продаже."));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПредмет сохраняется целиком:"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fзачарования, эффекты, название."));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fНовый товар стоит &#F8BEFB"
                    + MenuUtil.money(ShopEditStorage.DEFAULT_PRICE) + " &fмонет"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЦены — в &#F8BEFBshop/prices.yml"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fУровень и кулдаун — в &#F8BEFBshop_item.yml"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fСтраниц: &#F8BEFB" + pages));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack pageButton(boolean next, int currentPage) {
        ItemStack item = new ItemStack(Material.BLACK_DYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(next
                    ? "&7« &#F8BEFBСледующая страница &7»"
                    : "&7« &#F8BEFBПредыдущая страница &7»"));
            meta.setLore(Collections.singletonList(
                    HexUtil.translateHexColorCodes("&7● &fПравки страницы сохранятся")));
            item.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(item, NBT_PAGE, String.valueOf(next ? currentPage + 1 : currentPage - 1));
        return item;
    }

    /** Снимок клеток страницы: слот → предмет. Иконки наборов и пустые клетки пропускаются. */
    public Map<Integer, ItemStack> snapshot() {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        for (int slot : EDIT_SLOTS) {
            if (slot >= inventory.getSize()) continue;
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            // Набор — не товар: его содержимое правится в своём меню.
            if (NBTUtil.hasItemNBT(item, NBT_KIT)) continue;
            out.put(slot, item.clone());
        }
        return out;
    }

    /** Слот → id позиции, показанной в нём при открытии страницы. */
    public Map<Integer, String> getSlotIds() {
        return slotIds;
    }

    /** Все id позиций этой страницы: нужны, чтобы понять, какие из них забрали. */
    public List<String> getPageIds() {
        return pageIds;
    }

    /** Исходные предметы позиций: позволяют узнать товар, переложенный в другую клетку. */
    public Map<String, ItemStack> getOriginalItems() {
        return originalItems;
    }

    /** Набор по NBT-пометке иконки. */
    public static Kit kitOf(ItemStack item) {
        if (item == null || !NBTUtil.hasItemNBT(item, NBT_KIT)) return null;
        Main main = Main.getInstance();
        if (main == null || main.getKitManager() == null) return null;
        return main.getKitManager().getKit(NBTUtil.getNBTvalue(item, NBT_KIT));
    }

    public static boolean isEditableSlot(int slot) {
        for (int editable : EDIT_SLOTS) {
            if (editable == slot) return true;
        }
        return false;
    }

    public int getPage() {
        return page;
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean isSaved() {
        return saved;
    }

    public void markSaved() {
        this.saved = true;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
