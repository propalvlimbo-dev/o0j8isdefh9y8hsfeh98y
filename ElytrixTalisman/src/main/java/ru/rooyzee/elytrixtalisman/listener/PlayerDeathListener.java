package ru.rooyzee.elytrixtalisman.listener;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.service.ClanService;
import ru.rooyzee.elytrixtalisman.service.MessageService;

public class PlayerDeathListener implements Listener {

    private final TalismanManager talismanManager;
    private final ClanService clanService;
    private final MessageService messageService;

    public PlayerDeathListener(TalismanManager talismanManager, ClanService clanService,
                               MessageService messageService) {
        this.talismanManager = talismanManager;
        this.clanService = clanService;
        this.messageService = messageService;
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

        Player killer = victim.getKiller();
        Clan victimClan = clanService.getPlayerClan(victim);
        Clan killerClan = killer == null ? null : clanService.getPlayerClan(killer);

        // Фраг засчитываем только если убийца сам был у точки: выстрел издалека
        // не должен приносить очки захвата.
        boolean killerCounts = killer != null
                && victimClan != null
                && killerClan != null
                && !victimClan.getName().equalsIgnoreCase(killerClan.getName())
                && killer.getWorld().equals(talisman.getWorld())
                && killer.getLocation().distance(talisman) <= radius;

        // Порядок важен: очки переносим ДО обработки смерти, иначе они успеют сгореть.
        int stolen = 0;
        if (killerCounts) {
            stolen = talismanManager.addKill(killerClan.getName(), victimClan.getName(),
                    killer.getUniqueId(), victim.getUniqueId());
        }

        if (victimOnPoint) {
            // Сжигаем только то, что никто не забрал: смерть от мобов, падения, лавы
            // или от игрока, который сам в захвате не участвовал.
            talismanManager.handleDeathOnEvent(victim.getUniqueId(), !killerCounts);
        }

        if (stolen > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%killer%", killer.getName());
            ph.put("%victim%", victim.getName());
            ph.put("%points%", String.valueOf(stolen));
            messageService.send(killer, "score-stolen", ph);
            messageService.send(victim, "score-lost", ph);
        }
    }
}
