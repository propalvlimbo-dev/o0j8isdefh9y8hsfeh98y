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
import ru.rooyzee.elytrixclans.function.impl.shop.holder.ThingsInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitPreviewInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitsInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.ShulkerHolder;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.SpawnerHolder;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.ThingsHolder;

public class InventoryDragListener implements Listener {

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory topInv = event.getView().getTopInventory();
        InventoryHolder holder = topInv.getHolder();
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
                || holder instanceof ThingsInventory
                || holder instanceof ThingsHolder
                || holder instanceof SpawnerHolder
                || holder instanceof ShulkerHolder
                || holder instanceof GlowFunction
                || holder instanceof MemberInventory
                || holder instanceof KitsInventory
                || holder instanceof KitPreviewInventory;
    }
}