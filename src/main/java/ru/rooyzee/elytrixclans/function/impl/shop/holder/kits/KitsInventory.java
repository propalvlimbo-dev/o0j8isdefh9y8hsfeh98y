package ru.rooyzee.elytrixclans.function.impl.shop.holder.kits;

import java.util.ArrayList;
import java.util.Arrays;
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
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

public class KitsInventory implements InventoryHolder {

    private final Inventory inventory;

    public KitsInventory(Player player) {
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lКиты"));

        MenuUtil.applyLayout(inventory);

        for (Kit kit : Main.getInstance().getKitManager().getKits()) {
            try {
                Material material = Material.valueOf(kit.getIconMaterial().toUpperCase());
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(HexUtil.translateHexColorCodes("&7« " + kit.getDisplayName() + " &7»"));
                    List<String> lore = new ArrayList<>();
                    lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
                    lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fПредметов в наборе: &#F8BEFB" + kit.getItems().size()));
                    lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fЦена: &#F8BEFB" + kit.getPrice() + " &7поинтов"));
                    lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
                    lore.add(HexUtil.translateHexColorCodes("&7● &fЛКМ &7— &fПросмотреть содержимое"));
                    lore.add(HexUtil.translateHexColorCodes("&7● &fПКМ &7— &fКупить набор"));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
                NBTUtil.addItemNBT(item, "kit_id", kit.getId());
                inventory.setItem(kit.getSlot(), item);
            } catch (Exception ignored) {
            }
        }

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
            player.openInventory(new ShopFunction(player).getInventory());
            return;
        }

        if (NBTUtil.hasItemNBT(item, "kit_id")) {
            String kitId = NBTUtil.getNBTvalue(item, "kit_id");
            if (kitId == null) return;
            Kit kit = Main.getInstance().getKitManager().getKit(kitId);
            if (kit == null) return;

            if (event.isLeftClick()) {
                player.openInventory(new KitPreviewInventory(player, kit).getInventory());
            } else if (event.isRightClick()) {
                Main.getInstance().getBuyManager().buyKit(player, kit);
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}