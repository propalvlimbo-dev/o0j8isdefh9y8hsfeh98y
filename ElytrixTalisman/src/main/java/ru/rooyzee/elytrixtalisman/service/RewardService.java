package ru.rooyzee.elytrixtalisman.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ru.rooyzee.elytrixclans.api.TalismanRewards;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.model.ClanCaptureData;
import ru.rooyzee.elytrixtalisman.model.TalismanSession;
import ru.rooyzee.elytrixtalisman.util.PlaceholderUtil;

/**
 * Раздача наград в конце ивента.
 *
 * Размеры наград здесь не хранятся: их знает ElytrixClans (config.yml, секция
 * talisman.places). Задача сервиса — определить, какой клан какое место занял и кто
 * именно из этого клана стоял на точке, и передать это кланам.
 *
 * Итог объявляется ОДНИМ сообщением на весь ивент. Раньше broadcast шёл внутри цикла по
 * местам, и чат получал три многострочных блока подряд — при трёх кланах это полтора
 * экрана текста. Теперь места собираются в короткий список и уходят разом.
 */
public class RewardService {

    /** Сколько мест награждается. Совпадает с числом секций talisman.places у кланов. */
    private final int places;
    private final ConfigManager configManager;
    private final PlayerParticipationTracker tracker;
    private final MessageService messageService;

    public RewardService(ConfigManager configManager, PlayerParticipationTracker tracker,
                         MessageService messageService) {
        this.configManager = configManager;
        this.tracker = tracker;
        this.messageService = messageService;
        this.places = Math.max(1, configManager.getConfig().getInt("reward-places", 3));
    }

    public void giveRewards(TalismanSession session) {
        Map<String, ClanCaptureData> top = session.getTopClans(places);
        List<String> summary = new ArrayList<>();
        int place = 1;

        for (Map.Entry<String, ClanCaptureData> entry : top.entrySet()) {
            String clanName = entry.getKey();
            ClanCaptureData data = entry.getValue();

            // Клан с нулём очков награду не получает: он попал в список, потому что
            // словил смерть на точке, но захватом это не является.
            if (data.getCapturePoints() <= 0) continue;

            List<String> holders = tracker.getHolders(clanName);
            TalismanRewards.Result result = clanReward(clanName, holders, place);
            if (result == null || !result.isSuccess()) continue;

            Map<String, String> ph = new HashMap<>();
            ph.put("%place%", String.valueOf(place));
            ph.put("%clan%", clanName);
            ph.put("%points%", String.valueOf(data.getCapturePoints()));
            ph.put("%kills%", String.valueOf(data.getKills()));
            ph.put("%deaths%", String.valueOf(data.getDeaths()));
            ph.put("%exp%", String.valueOf((long) result.getClanExp()));
            ph.put("%money%", String.valueOf((long) result.getMoneyEach()));
            ph.put("%holders%", String.valueOf(result.getRewarded().size()));

            String template = configManager.getMessages()
                    .getString("reward-line-" + place, "&f%place%. %clan% &8· &a+%exp%");
            summary.add(PlaceholderUtil.replace(template, ph));
            place++;
        }

        if (summary.isEmpty()) {
            // Ивент прошёл вхолостую: на точке никто не стоял либо все обнулились.
            // Сообщить всё равно надо, иначе талисман исчезает молча.
            messageService.broadcastLine("reward-nobody", new HashMap<>());
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("%top%", String.join("\n", summary));
        messageService.broadcast("reward-summary", ph);
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
