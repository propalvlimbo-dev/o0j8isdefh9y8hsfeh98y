package ru.rooyzee.elytrixclans.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.function.impl.confirm.DisbandConfirmManager;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;

public class DisbandChatListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();
        if (!DisbandConfirmManager.hasPending(name)) return;

        String expected = DisbandConfirmManager.getPending(name);
        // Подтверждение могло истечь между проверками (таймер снимает его в main-потоке).
        // Без этой проверки было NPE в асинхронном событии чата, а сообщение игрока — съеденным.
        if (expected == null) return;

        String message = event.getMessage().trim();

        event.setCancelled(true);
        DisbandConfirmManager.cancel(name);

        if (!message.equalsIgnoreCase(expected)) {
            Bukkit.getScheduler().runTask(Main.getInstance(),
                    () -> ConfigUtil.sendMessage(player, "messages.disbandCancelled", null));
            return;
        }

        Bukkit.getScheduler().runTask(Main.getInstance(), () -> {
            Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
            if (clan == null || !clan.getName().equalsIgnoreCase(expected)) {
                ConfigUtil.sendMessage(player, "messages.disbandCancelled", null);
                return;
            }
            Main.getInstance().getClanManager().deleteClan(clan);
            ConfigUtil.sendMessage(player, "messages.clanDisbanded", null);
        });
    }
}