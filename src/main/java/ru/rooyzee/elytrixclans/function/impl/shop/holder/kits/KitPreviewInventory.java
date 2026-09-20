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
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.function.impl.shop.util.BuyManager;
import ru.rooyzee.elytrixclans.function.impl.shop.util.PurchaseCooldownStorage;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;
import ru.rooyzee.elytrixclans.utils.MenuUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

/** Предпросмотр набора: состав, цена в монетах и кнопка покупки. */
public class KitPreviewInventory implements InventoryHolder {

    private static final int[] PREVIEW_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final Inventory inventory;
    private final int shopPage;

    public KitPreviewInventory(Player player, Kit kit, int shopPage) {
        this.shopPage = shopPage;
        inventory = Bukkit.createInventory(this, 54,
                HexUtil.translateHexColorCodes("&#F8BEFB&lНабор: ") + kit.getDisplayName());

        MenuUtil.applyLayout(inventory);
        // Слоты 10, 16, 37 и 43 по требованию отданы под товар — стекла там нет.
        MenuUtil.clearShopFreedSlots(inventory);

        int i = 0;
        for (ItemStack item : kit.getItems()) {
            if (i >= PREVIEW_SLOTS.length) break;
            inventory.setItem(PREVIEW_SLOTS[i], item.clone());
            i++;
        }
        if (!kit.getCommands().isEmpty() && i < PREVIEW_SLOTS.length) {
            inventory.setItem(PREVIEW_SLOTS[i], commandsInfo(kit));
        }

        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        inventory.setItem(4, MenuUtil.createInfoItem(clan,
                Main.getInstance().getBuyManager().getBalance(player)));
        inventory.setItem(49, buyButton(player, kit, clan));
        inventory.setItem(45, MenuUtil.createBackButton());
    }

    private ItemStack commandsInfo(Kit kit) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(HexUtil.translateHexColorCodes("&#F8BEFB&lДополнительно"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fВ набор входят особые предметы:"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &f" + kit.getCommands().size() + " шт."));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buyButton(Player player, Kit kit, Clan clan) {
        Level level = clan != null ? LevelUtil.getClanLevel(clan.getExp()) : null;
        int clanLevel = level != null ? level.getLevel() : 0;
        boolean locked = clanLevel < kit.getRequiredLevel();
        long cooldown = locked ? 0L
                : Main.getInstance().getBuyManager().remainingCooldown(player, BuyManager.kitKey(kit));
        boolean available = !locked && cooldown <= 0;

        ItemStack buyBtn = new ItemStack(available ? Material.EMERALD : Material.BARRIER);
        ItemMeta buyMeta = buyBtn.getItemMeta();
        if (buyMeta != null) {
            buyMeta.setDisplayName(HexUtil.translateHexColorCodes(available
                    ? "&7« &aКупить набор &7»"
                    : "&7« &cНедоступно &7»"));
            List<String> lore = new ArrayList<>();
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fНабор: ") + kit.getDisplayName());
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fСтоимость: &#F8BEFB"
                    + MenuUtil.money(kit.getPrice()) + " монет"));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ &fТребуется уровень: &#F8BEFB"
                    + kit.getRequiredLevel()));
            lore.add(HexUtil.translateHexColorCodes("&#F8BEFB&l┃ "));
            if (locked) {
                lore.add(HexUtil.translateHexColorCodes("&c● Откроется на &#F8BEFB"
                        + kit.getRequiredLevel() + " &cуровне клана"));
            } else if (cooldown > 0) {
                lore.add(HexUtil.translateHexColorCodes("&c● Перезарядка: &#F8BEFB"
                        + PurchaseCooldownStorage.format(cooldown)));
            } else {
                lore.add(HexUtil.translateHexColorCodes("&7● &fНажмите для покупки"));
            }
            buyMeta.setLore(lore);
            buyBtn.setItemMeta(buyMeta);
        }
        if (available) {
            // NBT вешаем только на доступную кнопку: недоступную клик просто игнорирует.
            NBTUtil.addItemNBT(buyBtn, "kit_buy", kit.getId());
        }
        return buyBtn;
    }

    public void onInventoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (NBTUtil.hasItemNBT(item, "arrowItem")) {
            player.openInventory(new ShopFunction(player, shopPage).getInventory());
            return;
        }

        if (NBTUtil.hasItemNBT(item, "kit_buy")) {
            Kit kit = Main.getInstance().getKitManager().getKit(NBTUtil.getNBTvalue(item, "kit_buy"));
            if (kit == null) return;
            Main.getInstance().getBuyManager().buyKit(player, kit);
            player.openInventory(new KitPreviewInventory(player, kit, shopPage).getInventory());
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
