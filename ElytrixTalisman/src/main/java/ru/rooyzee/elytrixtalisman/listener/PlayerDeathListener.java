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

        int radius = talismanManager.getSession().getCaptureRadius();

        boolean victimOnPoint = victim.getWorld().equals(talisman.getWorld())
                && victim.getLocation().distance(talisman) <= radius;

        if (victimOnPoint) {
            talismanManager.handleDeathOnEvent(victim.getUniqueId());
        }

        Player killer = victim.getKiller();
        if (killer == null) return;

        Clan victimClan = clanService.getPlayerClan(victim);
        Clan killerClan = clanService.getPlayerClan(killer);
        if (victimClan == null || killerClan == null) return;
        if (victimClan.getName().equalsIgnoreCase(killerClan.getName())) return;

        // Фраг засчитываем только если убийца сам был у точки: выстрел издалека
        // не должен приносить очки захвата.
        if (!killer.getWorld().equals(talisman.getWorld())) return;
        if (killer.getLocation().distance(talisman) > radius) return;

        talismanManager.addKill(killerClan.getName(), victimClan.getName());
    }
}
