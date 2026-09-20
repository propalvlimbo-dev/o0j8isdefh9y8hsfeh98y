package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;

/**
 * Редактор позиций категории магазина. Выглядит как меню магазина в /clan shop:
 * та же рамка, те же клетки, только предметы в них можно класть и забирать.
 * Сохранение — при закрытии окна.
 */
public class ShopEditInventory implements InventoryHolder, EditableInventory {

    private final Inventory inventory;
    private final UUID owner;
    private final String category;
    private final int outsideSlots;

    public ShopEditInventory(Player player, String category, YamlConfiguration config) {
        this.owner = player == null ? null : player.getUniqueId();
        this.category = category;

        String title = HexUtil.translateHexColorCodes(
                "&#F8BEFB&l" + ShopEditStorage.label(category) + " &7(&cредактор&7)");
        inventory = Bukkit.createInventory(this, 54, title);

        MenuUtil.applyLayout(inventory);

        int outside = 0;
        int inside = 0;
        for (Map.Entry<Integer, ItemStack> entry : ShopEditStorage.previewCategory(config, category).entrySet()) {
            int slot = entry.getKey();
            if (entry.getValue() == null) continue;
            if (ShopEditStorage.isEditableSlot(slot)) {
                inventory.setItem(slot, entry.getValue());
                inside++;
            } else {
                // Позиция стоит вне сетки редактора — показываем её в info-предмете,
                // но не трогаем: иначе админ нечаянно «удалил» бы то, что не видит.
                outside++;
            }
        }
        this.outsideSlots = outside;

        inventory.setItem(4, createInfoItem(inside));
        inventory.setItem(45, MenuUtil.createCloseButton());
    }

    private ItemStack createInfoItem(int positions) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(
                    "&7« &#F8BEFBРедактор: " + ShopEditStorage.label(category) + " &7»"));
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПозиций в редакторе: &#F8BEFB" + positions));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЦена новой позиции: &#F8BEFB"
                    + ShopEditStorage.defaultPrice() + " &7поинтов"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fПоложи предмет в клетку — он появится в магазине"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fУберёшь предмет — позиция удалится"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЗачарования, эффекты, прочность — какие положил, такие и продаются"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЦену, имя и лор существующих позиций не трогаем"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЗакрыл меню — сохранилось"));
            if (outsideSlots > 0) {
                lore.add(HexUtil.translateHexColorCodes("&8• &7вне сетки редактора осталось позиций: &f"
                        + outsideSlots));
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Снимок редактируемых клеток: слот → предмет (пустых клеток в карте нет). */
    public Map<Integer, ItemStack> snapshot() {
        Map<Integer, ItemStack> out = new LinkedHashMap<>();
        for (int slot : ShopEditStorage.EDIT_SLOTS) {
            if (slot >= inventory.getSize()) continue;
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            out.put(slot, item.clone());
        }
        return out;
    }

    public UUID getOwner() {
        return owner;
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

    public String getCategory() {
        return category;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
