package ru.rooyzee.elytrixclans.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.KitEditInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.ShopEditInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.ShopEditStorage;
import ru.rooyzee.elytrixclans.utils.HexUtil;

/**
 * Сохранение админ-редактора магазина при закрытии окна.
 *
 * Пишем ровно один раз: у InventoryCloseEvent нет гарантии единственного вызова
 * (переоткрытие меню, кик, выключение сервера), а перезаписывать shop_item.yml лишний раз
 * незачем.
 */
public class ShopEditListener implements Listener {

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof KitEditInventory) {
            saveKit(event, (KitEditInventory) holder);
            return;
        }

        if (!(holder instanceof ShopEditInventory)) return;
        ShopEditInventory editor = (ShopEditInventory) holder;
        if (editor.isSaved()) return;
        editor.markSaved();

        boolean ok = ShopEditStorage.save(editor.snapshot(), editor.getSlotIds(),
                editor.getPageIds(), editor.getOriginalItems());
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (ok) {
            player.sendMessage(HexUtil.translateHexColorCodes(
                    "&f☁ &7» &aАссортимент сохранён. Цены задаются в &fshop/prices.yml"));
        } else {
            player.sendMessage(HexUtil.translateHexColorCodes(
                    "&f☁ &7» &cНе удалось сохранить магазин, смотрите консоль сервера"));
        }
    }

    private void saveKit(InventoryCloseEvent event, KitEditInventory editor) {
        if (editor.isSaved()) return;
        editor.markSaved();

        boolean ok = ShopEditStorage.saveKit(editor.getKitId(), editor.snapshot());
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        if (ok) {
            player.sendMessage(HexUtil.translateHexColorCodes(
                    "&f☁ &7» &aНабор сохранён"));
        } else {
            player.sendMessage(HexUtil.translateHexColorCodes(
                    "&f☁ &7» &cНе удалось сохранить набор, смотрите консоль сервера"));
        }

        // Возвращаем администратора в витрину редактора: набор он открывал оттуда.
        final int back = editor.getBackPage();
        if (back < 0) return;
        Main main = Main.getInstance();
        if (main == null) return;
        Bukkit.getScheduler().runTask(main, () -> {
            if (player.isOnline()) {
                player.openInventory(new ShopEditInventory(player, back).getInventory());
            }
        });
    }
}
