package ru.rooyzee.elytrixclans.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;
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
        if (!(holder instanceof ShopEditInventory)) return;
        ShopEditInventory editor = (ShopEditInventory) holder;
        if (editor.isSaved()) return;
        editor.markSaved();

        boolean ok = ShopEditStorage.save(editor.snapshot());
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
}
