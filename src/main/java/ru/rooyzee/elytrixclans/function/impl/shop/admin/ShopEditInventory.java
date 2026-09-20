package ru.rooyzee.elytrixclans.function.impl.shop.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Админ-меню /elytrixclan edititem.
 *
 * Три ряда пустых клеток: кладёте туда предметы прямо из инвентаря, закрываете окно —
 * позиции попадают в магазин. Цена задаётся отдельно, в shop/prices.yml, по id клетки
 * (он подписан в лоре подсказки: edit_s10, edit_s11 и так далее).
 *
 * Меню намеренно выглядит как витрина /clan shop: та же рамка, те же клетки под товар,
 * чтобы было видно, как позиция встанет в магазине.
 */
public class ShopEditInventory implements InventoryHolder {

    private final Inventory inventory;
    private final UUID owner;
    /** Сохранение выполняется один раз: и по кнопке, и по закрытию окна. */
    private boolean saved;

    public ShopEditInventory(Player player) {
        this.owner = player == null ? null : player.getUniqueId();

        inventory = Bukkit.createInventory(this, 54, HexUtil.translateHexColorCodes(
                "&#F8BEFB&lРедактор магазина"));

        MenuUtil.applyLayout(inventory);
        // Освобождаем ровно те клетки, куда кладут предметы.
        for (int slot : ShopEditStorage.EDIT_SLOTS) {
            inventory.setItem(slot, null);
        }

        YamlConfiguration config = ShopEditStorage.loadYaml();
        for (Map.Entry<Integer, ItemStack> entry : ShopEditStorage.preview(config).entrySet()) {
            inventory.setItem(entry.getKey(), entry.getValue());
        }

        inventory.setItem(4, infoItem());
        inventory.setItem(45, MenuUtil.createCloseButton());
    }

    private ItemStack infoItem() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&7« &#F8BEFBРедактор магазина &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПоложите предметы в свободные клетки."));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЗакрыли меню — товары в магазине."));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЦена задаётся в &#F8BEFBshop/prices.yml"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fId клетки: &#F8BEFBedit_s&f + номер слота"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНапример верхняя левая: &#F8BEFBedit_s10"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fБез цены товар стоит &#F8BEFB"
                    + MenuUtil.money(ShopEditStorage.DEFAULT_PRICE) + " &fмонет"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fУровень и кулдаун — в &#F8BEFBshop_item.yml"));
            lore.add(HexUtil.translateHexColorCodes("&7● &fЗабрали предмет — позиция удалена"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Снимок клеток: слот → предмет. Пустые клетки в карту не попадают. */
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
