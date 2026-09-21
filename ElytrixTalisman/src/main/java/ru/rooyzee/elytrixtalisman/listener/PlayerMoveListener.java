package ru.rooyzee.elytrixtalisman.listener;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.model.TalismanSession;
import ru.rooyzee.elytrixtalisman.model.TalismanState;
import ru.rooyzee.elytrixtalisman.service.EssentialsService;

public class PlayerMoveListener implements Listener {

    private final TalismanManager talismanManager;
    private final EssentialsService essentialsService;

    public PlayerMoveListener(TalismanManager talismanManager, EssentialsService essentialsService) {
        this.talismanManager = talismanManager;
        this.essentialsService = essentialsService;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!talismanManager.isRunning()) return;
        TalismanSession session = talismanManager.getSession();
        if (session == null || session.getState() != TalismanState.RUNNING) return;

        Location talisman = session.getTalismanBlockLocation();
        if (talisman == null) return;
        if (e.getPlayer().getWorld() != talisman.getWorld()) return;

        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        if (e.getPlayer().getLocation().distance(talisman) <= session.getCaptureRadius()) {
            essentialsService.strip(e.getPlayer());
        }
    }
}