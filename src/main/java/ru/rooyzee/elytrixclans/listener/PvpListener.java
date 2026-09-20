package ru.rooyzee.elytrixclans.listener;

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

        damager.sendMessage(ConfigUtil.getString("messages.noPvp"));
        event.setCancelled(true);
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