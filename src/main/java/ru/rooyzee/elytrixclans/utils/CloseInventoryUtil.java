package ru.rooyzee.elytrixclans.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.function.impl.glow.GlowFunction;
import ru.rooyzee.elytrixclans.function.impl.info.InfoFunction;
import ru.rooyzee.elytrixclans.function.impl.info.MemberInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.ShopFunction;
import ru.rooyzee.elytrixclans.function.impl.shop.admin.ShopEditInventory;
import ru.rooyzee.elytrixclans.function.impl.shop.holder.kits.KitPreviewInventory;

public class CloseInventoryUtil {

    public static void closeAllMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player != null && isMenuHolder(player.getOpenInventory().getTopInventory().getHolder())) {
                player.closeInventory();
            }
        }
    }

    public static void closeClanMenus(Clan clan) {
        for (ClanMember member : clan.getMemberList()) {
            if (member != null && member.getPlayer() != null && member.getPlayer().isOnline()) {
                Player player = member.getPlayer();
                if (isMenuHolder(player.getOpenInventory().getTopInventory().getHolder())) {
                    player.closeInventory();
                }
            }
        }
    }

    public static void closePlayerMenus(Player player) {
        if (player != null && isMenuHolder(player.getOpenInventory().getTopInventory().getHolder())) {
            player.closeInventory();
        }
    }

    public static boolean isMenuHolder(InventoryHolder holder) {
        return holder instanceof InfoFunction
                || holder instanceof ShopFunction
                || holder instanceof GlowFunction
                || holder instanceof MemberInventory
                || holder instanceof KitPreviewInventory
                // Редактор закрываем тоже: на закрытии он сохраняет правки в файл.
                || holder instanceof ShopEditInventory;
    }
}