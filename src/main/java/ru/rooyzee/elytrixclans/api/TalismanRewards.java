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
 * Точка входа для ElytrixTalisman: тот плагин знает, КТО и сколько удерживал точку, а этот —
 * кому и сколько за это причитается. Талисман передаёт клан, список его захватчиков и занятое
 * место, остальное (клановый опыт, личный вклад, монеты, объявление) делается здесь.
 *
 * Награды настраиваются в config.yml, секция talisman.places — отдельно на каждое место.
 *
 *   clan-exp        — опыт клану за место. Даётся ОДИН раз и НЕ умножается на число
 *                     захватчиков: иначе клан из десяти человек получал бы за тот же талисман
 *                     вдесятеро больше, и топ мерил бы численность, а не силу клана.
 *   exp-per-holder  — личный вклад каждому захватчику. Это внутренняя статистика клана
 *                     (по ней растут роли), на топ она не влияет, поэтому здесь выплата равная.
 *   money-pool      — призовой фонд места, ДЕЛИТСЯ между захватчиками. Тоже намеренно:
 *                     фонд за место один, иначе выгодно тащить на точку весь состав.
 *   money-min-share — нижняя граница доли, чтобы при толпе выплата не выродилась в копейки.
 *
 * Пример для первого места (фонд 30000):
 *   захватывал 1 игрок  → клану +500 опыта, игроку 30000 монет и +50 к вкладу;
 *   захватывали 3       → клану +500 опыта, каждому по 10000 монет и +50 к вкладу;
 *   захватывали 6       → клану +500 опыта, каждому по 5000 монет и +50 к вкладу.
 */
public final class TalismanRewards {

    private TalismanRewards() {
    }

    /** Итог выдачи — чтобы вызывающий плагин мог залогировать результат. */
    public static final class Result {
        private final boolean success;
        private final String error;
        private final int place;
        private final String clanName;
        private final double clanExp;
        private final double moneyEach;
        private final List<String> rewarded;

        private Result(boolean success, String error, int place, String clanName,
                       double clanExp, double moneyEach, List<String> rewarded) {
            this.success = success;
            this.error = error;
            this.place = place;
            this.clanName = clanName;
            this.clanExp = clanExp;
            this.moneyEach = moneyEach;
            this.rewarded = rewarded;
        }

        public boolean isSuccess() { return success; }
        public int getPlace() { return place; }
        public String getError() { return error; }
        public String getClanName() { return clanName; }
        public double getClanExp() { return clanExp; }
        public double getMoneyEach() { return moneyEach; }
        public List<String> getRewarded() { return rewarded; }
    }

    private static Result fail(String error) {
        return new Result(false, error, 0, null, 0, 0, new ArrayList<>());
    }

    /**
     * Выдаёт награды за захват талисмана.
     *
     * @param holderNames ники тех, кто удерживал талисман. Могут быть из разных кланов и
     *                    повторяться — метод сам оставит уникальных игроков победившего клана.
     * @return результат выдачи; не бросает исключений
     */
    /**
     * Выдаёт награды одному клану за занятое место.
     *
     * @param clanName    клан-победитель; если null, определяется по первому игроку из списка
     * @param holderNames ники тех, кто удерживал точку. Могут быть из разных кланов и
     *                    повторяться — останутся уникальные участники нужного клана
     * @param place       занятое место (1, 2, 3...)
     * @return результат выдачи; исключений не бросает
     */
    public static Result rewardPlace(String clanName, List<String> holderNames, int place) {
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return fail("плагин не готов");
        if (place < 1) return fail("неверное место: " + place);

        ClanManager manager = main.getClanManager();

        // Уникальные ники без учёта регистра, порядок захвата сохраняем.
        Map<String, String> unique = new LinkedHashMap<>();
        if (holderNames != null) {
            for (String raw : holderNames) {
                if (raw == null) continue;
                String name = raw.trim();
                if (name.isEmpty()) continue;
                unique.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
            }
        }

        Clan clan = clanName == null ? null : manager.getClanByName(clanName);
        if (clan == null) {
            // Клан не назвали (или он переименован) — берём по первому игроку с кланом.
            for (String name : unique.values()) {
                Clan found = manager.getPlayerClan(name);
                if (found != null) {
                    clan = found;
                    break;
                }
            }
        }
        if (clan == null) return fail("клан не найден");

        // Оставляем только участников победившего клана: чужие наград не получают.
        List<String> winners = new ArrayList<>();
        for (String name : unique.values()) {
            Clan other = manager.getPlayerClan(name);
            if (other != null && other.getName() != null && other.getName().equals(clan.getName())) {
                winners.add(name);
            }
        }

        double clanExp = setting(main, place, "clan-exp", defaultClanExp(place));
        double expPerHolder = setting(main, place, "exp-per-holder", defaultHolderExp(place));
        double moneyPool = setting(main, place, "money-pool", defaultPool(place));
        double minShare = setting(main, place, "money-min-share", 0.0);
        boolean announce = main.getConfig().getBoolean("talisman.announce", true);

        // Клановый опыт — один раз за место, даже если поимённого списка нет.
        if (clanExp > 0) manager.addClanExp(clan, clanExp);

        // Личный вклад — каждому захватчику.
        if (expPerHolder > 0) {
            for (String name : winners) {
                ClanMember member = manager.getMember(clan, name);
                if (member != null) member.setLevel(member.getLevel() + expPerHolder);
            }
        }

        double share = 0;
        if (moneyPool > 0 && !winners.isEmpty()) {
            share = Math.max(moneyPool / winners.size(), Math.max(0, minShare));
            share = Math.floor(share * 100.0) / 100.0;
            payout(winners, share);
        }

        if (announce) announce(clan, winners, place, clanExp, share);

        return new Result(true, null, place, clan.getName(), clanExp, share, winners);
    }

    /** Захват без указания места — считается первым. */
    public static Result reward(List<String> holderNames) {
        return rewardPlace(null, holderNames, 1);
    }

    /** Значение награды для места: сначала talisman.places.N.<key>, иначе значение по умолчанию. */
    private static double setting(Main main, int place, String key, double fallback) {
        String path = "talisman.places." + place + "." + key;
        if (main.getConfig().isSet(path)) return main.getConfig().getDouble(path);
        // Совместимость со старым плоским конфигом (talisman.clan-exp и т.д.) — только 1 место.
        if (place == 1 && main.getConfig().isSet("talisman." + key)) {
            return main.getConfig().getDouble("talisman." + key);
        }
        return fallback;
    }

    // Значения на случай, если места в конфиге нет вовсе: каждое следующее вдвое скромнее.
    private static double defaultClanExp(int place) {
        if (place == 1) return 500.0;
        if (place == 2) return 300.0;
        if (place == 3) return 150.0;
        return 0.0;
    }

    private static double defaultHolderExp(int place) {
        if (place == 1) return 50.0;
        if (place == 2) return 30.0;
        if (place == 3) return 15.0;
        return 0.0;
    }

    private static double defaultPool(int place) {
        if (place == 1) return 30000.0;
        if (place == 2) return 15000.0;
        if (place == 3) return 7500.0;
        return 0.0;
    }

    /** Совместимость: один захватчик, первое место. */
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

    private static void announce(Clan clan, List<String> winners, int place,
                                 double clanExp, double share) {
        String holders = winners.isEmpty() ? "&7—" : String.join("&7, &f", winners);
        Map<String, String> holder = ConfigUtil.setHolder(
                new String[]{"%clan%", "%players%", "%exp%", "%money%", "%count%", "%place%"},
                new String[]{String.valueOf(clan.getName()), holders,
                        number(clanExp), number(share), String.valueOf(winners.size()),
                        String.valueOf(place)});

        String message = null;
        Main main = Main.getInstance();
        if (main != null) message = main.getConfig().getString("messages.talismanWin");
        if (message == null || message.isEmpty()) {
            message = "&f☁ ᴇʟʏᴛʀɪx &7» &#F8BEFB#%place% &fклан &#F8BEFB%clan%&f: "
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
