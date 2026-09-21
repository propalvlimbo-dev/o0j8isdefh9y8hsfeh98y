package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.configuration.ConfigurationSection;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.model.ClanCaptureData;
import ru.rooyzee.elytrixtalisman.model.RewardTier;
import ru.rooyzee.elytrixtalisman.model.TalismanSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewardService {

    private final List<RewardTier> tiers;
    private final ClanService clanService;
    private final MessageService messageService;

    public RewardService(ConfigManager configManager, ClanService clanService, MessageService messageService) {
        this.clanService = clanService;
        this.messageService = messageService;
        this.tiers = new ArrayList<>();

        ConfigurationSection section = configManager.getRewards().getConfigurationSection("rewards");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int place = Integer.parseInt(key);
                double exp = section.getDouble(key + ".exp", 0);
                double points = section.getDouble(key + ".points", 0);
                tiers.add(new RewardTier(place, exp, points));
            }
        }
        tiers.sort((a, b) -> Integer.compare(a.getPlace(), b.getPlace()));
    }

    public void giveRewards(TalismanSession session) {
        Map<String, ClanCaptureData> top = session.getTopClans(tiers.size());
        int place = 1;

        for (Map.Entry<String, ClanCaptureData> entry : top.entrySet()) {
            String clanName = entry.getKey();
            ClanCaptureData data = entry.getValue();
            Clan clan = clanService.getClanByName(clanName);
            if (clan == null) {
                place++;
                continue;
            }

            RewardTier tier = getTier(place);
            if (tier == null) {
                place++;
                continue;
            }

            clanService.addExp(clan, tier.getExp());
            clanService.addPoints(clan, tier.getPoints());

            Map<String, String> ph = new HashMap<>();
            ph.put("%place%", String.valueOf(place));
            ph.put("%clan%", clanName);
            ph.put("%capture_points%", String.valueOf(data.getCapturePoints()));
            ph.put("%kills%", String.valueOf(data.getKills()));
            ph.put("%deaths%", String.valueOf(data.getDeaths()));
            ph.put("%exp%", String.valueOf((int) tier.getExp()));
            ph.put("%points%", String.valueOf((int) tier.getPoints()));
            messageService.broadcast("reward-broadcast", ph);

            place++;
        }
    }

    private RewardTier getTier(int place) {
        return tiers.stream().filter(t -> t.getPlace() == place).findFirst().orElse(null);
    }
}