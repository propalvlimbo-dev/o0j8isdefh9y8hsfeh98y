package ru.rooyzee.elytrixclans.function.impl.shop;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitPreviewInventory;

/**
 * Раз в секунду обновляет таймеры перезарядки в уже открытых меню магазина.
 *
 * Без этого время в лоре «замерзало»: игрок видел значение на момент открытия меню,
 * и чтобы понять, доступна ли покупка снова, приходилось закрывать и открывать магазин.
 *
 * Стоимость минимальна: обходятся только игроки, у которых прямо сейчас открыт магазин
 * или предпросмотр набора, и перерисовываются только слоты с активной перезарядкой.
 */
public final class ShopTicker {

    private static int taskId = -1;

    private ShopTicker() {
    }

    public static void start(JavaPlugin plugin) {
        stop();
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, ShopTicker::tick, 20L, 20L).getTaskId();
    }

    public static void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private static void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) continue;
            try {
                InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
                if (holder instanceof ShopFunction) {
                    ((ShopFunction) holder).tickCooldowns();
                } else if (holder instanceof KitPreviewInventory) {
                    ((KitPreviewInventory) holder).tickCooldown();
                }
            } catch (Throwable ignored) {
                // Одно проблемное меню не должно ломать обновление у остальных игроков.
            }
        }
    }
}
