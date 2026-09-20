package ru.rooyzee.elytrixclans.function.impl.sethome;

import org.bukkit.entity.Player;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;

public class SetHomeFunction {

    public static void setClanHome(Player player) {
        Home home = new Home(
                player.getWorld().getName(),
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ(),
                player.getLocation().getYaw(),
                player.getLocation().getPitch()
        );
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan != null) {
            clan.setHome(home);
        }
    }

    public static void deleteClanHome(Player player) {
        Clan clan = Main.getInstance().getClanManager().getPlayerClan(player);
        if (clan != null) {
            clan.setHome(null);
        }
    }
}