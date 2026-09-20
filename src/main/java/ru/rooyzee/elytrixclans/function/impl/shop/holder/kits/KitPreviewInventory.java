package ru.rooyzee.elytrixclans.function.impl.shop.holder.kits;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

public class KitPreviewInventory implements InventoryHolder {

    private final Inventory inventory;
    private final Kit kit;

    public KitPreviewInventory(Player player, Kit kit) {
        this.kit = kit;
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lПревью: " + kit.getDisplayName()));

        MenuUtil.applyLayout(inventory);

        int[] previewSlots = {
                11, 12, 13, 14, 15,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                38, 39, 40, 41, 42
        };

        int i = 0;
        for (ItemStack item : kit.getItems()) {
            if (i >= previewSlots.length) break;
            ItemStack preview = item.clone();
            inventory.setItem(previewSlots[i], preview);
            i++;
        }

        ItemStack buyBtn = new ItemStack(Material.EMERALD);
        ItemMeta buyMeta = buyBtn.getItemMeta();
        if (buyMeta != null) {
            buyMeta.setDisplayName(HexUtil.translateHexColorCodes("&7« &aКупить набор &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНабор: " + kit.getDisplayName()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЦена: &#F8BEFB" + kit.getPrice() + " &7поинтов"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для покупки"));
            buyMeta.setLore(lore);
            buyBtn.setItemMeta(buyMeta);
        }
        NBTUtil.addItemNBT(buyBtn, "kit_buy", kit.getId());
        inventory.setItem(49, buyBtn);

        inventory.setItem(45, MenuUtil.createBackButton());
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();

        if (NBTUtil.hasItemNBT(item, "arrowItem")) {
            player.openInventory(new KitsInventory(player).getInventory());
            return;
        }

        if (NBTUtil.hasItemNBT(item, "kit_buy")) {
            String kitId = NBTUtil.getNBTvalue(item, "kit_buy");
            if (kitId == null) return;
            Kit kit = Main.getInstance().getKitManager().getKit(kitId);
            if (kit == null) return;
            Main.getInstance().getBuyManager().buyKit(player, kit);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}