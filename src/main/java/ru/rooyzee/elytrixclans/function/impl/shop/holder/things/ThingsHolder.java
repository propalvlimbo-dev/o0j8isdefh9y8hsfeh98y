package ru.rooyzee.elytrixclans.function.impl.shop.holder.things;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.ThingsInventory;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

public class ThingsHolder implements InventoryHolder {

    private final Inventory inventory;

    public ThingsHolder(Player player) {
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lВещи"));
        MenuUtil.applyLayout(inventory);
        Main.getInstance().getItemsConfiguration().setItems(inventory, "things");
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        String points = clan != null ? String.valueOf(clan.getPoints()) : "0";
        inventory.setItem(4, MenuUtil.createInfoItem(points));
        inventory.setItem(45, MenuUtil.createBackButton());
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (NBTUtil.hasItemNBT(item, "arrowItem")) {
            player.openInventory(new ThingsInventory(player).getInventory());
        } else {
            Main.getInstance().getBuyManager().buyItem(player, item);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}