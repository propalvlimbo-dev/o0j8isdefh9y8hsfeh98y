package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.entity.Player;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.api.ClanManager;
import ru.rooyzee.elytrixclans.clans.Clan;

public class ClanService {

    private ClanManager getClanManager() {
        return ru.rooyzee.elytrixclans.Main.getInstance().getClanManager();
    }

    public Clan getPlayerClan(Player player) {
        return getClanManager().getPlayerClan(player);
    }

    public Clan getPlayerClan(String name) {
        return getClanManager().getPlayerClan(name);
    }

    public Clan getClanByName(String name) {
        return getClanManager().getClanByName(name);
    }

    public void addExp(Clan clan, double amount) {
        clan.setExp(clan.getExp() + amount);
    }

    public void addPoints(Clan clan, double amount) {
        clan.setPoints(clan.getPoints() + amount);
    }
}