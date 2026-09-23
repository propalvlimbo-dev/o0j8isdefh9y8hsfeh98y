package ru.rooyzee.elytrixtalisman.listener;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixtalisman.manager.TalismanManager;
import ru.rooyzee.elytrixtalisman.service.ClanService;
import ru.rooyzee.elytrixtalisman.service.MessageService;

/**
 * Смерть во время ивента.
 *
 * Разбираются три случая, и в каждом игрок получает сообщение — молча очки не пропадают:
 *
 *   убил игрок другого клана, стоявший у точки → очки переходят ему и его клану;
 *   убил кто-то извне захвата (снайпер, чужой мир) → очки сгорают, забирать некому;
 *   смерть без убийцы (упал, лава, моб, /kill) → очки сгорают.
 */
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

    // MONITOR: к этому моменту остальные плагины уже отработали, и getKiller() устоялся.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!talismanManager.isRunning()) return;
        if (talismanManager.getSession() == null) return;

        Player victim = event.getEntity();
        Location talisman = talismanManager.getSession().getTalismanBlockLocation();
        if (talisman == null) return;

        int radius = talismanManager.getSession().getCaptureRadius();

        // Сравниваем миры до distance(): вызов у локаций из разных миров бросает
        // IllegalArgumentException, а исключение здесь оборвало бы всю обработку смерти.
        boolean victimOnPoint = victim.getWorld().equals(talisman.getWorld())
                && victim.getLocation().distance(talisman) <= radius;

        Player killer = victim.getKiller();
        Clan victimClan = clanService.getPlayerClan(victim);
        Clan killerClan = killer == null ? null : clanService.getPlayerClan(killer);

        // Фраг засчитываем, только если убийца сам был у точки: выстрел издалека
        // не должен приносить очки захвата. Свой своего тоже не грабит.
        boolean killerCounts = killer != null
                && !killer.getUniqueId().equals(victim.getUniqueId())
                && victimClan != null
                && killerClan != null
                && !victimClan.getName().equalsIgnoreCase(killerClan.getName())
                && killer.getWorld().equals(talisman.getWorld())
                && killer.getLocation().distance(talisman) <= radius;

        // Порядок важен: очки переносим ДО обработки смерти, иначе она их сожжёт.
        int stolen = 0;
        if (killerCounts) {
            stolen = talismanManager.addKill(killerClan.getName(), victimClan.getName(),
                    killer.getUniqueId(), victim.getUniqueId());
        }

        // Сжигаем всё, что никто не забрал. Проверяем именно stolen, а не killerCounts:
        // убийца мог быть засчитан, но жертве нечего было отдавать.
        int burned = 0;
        if (victimOnPoint || !killerCounts) {
            String clanName = victimClan == null ? null : victimClan.getName();
            burned = talismanManager.handleDeathOnEvent(victim.getUniqueId(), clanName, stolen == 0);
        }

        if (stolen > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%killer%", killer.getName());
            ph.put("%victim%", victim.getName());
            ph.put("%points%", String.valueOf(stolen));
            messageService.send(killer, "score-stolen", ph);
            messageService.send(victim, "score-lost", ph);
        } else if (burned > 0) {
            Map<String, String> ph = new HashMap<>();
            ph.put("%victim%", victim.getName());
            ph.put("%points%", String.valueOf(burned));
            messageService.send(victim, "score-burned", ph);
        }
    }
}
