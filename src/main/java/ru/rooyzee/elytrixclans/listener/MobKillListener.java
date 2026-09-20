package ru.rooyzee.elytrixclans.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.api.ClanManager;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;

public class MobKillListener implements Listener {

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        Main main = Main.getInstance();
        if (main == null) return;
        ClanManager clanManager = main.getClanManager();
        if (clanManager == null) return;

        Clan clan = clanManager.getPlayerClan(killer);
        if (clan == null) return;
        // Участника ищем внутри его же клана: второй обхода всех кланов на каждое убийство моба
        // (а на фермах это сотни событий в минуту) не нужно.
        ClanMember member = clanManager.getMember(clan, killer.getName());
        if (member == null) return;

        double pointsForKill = round(main.getConfig().getDouble("points-for-kill-mob", 0));
        double expForKill = round(main.getConfig().getDouble("exp-for-kill-mob", 0));
        if (pointsForKill == 0 && expForKill == 0) return;
        clan.setPoints(clan.getPoints() + pointsForKill);
        clan.setExp(clan.getExp() + expForKill);
        member.setPoints(member.getPoints() + pointsForKill);
        member.setLevel(member.getLevel() + expForKill);
    }

    private static double round(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        return Math.round(value * 100.0) / 100.0;
    }
}