package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;

/**
 * Редактирование содержимого набора.
 *
 * Открывается кликом по набору в меню /elytrixclan edititem — отдельной команды нет.
 * Открывается тот же набор, который покупают игроки: что лежит в клетках, то и выдаётся.
 * Складывайте предметы прямо из инвентаря, закройте окно — набор сохранён в shop_item.yml.
 *
 * Цена, уровень и кулдаун набора здесь не меняются: цена живёт в shop/prices.yml,
 * остальное — в shop_item.yml. Так редактирование содержимого не сбрасывает настройки.
 */
public class KitEditInventory implements InventoryHolder {

    /** Клетки под предметы набора: 4 ряда, 28 штук — больше в набор и не кладут. */
    public static final int[] EDIT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final Inventory inventory;
    private final UUID owner;
    private final String kitId;
    /** Страница редактора магазина, с которой пришли: на неё и вернёмся после закрытия. */
    private final int backPage;
    private boolean saved;

    public KitEditInventory(Player player, Kit kit) {
        this(player, kit, -1);
    }

    public KitEditInventory(Player player, Kit kit, int backPage) {
        this.owner = player == null ? null : player.getUniqueId();
        this.kitId = kit.getId();
        this.backPage = backPage;

        inventory = Bukkit.createInventory(this, 54, HexUtil.translateHexColorCodes(
                "&#F8BEFBРедактор: " + stripColors(kit.getDisplayName())));

        MenuUtil.applyLayout(inventory);
        for (int slot : EDIT_SLOTS) {
            inventory.setItem(slot, null);
        }

        // Раскладываем текущее содержимое набора по клеткам по порядку.
        List<ItemStack> items = kit.getItems();
        for (int i = 0; i < items.size() && i < EDIT_SLOTS.length; i++) {
            ItemStack item = items.get(i);
            if (item != null) inventory.setItem(EDIT_SLOTS[i], item.clone());
        }

        inventory.setItem(4, infoItem(kit, items.size()));
        inventory.setItem(45, MenuUtil.createCloseButton());
    }

    public static boolean isEditableSlot(int slot) {
        for (int editable : EDIT_SLOTS) {
            if (editable == slot) return true;
        }
        return false;
    }

    private ItemStack infoItem(Kit kit, int count) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(
                    "&7« &#F8BEFB" + stripColors(kit.getDisplayName()) + " &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПредметов в наборе: &#F8BEFB" + count));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fУровень клана: &#F8BEFB"
                    + kit.getRequiredLevel()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЦена: &#F8BEFB"
                    + MenuUtil.money(kit.getPrice()) + " монет"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fId набора: &#F8BEFB" + kit.getId()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fПоложите предметы в клетки"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЗакрыли меню — набор сохранён"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЦена правится в &#F8BEFBshop/prices.yml"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Содержимое клеток по порядку: именно в таком виде набор попадёт в конфиг. */
    public List<ItemStack> snapshot() {
        List<ItemStack> out = new ArrayList<>();
        for (int slot : EDIT_SLOTS) {
            if (slot >= inventory.getSize()) continue;
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            out.add(item.clone());
        }
        return out;
    }

    /** Страница редактора магазина для возврата, либо -1 если возвращаться некуда. */
    public int getBackPage() {
        return backPage;
    }

    public String getKitId() {
        return kitId;
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
