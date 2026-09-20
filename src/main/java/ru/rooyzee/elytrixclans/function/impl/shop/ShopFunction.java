package ru.rooyzee.elytrixclans.function.impl.shop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.function.impl.shop.config.ShopMenuConfiguration;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.ThingsInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitsInventory;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

public class ShopFunction implements InventoryHolder {

    private final Inventory inventory;

    public ShopFunction(Player player) {
        String title = Main.getInstance().getShopMenuConfiguration().getTitle("main");
        inventory = Bukkit.createInventory(this, 54, title);

        MenuUtil.applyLayout(inventory);

        for (ShopMenuConfiguration.CategoryButton btn : Main.getInstance().getShopMenuConfiguration().getButtons("main")) {
            inventory.setItem(btn.getSlot(), btn.getItem());
        }

        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        String points = clan != null ? String.valueOf(clan.getPoints()) : "0";
        inventory.setItem(4, MenuUtil.createInfoItem(points));
        inventory.setItem(45, MenuUtil.createCloseButton());
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();

        if (!NBTUtil.hasItemNBT(item, "shop_action")) return;
        String action = NBTUtil.getNBTvalue(item, "shop_action");
        if (action == null) return;

        switch (action.toLowerCase()) {
            case "things":
                player.openInventory(new ThingsInventory(player).getInventory());
                break;
            case "kits":
                player.openInventory(new KitsInventory(player).getInventory());
                break;
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}