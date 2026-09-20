package ru.rooyzee.elytrixclans.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.api.ClanManager;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;

public class PvpListener implements Listener {

    /**
     * Антиспам подсказки «Вы не можете ударить соклановца».
     *
     * Удар мечом проходит несколько раз в секунду, и раньше каждый из них печатал строку
     * в чат. Теперь сообщение показывается не чаще раза в 5 секунд на игрока: сам удар
     * при этом по-прежнему отменяется всегда.
     */
    private static final long MESSAGE_COOLDOWN_MS = 5000L;
    private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
    /** Порог очистки: на большом онлайне карта не должна расти бесконечно. */
    private static final int MAX_TRACKED = 512;

    @EventHandler(priority = EventPriority.LOW)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player)) return;

        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return;
        ClanManager clanManager = main.getClanManager();

        Player player = (Player) event.getEntity();
        // Стрелы/трезубцы/зелья раньше не учитывались: по соклановцу можно было стрелять,
        // даже когда PvP в клане выключен.
        Player damager = resolveAttacker(event.getDamager());
        if (damager == null || damager.equals(player)) return;

        Clan damagerClan = clanManager.getPlayerClan(damager);
        if (damagerClan == null) return;
        Clan playerClan = clanManager.getPlayerClan(player);
        if (playerClan == null || playerClan != damagerClan) return;
        if (playerClan.isPvp()) return;

        event.setCancelled(true);
        if (canNotify(damager.getUniqueId())) {
            damager.sendMessage(ConfigUtil.getString("messages.noPvp"));
        }
    }

    /** true, если игроку можно снова показать подсказку про PvP. */
    private static boolean canNotify(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = LAST_MESSAGE.get(uuid);
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return false;
        if (LAST_MESSAGE.size() > MAX_TRACKED) {
            // Чистим протухшие записи оптом: отдельный таймер ради этого не нужен.
            LAST_MESSAGE.values().removeIf(stamp -> now - stamp > MESSAGE_COOLDOWN_MS);
        }
        LAST_MESSAGE.put(uuid, now);
        return true;
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            // getShooter() отдаёт ProjectileSource: стрелять может и диспенсер, и моб — их не трогаем.
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) return (Player) shooter;
        }
        return null;
    }
}