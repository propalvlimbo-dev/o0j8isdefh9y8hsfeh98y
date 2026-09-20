package ru.rooyzee.elytrixclans.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.rooyzee.elytrixclans.function.impl.glow.GlowFunction;
import ru.rooyzee.elytrixclans.function.impl.info.InfoFunction;
import ru.rooyzee.elytrixclans.function.impl.info.MemberInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.KitEditInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.ShopEditInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.ShopEditStorage;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitPreviewInventory;

public class InventoryDragListener implements Listener {

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory topInv = event.getView().getTopInventory();
        InventoryHolder holder = topInv.getHolder();

        // В редакторе перетаскивание разрешено, но только по клеткам под товар.
        if (holder instanceof ShopEditInventory || holder instanceof KitEditInventory) {
            boolean kitEditor = holder instanceof KitEditInventory;
            int editorSize = topInv.getSize();
            for (int slot : event.getRawSlots()) {
                if (slot >= editorSize) continue;
                boolean editable = kitEditor
                        ? KitEditInventory.isEditableSlot(slot)
                        : ShopEditStorage.isEditableSlot(slot);
                if (!editable) {
                    event.setCancelled(true);
                    return;
                }
            }
            return;
        }

        if (!isMenuHolder(holder)) return;
        int topSize = topInv.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean isMenuHolder(InventoryHolder holder) {
        return holder instanceof InfoFunction
                || holder instanceof ShopFunction
                || holder instanceof GlowFunction
                || holder instanceof MemberInventory
                || holder instanceof KitPreviewInventory;
    }
}