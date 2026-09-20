package ru.rooyzee.elytrixclans.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.api.ClanManager;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;

public class PlayerDeathListener implements Listener {

    // Ключ — UUID, а не ник: ник игрок может сменить, и «кулдаун» фарма перестанет работать,
    // а карта начнёт расти за счёт старых имён.
    private final Map<UUID, UUID> killersMap = new ConcurrentHashMap<>();

    @EventHandler
    public void onKillPlayer(PlayerDeathEvent event) {
        Main main = Main.getInstance();
        if (main == null) return;
        ClanManager clanManager = main.getClanManager();
        if (clanManager == null) return;

        int pointsForKill = main.getConfig().getInt("pointsForKill", 1);
        int expForKill = main.getConfig().getInt("expForKill", 1);
        int timeForKillReload = Math.max(1, main.getConfig().getInt("timeForKillReload", 1200));

        Player player = event.getEntity();
        Clan targetClan = clanManager.getPlayerClan(player);
        if (targetClan != null) {
            processTarget(player, targetClan, clanManager, pointsForKill, expForKill);
        }

        Player killer = player.getKiller();
        if (killer == null) return;
        Clan killerClan = clanManager.getPlayerClan(killer);
        if (killerClan == null || killerClan == targetClan) return;

        UUID killerId = killer.getUniqueId();
        UUID victimId = player.getUniqueId();

        if (victimId.equals(killersMap.get(killerId))) return;

        killersMap.put(killerId, victimId);
        new BukkitRunnable() {
            @Override
            public void run() {
                // Снимаем отметку только если с момента фрага её не перезаписали другим убийством.
                killersMap.remove(killerId, victimId);
            }
        }.runTaskLater(main, timeForKillReload * 20L);

        processKiller(killer, killerClan, clanManager, pointsForKill, expForKill);
    }

    private void processKiller(Player killer, Clan clan, ClanManager clanManager, int pointsForKill, int expForKill) {
        ClanMember member = clanManager.getMember(clan, killer.getName());
        if (member == null) return;
        Level prevLevel = LevelUtil.getClanLevel(clan.getExp());
        clan.setExp(clan.getExp() + expForKill);
        clan.setPoints(clan.getPoints() + pointsForKill);
        member.setPoints(member.getPoints() + pointsForKill);
        member.setLevel(member.getLevel() + expForKill);
        member.setKills(member.getKills() + 1);
        updateKDA(member);
        Level newLevel = LevelUtil.getClanLevel(clan.getExp());
        if (prevLevel != null && newLevel != null && prevLevel.getLevel() < newLevel.getLevel()) {
            notifyMembers(clan, newLevel, "messages.lvlUp");
        }
    }

    private void processTarget(Player player, Clan clan, ClanManager clanManager, int pointsForKill, int expForKill) {
        ClanMember member = clanManager.getMember(clan, player.getName());
        if (member == null) return;
        Level prevLevel = LevelUtil.getClanLevel(clan.getExp());
        double newExp = Math.max(0, clan.getExp() - expForKill);
        double newPoints = Math.max(0, clan.getPoints() - pointsForKill);
        clan.setExp(newExp);
        clan.setPoints(newPoints);
        member.setPoints(Math.max(0, member.getPoints() - pointsForKill));
        member.setLevel(Math.max(0, member.getLevel() - expForKill));
        member.setDeaths(member.getDeaths() + 1);
        updateKDA(member);
        Level newLevel = LevelUtil.getClanLevel(clan.getExp());
        if (prevLevel != null && newLevel != null && prevLevel.getLevel() > newLevel.getLevel()) {
            notifyMembers(clan, newLevel, "messages.lvlDown");
        }
    }

    private void updateKDA(ClanMember member) {
        if (member.getDeaths() > 0) {
            member.setKDA(Math.round((double) member.getKills() / member.getDeaths() * 100.0) / 100.0);
        } else {
            member.setKDA(member.getKills());
        }
    }

    private void notifyMembers(Clan clan, Level level, String messageKey) {
        for (ClanMember member : clan.getMemberList()) {
            if (member.getPlayer() != null && member.getPlayer().isOnline()) {
                ConfigUtil.sendMessage(member.getPlayer(), messageKey,
                        ConfigUtil.setHolder(new String[]{"%lvl%"}, new String[]{String.valueOf(level.getLevel())}));
            }
        }
    }
}