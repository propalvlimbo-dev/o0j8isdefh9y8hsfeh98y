package ru.rooyzee.elytrixclans.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import ru.rooyzee.elytrixclans.function.impl.glow.GlowFunction;
import ru.rooyzee.elytrixclans.function.impl.info.InfoFunction;
import ru.rooyzee.elytrixclans.function.impl.info.MemberInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitPreviewInventory;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

public class InventoryClickListener implements Listener {

    @EventHandler
    public void onInvClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        Inventory topInv = player.getOpenInventory().getTopInventory();
        InventoryHolder holder = topInv.getHolder();
        if (!isMenuHolder(holder)) return;

        if (event.getClickedInventory() != topInv) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        if (event.getSlot() < 0) return;

        if (event.getCurrentItem() != null && NBTUtil.hasItemNBT(event.getCurrentItem(), "closeItem")) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        if (holder instanceof InfoFunction) ((InfoFunction) holder).onInventoryClick(event);
        else if (holder instanceof ShopFunction) ((ShopFunction) holder).onInventoryClick(event);
        else if (holder instanceof KitPreviewInventory) ((KitPreviewInventory) holder).onInventoryClick(event);
        else if (holder instanceof GlowFunction) ((GlowFunction) holder).onInventoryClick(event);
        else if (holder instanceof MemberInventory) ((MemberInventory) holder).onInventoryClick(event);
    }

    private boolean isMenuHolder(InventoryHolder holder) {
        return holder instanceof InfoFunction
                || holder instanceof ShopFunction
                || holder instanceof GlowFunction
                || holder instanceof MemberInventory
                || holder instanceof KitPreviewInventory;
    }
}