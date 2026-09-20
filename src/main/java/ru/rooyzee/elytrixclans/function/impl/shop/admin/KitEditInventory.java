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
 * Редактор содержимого набора (kit). Открыть может только админ: окно выглядит как
 * превью набора из /clan shop, но клетки пустые и в них можно класть предметы.
 * Сохранение — при закрытии окна; цена, имя, иконка и слот набора не меняются.
 */
public class KitEditInventory implements InventoryHolder, EditableInventory {

    private final Inventory inventory;
    private final UUID owner;
    private final String kitId;
    private final String kitName;
    private final int kitPrice;
    private final int unfits;

    public KitEditInventory(Player player, String kitId, Kit kit) {
        this.owner = player == null ? null : player.getUniqueId();
        this.kitId = kitId;
        this.kitName = kit != null ? kit.getDisplayName() : kitId;
        this.kitPrice = kit != null ? kit.getPrice() : 0;

        String title = HexUtil.translateHexColorCodes("&#F8BEFB&lНабор: " + kitName + " &7(&cредактор&7)");
        inventory = Bukkit.createInventory(this, 54, title);

        MenuUtil.applyLayout(inventory);

        int index = 0;
        int skipped = 0;
        if (kit != null) {
            for (ItemStack item : kit.getItems()) {
                if (item == null || item.getType() == Material.AIR) continue;
                if (index >= ShopEditStorage.EDIT_SLOTS.length) {
                    skipped++;
                    continue;
                }
                inventory.setItem(ShopEditStorage.EDIT_SLOTS[index++], item.clone());
            }
        }
        this.unfits = skipped;

        inventory.setItem(4, createInfoItem(index));
        inventory.setItem(45, MenuUtil.createCloseButton());
    }

    private ItemStack createInfoItem(int items) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBСодержимое набора &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНабор: &#F8BEFB" + kitName));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЦена набора: &#F8BEFB" + kitPrice + " &7поинтов"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПредметов: &#F8BEFB" + items));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fПоложи предметы в клетки — это содержимое набора"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЛишнее можно забрать: чего нет в клетках, в наборе не будет"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЗачарования, эффекты и прочность предметов сохраняются"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЗакрыл меню — сохранилось"));
            if (unfits > 0) {
                lore.add(HexUtil.translateHexColorCodes("&8• &7не влезло в редактор предметов: &f" + unfits));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Предметы набора в том порядке, в котором они стоят в клетках. */
    public List<ItemStack> snapshot() {
        List<ItemStack> out = new ArrayList<>();
        for (int slot : ShopEditStorage.EDIT_SLOTS) {
            if (slot >= inventory.getSize()) continue;
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            out.add(item.clone());
        }
        return out;
    }

    public UUID getOwner() {
        return owner;
    }

    /** Сколько предметов набора не влезло в клетки редактора — их сохраняем как были. */
    public int getUnfits() {
        return unfits;
    }

    private boolean saved;

    @Override
    public boolean isSaved() {
        return saved;
    }

    @Override
    public void markSaved() {
        this.saved = true;
    }

    public String getKitId() {
        return kitId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
