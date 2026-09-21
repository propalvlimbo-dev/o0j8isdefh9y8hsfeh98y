package ru.rooyzee.elytrixtalisman.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.service.BossBarService;

public class PlayerJoinListener implements Listener {

    private final TalismanManager talismanManager;
    private final BossBarService bossBarService;

    public PlayerJoinListener(TalismanManager talismanManager, BossBarService bossBarService) {
        this.talismanManager = talismanManager;
        this.bossBarService = bossBarService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!talismanManager.isRunning()) return;
        bossBarService.addPlayer(event.getPlayer());
    }
}