package ru.rooyzee.elytrixclans.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.utils.CloseInventoryUtil;
import ru.rooyzee.elytrixclans.utils.PlayerHeadCache;

public class ArmorUpdateListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        // Держим кэш голов тёплым: тогда меню кланов открываются без единого обращения к Mojang.
        PlayerHeadCache.remember(player);
        Main main = Main.getInstance();
        if (main == null || main.getGlowManager() == null) return;
        Bukkit.getScheduler().runTaskLater(main,
                () -> {
                    if (player.isOnline() && main.getGlowManager() != null) {
                        main.getGlowManager().refreshPlayer(player);
                    }
                }, 20L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        Main main = Main.getInstance();
        if (main == null || main.getGlowManager() == null) return;
        Bukkit.getScheduler().runTaskLater(main,
                () -> {
                    if (player.isOnline() && main.getGlowManager() != null) {
                        main.getGlowManager().refreshPlayer(player);
                    }
                }, 5L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // После смены мира id сущностей пересоздаются, и подкрашенный шлем у союзников «пропадает»,
        // пока игрок не перезайдёт. Обновляем так же, как при входе.
        final Player player = event.getPlayer();
        Main main = Main.getInstance();
        if (main == null || main.getGlowManager() == null) return;
        Clan clan = main.getClanManager() != null ? main.getClanManager().getPlayerClan(player) : null;
        if (clan == null || !clan.isGlow()) return;
        Bukkit.getScheduler().runTaskLater(main,
                () -> {
                    if (player.isOnline() && main.getGlowManager() != null) {
                        main.getGlowManager().refreshPlayer(player);
                    }
                }, 10L);
    }

    @EventHandler
    public void onInvClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        // Обновляем броню только после закрытия НАШИХ меню: раньше задача ставилась
        // на каждое закрытие любого инвентаря (сундук, верстак, печь) у игрока из клана с подсветкой.
        if (!CloseInventoryUtil.isMenuHolder(event.getInventory().getHolder())) return;
        Player player = (Player) event.getPlayer();
        Main main = Main.getInstance();
        if (main == null || main.getGlowManager() == null) return;
        Clan clan = main.getClanManager() != null ? main.getClanManager().getPlayerClan(player) : null;
        if (clan == null || !clan.isGlow()) return;
        Bukkit.getScheduler().runTaskLater(main,
                () -> {
                    if (player.isOnline() && main.getGlowManager() != null) {
                        main.getGlowManager().refreshPlayer(player);
                    }
                }, 1L);
    }
}