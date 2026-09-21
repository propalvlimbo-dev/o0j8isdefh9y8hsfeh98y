package ru.rooyzee.elytrixtalisman.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.rooyzee.elytrixclans.api.TalismanRewards;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.model.ClanCaptureData;
import ru.rooyzee.elytrixtalisman.model.TalismanSession;

/**
 * Раздача наград в конце ивента.
 *
 * Сами размеры наград здесь больше не хранятся: их знает ElytrixClans (config.yml, секция
 * talisman.places). Так сделано, чтобы опыт и монеты не задавались в двух местах сразу —
 * раньше rewards.yml дублировал то, что кланы всё равно начисляют по-своему, а поле points
 * вообще ссылалось на вырезанную из кланов валюту.
 *
 * Задача этого сервиса — определить, какой клан какое место занял и кто именно из этого
 * клана стоял на точке, и передать это кланам.
 */
public class RewardService {

    /** Сколько мест награждается. Совпадает с числом секций talisman.places у кланов. */
    private final int places;
    private final PlayerParticipationTracker tracker;
    private final MessageService messageService;

    public RewardService(ConfigManager configManager, PlayerParticipationTracker tracker,
                         MessageService messageService) {
        this.tracker = tracker;
        this.messageService = messageService;
        this.places = Math.max(1, configManager.getConfig().getInt("reward-places", 3));
    }

    public void giveRewards(TalismanSession session) {
        Map<String, ClanCaptureData> top = session.getTopClans(places);
        int place = 1;

        for (Map.Entry<String, ClanCaptureData> entry : top.entrySet()) {
            String clanName = entry.getKey();
            ClanCaptureData data = entry.getValue();

            // Поимённый список тех, кто реально стоял на точке за этот клан.
            List<String> holders = tracker.getHolders(clanName);

            TalismanRewards.Result result = clanReward(clanName, holders, place);
            if (result == null || !result.isSuccess()) {
                place++;
                continue;
            }

            Map<String, String> ph = new HashMap<>();
            ph.put("%place%", String.valueOf(place));
            ph.put("%clan%", clanName);
            ph.put("%capture_points%", String.valueOf(data.getCapturePoints()));
            ph.put("%kills%", String.valueOf(data.getKills()));
            ph.put("%deaths%", String.valueOf(data.getDeaths()));
            ph.put("%exp%", String.valueOf((long) result.getClanExp()));
            ph.put("%money%", String.valueOf((long) result.getMoneyEach()));
            ph.put("%holders%", String.valueOf(result.getRewarded().size()));
            messageService.broadcast("reward-broadcast", ph);

            place++;
        }
    }

    private TalismanRewards.Result clanReward(String clanName, List<String> holders, int place) {
        try {
            return TalismanRewards.rewardPlace(clanName, holders, place);
        } catch (Throwable t) {
            // Кланы могли не успеть прогрузиться или быть выключены — ивент из-за этого
            // падать не должен, остальные места всё равно наградим.
            org.bukkit.Bukkit.getLogger().warning(
                    "[ElytrixTalisman] Не удалось выдать награду клану " + clanName + ": " + t.getMessage());
            return null;
        }
    }
}
