package ru.rooyzee.elytrixclans.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.EditableInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.EditMenuInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.KitEditInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.ShopEditInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.ShopEditStorage;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.Kit;
import ru.rooyzee.elytrixclans.function.impl.shop.kit.KitManager;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.NBTUtil;

/**
 * Поведение окон админ-редактора магазина: разрешает менять предметы только в клетках
 * позиции, защищает рамку меню, открывает редакторы из меню выбора и сохраняет
 * shop_item.yml при закрытии окна.
 */
public class ShopEditListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        boolean chooser = holder instanceof EditMenuInventory;
        boolean editor = holder instanceof ShopEditInventory || holder instanceof KitEditInventory;
        if (!chooser && !editor) return;

        if (!(event.getWhoClicked() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player who = (Player) event.getWhoClicked();
        if (!isOwner(holder, who)) {
            event.setCancelled(true);
            return;
        }
        if (!who.hasPermission("elytrixclans.admin")) {
            // Права сняли, пока окно открыто — закрываем без правок чужого окна.
            event.setCancelled(true);
            who.closeInventory();
            return;
        }

        Inventory top = event.getView().getTopInventory();
        ItemStack current = event.getCurrentItem();

        // Меню выбора: ничего не отдаём и ничего не принимаем, только клики по кнопкам.
        if (chooser) {
            event.setCancelled(true);
            if (event.getClickedInventory() == top) openSelected(who, current);
            return;
        }

        if (event.getClickedInventory() == top) {
            if (current != null && NBTUtil.hasItemNBT(current, "closeItem")) {
                event.setCancelled(true);
                who.closeInventory();
                return;
            }
            if (!ShopEditStorage.isEditableSlot(event.getRawSlot())) {
                event.setCancelled(true);
                return;
            }
            // В 1.16 «собрать всё подходящее в курсор» — это двойной клик (в 1.17+ появился
            // отдельный COLLECT_TO_CURSOR). Иначе им можно стянуть стекло рамки и разобрать меню.
            if (event.getClick() == ClickType.DOUBLE_CLICK) {
                event.setCancelled(true);
            }
            return;
        }

        // Клик по своему инвентарю: обычный перенос разрешаем, shift-перенос делаем сами,
        // чтобы Minecraft не подмешал предмет в стак рамки.
        if (event.isShiftClick() && editor) {
            event.setCancelled(true);
            if (current == null || current.getType() == Material.AIR) return;
            ItemStack rest = placeIntoEditor(top, current);
            event.setCurrentItem(rest);
            who.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof ShopEditInventory) && !(holder instanceof KitEditInventory)) return;
        if (!(event.getWhoClicked() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        Player who = (Player) event.getWhoClicked();
        if (!isOwner(holder, who)) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int topSize = top.getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize && !ShopEditStorage.isEditableSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        save((Player) event.getPlayer(), event.getInventory().getHolder());
    }

    private void openSelected(Player who, ItemStack clicked) {
        if (clicked == null) return;
        if (NBTUtil.hasItemNBT(clicked, "edit_category")) {
            String category = NBTUtil.getNBTvalue(clicked, "edit_category");
            if (category == null) return;
            who.openInventory(new ShopEditInventory(who, category, ShopEditStorage.loadYaml()).getInventory());
            return;
        }
        if (NBTUtil.hasItemNBT(clicked, "edit_kit")) {
            String kitId = NBTUtil.getNBTvalue(clicked, "edit_kit");
            if (kitId == null) return;
            KitManager manager = Main.getInstance() == null ? null : Main.getInstance().getKitManager();
            Kit kit = manager == null ? null : manager.getKit(kitId);
            if (kit == null) {
                send(who, "&cНабор не найден в конфиге");
                return;
            }
            who.openInventory(new KitEditInventory(who, kitId, kit).getInventory());
        }
    }

    /** Кладёт предмет в первую подходящую клетку редактора; возвращает то, что не влезло. */
    private ItemStack placeIntoEditor(Inventory top, ItemStack stack) {
        ItemStack remaining = stack.clone();
        int max = remaining.getMaxStackSize();
        if (max <= 0) max = remaining.getAmount();

        for (int slot : ShopEditStorage.EDIT_SLOTS) {
            if (remaining.getAmount() <= 0) break;
            ItemStack current = slot < top.getSize() ? top.getItem(slot) : null;
            if (current == null || current.getType() != remaining.getType()) continue;
            if (current.getMaxStackSize() > 0 && current.getAmount() >= current.getMaxStackSize()) continue;
            if (!sameDisplay(current, remaining)) continue;
            int free = current.getMaxStackSize() > 0 ? current.getMaxStackSize() - current.getAmount() : remaining.getAmount();
            int move = Math.min(free, remaining.getAmount());
            if (move <= 0) continue;
            current.setAmount(current.getAmount() + move);
            top.setItem(slot, current);
            remaining.setAmount(remaining.getAmount() - move);
        }

        for (int slot : ShopEditStorage.EDIT_SLOTS) {
            if (remaining.getAmount() <= 0) break;
            if (slot >= top.getSize()) continue;
            if (top.getItem(slot) != null) continue;
            int take = Math.min(max, remaining.getAmount());
            ItemStack moved = remaining.clone();
            moved.setAmount(take);
            top.setItem(slot, moved);
            remaining.setAmount(remaining.getAmount() - take);
        }

        if (remaining.getAmount() <= 0) return null;
        return remaining;
    }

    private boolean sameDisplay(ItemStack a, ItemStack b) {
        boolean aMeta = a.hasItemMeta();
        boolean bMeta = b.hasItemMeta();
        if (aMeta != bMeta) return false;
        if (!aMeta) return true;
        return a.getItemMeta() != null && a.getItemMeta().equals(b.getItemMeta());
    }

    private boolean isOwner(InventoryHolder holder, Player player) {
        if (!(holder instanceof EditableInventory)) return true;
        UUID owner = ((EditableInventory) holder).getOwner();
        return owner == null || owner.equals(player.getUniqueId());
    }

    /** Сохраняет правки при закрытии редактора. Повторный вызов (выход игрока) игнорируется. */
    public static void save(Player who, InventoryHolder holder) {
        if (!(holder instanceof EditableInventory)) return;
        EditableInventory editable = (EditableInventory) holder;
        if (editable.isSaved()) return;
        if (who == null) return;
        if (editable.getOwner() != null && !editable.getOwner().equals(who.getUniqueId())) return;
        editable.markSaved();

        Main main = Main.getInstance();
        if (main == null) return;

        YamlConfiguration config = ShopEditStorage.loadYaml();
        ShopEditStorage.Result result;
        String label;

        if (holder instanceof ShopEditInventory) {
            ShopEditInventory editor = (ShopEditInventory) holder;
            label = ShopEditStorage.label(editor.getCategory());
            Map<Integer, ItemStack> grid = editor.snapshot();
            result = ShopEditStorage.applyCategory(config, editor.getCategory(), grid);
        } else if (holder instanceof KitEditInventory) {
            KitEditInventory editor = (KitEditInventory) holder;
            label = "набор " + editor.getKitId();
            List<ItemStack> items = editor.snapshot();
            // Что не влезло в клетки редактора — не выбрасываем, а оставляем в наборе как было.
            KitManager manager = main.getKitManager();
            Kit kit = manager == null ? null : manager.getKit(editor.getKitId());
            if (kit != null && editor.getUnfits() > 0) {
                List<ItemStack> all = new ArrayList<>(kit.getItems());
                for (int i = ShopEditStorage.EDIT_SLOTS.length; i < all.size(); i++) {
                    ItemStack tail = all.get(i);
                    if (tail != null) items.add(tail.clone());
                }
            }
            result = ShopEditStorage.applyKit(config, editor.getKitId(), items);
        } else {
            return;
        }

        if (result.failed) {
            send(who, "&cПозиция пропала из конфига — изменения не сохранены");
            return;
        }
        if (!ShopEditStorage.saveYaml(config)) {
            send(who, "&cНе удалось записать shop/shop_item.yml — смотри консоль");
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("&aСохранено");
        if (result.added > 0) message.append("&7, &aдобавлено&f: ").append(result.added);
        if (result.updated > 0) message.append("&7, &aизменено&f: ").append(result.updated);
        if (result.removed > 0) message.append("&7, &cудалено&f: ").append(result.removed);
        message.append("&7, &fвсего&f: ").append(result.entries);
        message.append(" &8(").append(label).append("&8)");
        send(who, message.toString());
    }

    /** Перед /elytrixclan reload закрываем открытые редакторы, не сохраняя устаревший снимок. */
    public static void discardOpenEditors() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null) continue;
            Inventory top = player.getOpenInventory().getTopInventory();
            InventoryHolder holder = top.getHolder();
            if (holder instanceof EditableInventory) {
                ((EditableInventory) holder).markSaved();
                player.closeInventory();
            }
        }
    }

    private static void send(Player player, String message) {
        if (player == null) return;
        player.sendMessage(HexUtil.translateHexColorCodes("&f☁ &7» &f" + message));
    }
}
