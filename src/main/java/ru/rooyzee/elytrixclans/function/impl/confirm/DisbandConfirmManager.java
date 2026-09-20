package ru.rooyzee.elytrixclans.function.impl.confirm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.scheduler.BukkitRunnable;
import ru.rooyzee.elytrixclans.Main;

public class DisbandConfirmManager {

    private static final Map<String, String> pending = new ConcurrentHashMap<>();

    public static void request(String playerName, String clanName) {
        pending.put(playerName, clanName);
        new BukkitRunnable() {
            @Override
            public void run() {
                // Снимаем подтверждение только если его не обновили новым запросом.
                pending.remove(playerName, clanName);
            }
        }.runTaskLater(Main.getInstance(), 30 * 20L);
    }

    public static boolean hasPending(String playerName) {
        return pending.containsKey(playerName);
    }

    public static String getPending(String playerName) {
        return pending.get(playerName);
    }

    public static void cancel(String playerName) {
        pending.remove(playerName);
    }
}