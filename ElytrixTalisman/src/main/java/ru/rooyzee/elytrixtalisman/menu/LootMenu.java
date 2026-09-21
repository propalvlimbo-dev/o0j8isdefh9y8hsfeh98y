package ru.rooyzee.elytrixtalisman.menu;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.rooyzee.elytrixtalisman.Main;
import ru.rooyzee.elytrixtalisman.model.LootItem;
import ru.rooyzee.elytrixtalisman.service.LootService;
import ru.rooyzee.elytrixtalisman.service.MessageService;
import ru.rooyzee.elytrixtalisman.util.ColorUtil;
import ru.rooyzee.elytrixtalisman.util.PlaceholderUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LootMenu implements Listener {

    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_SLOT = 45;
    private static final int INFO_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int[] FILLER_SLOTS = {46, 47, 48, 50, 51, 52};

    private final Main plugin;
    private final LootService lootService;
    private final MessageService messageService;
    private final Map<UUID, LootHolder> openHolders = new HashMap<>();
    private final Map<UUID, Boolean> switchingPage = new HashMap<>();

    public LootMenu(Main plugin, LootService lootService, MessageService messageService) {
        this.plugin = plugin;
        this.lootService = lootService;
        this.messageService = messageService;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player, int page) {
        List<LootItem> items = lootService.getItems();
        int filledPages = Math.max(1, (int) Math.ceil(items.size() / (double) ITEMS_PER_PAGE));
        int accessiblePages = filledPages + 1;

        if (page < 0) page = 0;
        if (page >= accessiblePages) page = accessiblePages - 1;

        String title = ColorUtil.colorize(messageService.getRaw("loot.menu-title") + " &8[" + (page + 1) + "]");
        LootHolder holder = new LootHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, items.get(i).getItemStack());
        }

        ItemStack filler = button(Material.PINK_STAINED_GLASS_PANE, " ");
        for (int s : FILLER_SLOTS) inv.setItem(s, filler);

        if (page > 0) {
            inv.setItem(PREV_SLOT, button(Material.BLACK_DYE, messageService.getRaw("loot.previous-page")));
        } else {
            inv.setItem(PREV_SLOT, filler);
        }
        inv.setItem(NEXT_SLOT, button(Material.BLACK_DYE, messageService.getRaw("loot.next-page")));

        Map<String, String> ph = new HashMap<>();
        ph.put("%current%", String.valueOf(page + 1));
        ph.put("%total%", String.valueOf(filledPages));
        inv.setItem(INFO_SLOT, button(Material.PAPER, PlaceholderUtil.replace(messageService.getRaw("loot.page-info"), ph)));

        openHolders.put(player.getUniqueId(), holder);
        player.openInventory(inv);
    }

    private ItemStack button(Material material, String name) {
        ItemStack is = new ItemStack(material);
        ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            is.setItemMeta(meta);
        }
        return is;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (!(event.getInventory().getHolder() instanceof LootHolder)) return;
        Player player = (Player) event.getWhoClicked();
        LootHolder holder = (LootHolder) event.getInventory().getHolder();

        if (event.getClickedInventory() == null) return;
        if (!event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        int slot = event.getRawSlot();

        if (slot == PREV_SLOT) {
            event.setCancelled(true);
            if (holder.getPage() <= 0) return;
            savePage(holder);
            switchingPage.put(player.getUniqueId(), true);
            int targetPage = holder.getPage() - 1;
            Bukkit.getScheduler().runTask(plugin, () -> open(player, targetPage));
            return;
        }
        if (slot == NEXT_SLOT) {
            event.setCancelled(true);
            savePage(holder);
            switchingPage.put(player.getUniqueId(), true);
            int targetPage = holder.getPage() + 1;
            Bukkit.getScheduler().runTask(plugin, () -> open(player, targetPage));
            return;
        }
        if (slot == INFO_SLOT) {
            event.setCancelled(true);
            return;
        }
        for (int s : FILLER_SLOTS) {
            if (slot == s) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LootHolder)) return;
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        Boolean switching = switchingPage.remove(player.getUniqueId());
        if (switching != null && switching) return;

        LootHolder holder = openHolders.remove(player.getUniqueId());
        if (holder != null) {
            savePage(holder);
        }
    }

    private void savePage(LootHolder holder) {
        Inventory inv = holder.getInventory();
        int page = holder.getPage();
        List<ItemStack> allItems = new ArrayList<>();
        for (LootItem li : lootService.getItems()) allItems.add(li.getItemStack());

        int start = page * ITEMS_PER_PAGE;

        while (allItems.size() < start + ITEMS_PER_PAGE) {
            allItems.add(null);
        }

        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            ItemStack slotItem = inv.getItem(i);
            int globalIndex = start + i;
            if (slotItem == null || slotItem.getType() == Material.AIR) {
                allItems.set(globalIndex, null);
            } else {
                allItems.set(globalIndex, slotItem);
            }
        }

        allItems.removeIf(is -> is == null || is.getType() == Material.AIR);
        lootService.save(allItems);
    }

    private static class LootHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        public LootHolder(int page) {
            this.page = page;
        }

        public int getPage() {
            return page;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}