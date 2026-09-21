package ru.rooyzee.elytrixtalisman.listener;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.service.ClanService;

public class PlayerDeathListener implements Listener {

    private final TalismanManager talismanManager;
    private final ClanService clanService;

    public PlayerDeathListener(TalismanManager talismanManager, ClanService clanService) {
        this.talismanManager = talismanManager;
        this.clanService = clanService;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        if (!talismanManager.isRunning()) return;
        if (talismanManager.getSession() == null) return;

        Player victim = event.getEntity();
        Location talisman = talismanManager.getSession().getTalismanBlockLocation();
        if (talisman == null) return;

        if (victim.getWorld().equals(talisman.getWorld())
                && victim.getLocation().distance(talisman) <= talismanManager.getSession().getCaptureRadius()) {
            talismanManager.handleDeathOnEvent(victim.getUniqueId());
        }

        Player killer = victim.getKiller();
        if (killer == null) return;

        Clan victimClan = clanService.getPlayerClan(victim);
        Clan killerClan = clanService.getPlayerClan(killer);
        if (victimClan == null || killerClan == null) return;
        if (victimClan.getName().equalsIgnoreCase(killerClan.getName())) return;

        if (!killer.getWorld().equals(talisman.getWorld())) return;
        if (killer.getLocation().distance(talisman) > talismanManager.getSession().getCaptureRadius()) return;

        talismanManager.addKill(killerClan.getName(), victimClan.getName());
    }
}