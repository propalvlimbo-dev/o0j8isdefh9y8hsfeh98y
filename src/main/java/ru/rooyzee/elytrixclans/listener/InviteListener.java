package ru.rooyzee.elytrixclans.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.rooyzee.elytrixclans.function.impl.invite.InviteManager;

public class InviteListener implements Listener {

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        String name = event.getPlayer().getName();
        InviteManager.removeInvites(name);
        InviteManager.removeInvitesByInviter(name);
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        String name = event.getPlayer().getName();
        InviteManager.removeInvites(name);
        InviteManager.removeInvitesByInviter(name);
    }
}