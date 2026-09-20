package ru.rooyzee.elytrixclans.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.api.ClanManager;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.listener.kill.KillCooldownStorage;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;

/**
 * Опыт клана за убийство игрока.
 *
 * Изменения по сравнению со старой версией:
 *  - опыт даётся только убийце (+exp-for-kill, по умолчанию 5) и молча, без сообщения в чат;
 *  - за убийство своего же соклановца опыт не начисляется вовсе;
 *  - поинтов больше нет вообще;
 *  - кулдаун повторного убийства той же жертвы (по умолчанию 12 часов) переживает рестарт.
 */
public class PlayerDeathListener implements Listener {

    @EventHandler
    public void onKillPlayer(PlayerDeathEvent event) {
        Main main = Main.getInstance();
        if (main == null) return;
        ClanManager clanManager = main.getClanManager();
        if (clanManager == null) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Статистика смертей ведётся независимо от опыта.
        Clan victimClan = clanManager.getPlayerClan(victim);
        if (victimClan != null) {
            ClanMember victimMember = clanManager.getMember(victimClan, victim.getName());
            if (victimMember != null) {
                victimMember.setDeaths(victimMember.getDeaths() + 1);
                updateKDA(victimMember);
            }
        }

        if (killer == null || killer.equals(victim)) return;
        Clan killerClan = clanManager.getPlayerClan(killer);
        if (killerClan == null) return;

        ClanMember killerMember = clanManager.getMember(killerClan, killer.getName());
        if (killerMember != null) {
            killerMember.setKills(killerMember.getKills() + 1);
            updateKDA(killerMember);
        }

        // Убийство своего же соклановца опыта не приносит.
        if (killerClan == victimClan) return;

        double expForKill = main.getConfig().getDouble("exp-for-kill", 5.0);
        if (expForKill <= 0) return;

        long cooldownMillis = Math.max(0L, main.getConfig().getLong("kill-exp-cooldown-hours", 12L)) * 3600_000L;
        KillCooldownStorage cooldowns = main.getKillCooldownStorage();
        if (cooldowns != null
                && !cooldowns.tryRegisterKill(killer.getUniqueId(), victim.getUniqueId(), cooldownMillis)) {
            long left = cooldowns.remaining(killer.getUniqueId(), victim.getUniqueId());
            ConfigUtil.sendMessage(killer, "messages.killCooldown", ConfigUtil.setHolder(
                    new String[]{"%player%", "%time%"},
                    new String[]{victim.getName(), formatTime(left)}));
            return;
        }

        // Опыт начисляем молча: сообщение «Клан получил +5» в чате не нужно.
        clanManager.addClanExp(killerClan, expForKill, killer.getName());
    }

    private void updateKDA(ClanMember member) {
        if (member.getDeaths() > 0) {
            member.setKDA(Math.round((double) member.getKills() / member.getDeaths() * 100.0) / 100.0);
        } else {
            member.setKDA(member.getKills());
        }
    }

    private static String formatTime(long millis) {
        long totalMinutes = Math.max(0L, millis) / 60_000L;
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (hours > 0) return hours + "ч " + minutes + "м";
        if (minutes > 0) return minutes + "м";
        return "меньше минуты";
    }
}
