package ru.rooyzee.elytrixclans.function.impl.invite;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixclans.Main;

public class InviteManager {

    private static final Map<String, Invite> invites = new ConcurrentHashMap<>();

    public static boolean hasInvite(String playerName) {
        return invites.containsKey(playerName);
    }

    public static Invite getInvite(String playerName) {
        return invites.get(playerName);
    }

    public static boolean hasInviteInviter(String inviter) {
        for (Invite invite : invites.values()) {
            if (invite.getInviter().equalsIgnoreCase(inviter)) {
                return true;
            }
        }
        return false;
    }

    public static void addInvite(String playerName, String inviter, String clanName) {
        Invite invite = new Invite(inviter, clanName);
        invites.put(playerName, invite);
        int seconds = Math.max(1, Main.getInstance().getConfig().getInt("inviteExit", 15));
        new BukkitRunnable() {
            @Override
            public void run() {
                // Удаляем только СВОЁ приглашение: иначе таймер первого приглашения
                // снимал бы второе, отправленное следом (игрок терял возможность /clan accept).
                invites.remove(playerName, invite);
            }
        }.runTaskLater(Main.getInstance(), seconds * 20L);
    }

    public static void removeInvites(String playerName) {
        invites.remove(playerName);
    }

    public static void removeInvitesByInviter(String inviter) {
        Iterator<Map.Entry<String, Invite>> it = invites.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Invite> entry = it.next();
            if (entry.getValue().getInviter().equalsIgnoreCase(inviter)) {
                it.remove();
            }
        }
    }
}