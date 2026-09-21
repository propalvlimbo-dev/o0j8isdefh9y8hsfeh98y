package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.entity.Player;
import java.util.List;
import ru.rooyzee.elytrixclans.api.ClanManager;
import ru.rooyzee.elytrixclans.api.TalismanRewards;
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

    /**
     * Награды за занятое место отдаём на сторону кланов: там и опыт клана, и личный вклад
     * участников, и монеты Vault, и объявление — всё настраивается в config.yml кланов.
     *
     * Раньше здесь было clan.setExp(...) напрямую и clan.setPoints(...). Так делать нельзя:
     * прямая установка опыта проходит мимо проверки уровня (клан не получал сообщение о
     * повышении), а поинтов в кланах больше нет вообще — валюта теперь одна, монеты Vault.
     */
    public TalismanRewards.Result giveReward(String clanName, List<String> holders, int place) {
        return TalismanRewards.rewardPlace(clanName, holders, place);
    }
}