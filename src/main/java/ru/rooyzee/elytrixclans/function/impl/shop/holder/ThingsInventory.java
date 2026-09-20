package ru.rooyzee.elytrixclans.function.impl.shop.holder;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.ShulkerHolder;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.SpawnerHolder;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.things.ThingsHolder;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

public class ThingsInventory implements InventoryHolder {

    private final Inventory inventory;

    public ThingsInventory(Player player) {
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lРесурсы"));

        MenuUtil.applyLayout(inventory);

        inventory.setItem(20, createCategoryItem(Material.WHITE_SHULKER_BOX, "&7« &#F8BEFBШалкеры &7»",
                "&#F8BEFB&l┃ &fЦветные шалкеры: &#F8BEFBДоступно", "shulkersItem"));

        inventory.setItem(22, createCategoryItem(Material.SPAWNER, "&7« &#F8BEFBСпавнеры &7»",
                "&#F8BEFB&l┃ &fЯйца и спавнеры: &#F8BEFBДоступно", "spawnerItem"));

        inventory.setItem(24, createCategoryItem(Material.END_CRYSTAL, "&7« &#F8BEFBВещи &7»",
                "&#F8BEFB&l┃ &fПредметы и стрелы: &#F8BEFBДоступно", "shopThingsItem"));

        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        String points = clan != null ? String.valueOf(clan.getPoints()) : "0";
        inventory.setItem(4, MenuUtil.createInfoItem(points));
        inventory.setItem(45, MenuUtil.createBackButton());
    }

    private ItemStack createCategoryItem(Material material, String name, String info, String nbtKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes(name));
            meta.setLore(Arrays.asList(
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes(info),
                    HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "),
                    HexUtil.translateHexColorCodes("&7● &fНажмите для перехода")
            ));
            item.setItemMeta(meta);
        }
        NBTUtil.addItemNBT(item, nbtKey, "");
        return item;
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getCurrentItem() == null || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (NBTUtil.hasItemNBT(item, "shulkersItem")) {
            player.openInventory(new ShulkerHolder(player).getInventory());
        } else if (NBTUtil.hasItemNBT(item, "spawnerItem")) {
            player.openInventory(new SpawnerHolder(player).getInventory());
        } else if (NBTUtil.hasItemNBT(item, "shopThingsItem")) {
            player.openInventory(new ThingsHolder(player).getInventory());
        } else if (NBTUtil.hasItemNBT(item, "arrowItem")) {
            player.openInventory(new ShopFunction(player).getInventory());
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}