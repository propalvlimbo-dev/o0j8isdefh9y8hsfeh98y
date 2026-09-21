package ru.rooyzee.elytrixclans.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.clans.ClanMember;
import ru.rooyzee.elytrixclans.hook.impl.VaultHook;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;

/**
 * Награды за ивент «Талисман».
 *
 * Точка входа для ElytrixTalisman: тот плагин знает, КТО удерживал талисман, а этот —
 * кому и сколько за это причитается. Талисману достаточно передать список ников
 * победившего клана, остальное (опыт клана, личный вклад, монеты, объявление) здесь.
 *
 * Как делятся награды (настраивается в config.yml, секция talisman):
 *
 *   Опыт клана       — фиксированный за победу, НЕ умножается на число участников:
 *                      иначе клан из 10 человек получал бы вдесятеро больше за тот же
 *                      талисман, и топ превращался бы в соревнование по числу игроков.
 *   Личный вклад     — по exp-per-holder каждому, кто реально удерживал. Это внутренняя
 *                      статистика клана (по ней растут роли), на топ она не влияет.
 *   Монеты           — общий банк money-pool делится МЕЖДУ участниками захвата.
 *                      Тоже намеренно: призовой фонд за талисман один, а не «по 10к
 *                      каждому», иначе выгодно затаскивать на точку весь клан.
 *                      Доля не опускается ниже money-min-share.
 *
 * Пример на дефолтных значениях (пул 30000, клановый опыт 150, вклад 50):
 *   захватывал 1 игрок  → клану +150 опыта, игроку 30000 монет и +50 к вкладу;
 *   захватывали 3       → клану +150 опыта, каждому по 10000 монет и +50 к вкладу;
 *   захватывали 6       → клану +150 опыта, каждому по 5000 монет и +50 к вкладу.
 */
public final class TalismanRewards {

    private TalismanRewards() {
    }

    /** Итог выдачи — чтобы вызывающий плагин мог залогировать результат. */
    public static final class Result {
        private final boolean success;
        private final String error;
        private final String clanName;
        private final double clanExp;
        private final double moneyEach;
        private final List<String> rewarded;

        private Result(boolean success, String error, String clanName,
                       double clanExp, double moneyEach, List<String> rewarded) {
            this.success = success;
            this.error = error;
            this.clanName = clanName;
            this.clanExp = clanExp;
            this.moneyEach = moneyEach;
            this.rewarded = rewarded;
        }

        public boolean isSuccess() { return success; }
        public String getError() { return error; }
        public String getClanName() { return clanName; }
        public double getClanExp() { return clanExp; }
        public double getMoneyEach() { return moneyEach; }
        public List<String> getRewarded() { return rewarded; }
    }

    private static Result fail(String error) {
        return new Result(false, error, null, 0, 0, new ArrayList<>());
    }

    /**
     * Выдаёт награды за захват талисмана.
     *
     * @param holderNames ники тех, кто удерживал талисман. Могут быть из разных кланов и
     *                    повторяться — метод сам оставит уникальных игроков победившего клана.
     * @return результат выдачи; не бросает исключений
     */
    public static Result reward(List<String> holderNames) {
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return fail("плагин не готов");
        if (holderNames == null || holderNames.isEmpty()) return fail("список игроков пуст");

        ClanManager manager = main.getClanManager();

        // Уникальные ники без учёта регистра, порядок захвата сохраняем.
        Map<String, String> unique = new LinkedHashMap<>();
        for (String raw : holderNames) {
            if (raw == null) continue;
            String name = raw.trim();
            if (name.isEmpty()) continue;
            unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        if (unique.isEmpty()) return fail("список игроков пуст");

        // Клан определяем по первому игроку, у которого он есть: талисман захватывает клан.
        Clan clan = null;
        for (String name : unique.values()) {
            Clan found = manager.getPlayerClan(name);
            if (found != null) {
                clan = found;
                break;
            }
        }
        if (clan == null) return fail("ни один из игроков не состоит в клане");

        // Оставляем только участников победившего клана: чужие в захвате наград не получают.
        List<String> winners = new ArrayList<>();
        for (String name : unique.values()) {
            Clan other = manager.getPlayerClan(name);
            if (other != null && other.getName() != null && other.getName().equals(clan.getName())) {
                winners.add(name);
            }
        }
        if (winners.isEmpty()) return fail("в захвате нет участников клана");

        double clanExp = main.getConfig().getDouble("talisman.clan-exp", 150.0);
        double expPerHolder = main.getConfig().getDouble("talisman.exp-per-holder", 50.0);
        double moneyPool = main.getConfig().getDouble("talisman.money-pool", 30000.0);
        double minShare = main.getConfig().getDouble("talisman.money-min-share", 2500.0);
        boolean announce = main.getConfig().getBoolean("talisman.announce", true);

        // Клановый опыт — один раз за победу.
        if (clanExp > 0) manager.addClanExp(clan, clanExp);

        // Личный вклад — каждому захватчику.
        if (expPerHolder > 0) {
            for (String name : winners) {
                ClanMember member = manager.getMember(clan, name);
                if (member != null) member.setLevel(member.getLevel() + expPerHolder);
            }
        }

        double share = 0;
        if (moneyPool > 0) {
            share = Math.max(moneyPool / winners.size(), Math.max(0, minShare));
            share = Math.floor(share * 100.0) / 100.0;
            payout(winners, share);
        }

        if (announce) announce(clan, winners, clanExp, share);

        return new Result(true, null, clan.getName(), clanExp, share, winners);
    }

    /** Совместимость: один захватчик. */
    public static Result reward(String holderName) {
        List<String> one = new ArrayList<>();
        one.add(holderName);
        return reward(one);
    }

    private static void payout(List<String> winners, double share) {
        if (share <= 0) return;
        Economy economy = VaultHook.getEconomy();
        if (economy == null) return;
        for (String name : winners) {
            try {
                // Деньги кладём и офлайн-игрокам: захватчик мог выйти сразу после ивента.
                OfflinePlayer target = Bukkit.getPlayerExact(name);
                if (target == null) target = Bukkit.getOfflinePlayer(name);
                economy.depositPlayer(target, share);
            } catch (Throwable t) {
                Bukkit.getLogger().warning("[ElytrixClans] Талисман: не удалось выдать монеты "
                        + name + ": " + t.getMessage());
            }
        }
    }

    private static void announce(Clan clan, List<String> winners, double clanExp, double share) {
        String holders = String.join("&7, &f", winners);
        Map<String, String> holder = ConfigUtil.setHolder(
                new String[]{"%clan%", "%players%", "%exp%", "%money%", "%count%"},
                new String[]{String.valueOf(clan.getName()), holders,
                        number(clanExp), number(share), String.valueOf(winners.size())});

        String message = null;
        Main main = Main.getInstance();
        if (main != null) message = main.getConfig().getString("messages.talismanWin");
        if (message == null || message.isEmpty()) {
            message = "&f☁ ᴇʟʏᴛʀɪx &7» &fКлан &#F8BEFB%clan% &fзабрал талисман: "
                    + "&#F8BEFB+%exp% &fопыта, по &#F8BEFB%money% &fмонет захватчикам &7(%players%&7)";
        }
        for (Map.Entry<String, String> entry : holder.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }
        Bukkit.broadcastMessage(HexUtil.translateHexColorCodes(message));
    }

    /** Числа без «.0»: опыт и монеты показываем целыми, когда дробной части нет. */
    private static String number(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    /** Подсказка для сторонних плагинов: есть ли у игрока клан. */
    public static boolean hasClan(Player player) {
        Main main = Main.getInstance();
        return main != null && main.getClanManager() != null
                && main.getClanManager().getPlayerClan(player) != null;
    }
}
