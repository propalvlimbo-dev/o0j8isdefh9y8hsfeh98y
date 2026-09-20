package ru.rooyzee.elytrixclans.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
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

        // Действия, которые могут ВЫТАЩИТЬ предмет из меню, не кликая по нему напрямую:
        // shift-клик из своего инвентаря, раскладка по номерным клавишам, свап оффхендом,
        // двойной клик «собрать всё» и сбор одинаковых предметов курсором.
        InventoryAction action = event.getAction();
        if (event.isShiftClick()
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getClick() == ClickType.DOUBLE_CLICK
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            event.setCancelled(true);
            if (event.getClickedInventory() != topInv) return;
        }

        if (event.getClickedInventory() != topInv) return;

        // Клик мимо слотов (по краю окна) — слот -999; дальше он ничего полезного не даёт.
        if (event.getSlot() < 0) {
            event.setCancelled(true);
            return;
        }

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