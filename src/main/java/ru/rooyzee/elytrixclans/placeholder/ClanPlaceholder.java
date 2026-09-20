package ru.rooyzee.elytrixclans.placeholder;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bukkit.entity.Player;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.clans.Clan;
import ru.rooyzee.elytrixclans.utils.ConfigUtil;
import ru.rooyzee.elytrixclans.utils.HexUtil;
import ru.rooyzee.elytrixclans.utils.LevelUtil;

public class ClanPlaceholder extends PlaceholderExpansion {

    private static final String EMPTY = "&7—";
    private static final long TOP_CACHE_MS = 15_000L;
    private static final int TOP_SIZE = 10;

    private final Main plugin;

    // DecimalFormat не потокобезопасен, а плейсхолдеры запрашивают и из асинхронных плагинов.
    private static final ThreadLocal<DecimalFormat> FORMAT = new ThreadLocal<DecimalFormat>() {
        @Override
        protected DecimalFormat initialValue() {
            return new DecimalFormat("#.##");
        }
    };

    // Топ сортировался по всем кланам на КАЖДЫЙ запрос плейсхолдера: скорборд с %elytrixclans_clan_top_*_1..10%
    // на сотне игроков — это десятки тысяч сортировок в минуту в main-потоке. Кэшируем на 15 секунд.
    private volatile long topStamp = 0L;
    private volatile List<Clan> topByExp = Collections.emptyList();
    private volatile List<Clan> topByPoints = Collections.emptyList();

    public ClanPlaceholder(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getAuthor() { return "rooyzee"; }

    @Override
    public boolean persist() { return true; }

    @Override
    public String getIdentifier() { return "elytrixclans"; }

    @Override
    public String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (identifier == null) return "";
        Main main = Main.getInstance();
        if (main == null || main.getClanManager() == null) return EMPTY;

        if (identifier.startsWith("clan_top_exp_")) {
            return getTopClans(identifier, parsePlace(identifier), false, false);
        }
        if (identifier.startsWith("clan_top_points_")) {
            return getTopClans(identifier, parsePlace(identifier), true, true);
        }
        if (identifier.startsWith("clan_top_name_")) {
            return getTopClans(identifier, parsePlace(identifier), false, false);
        }

        Clan clan = player != null && Main.getInstance().getClanManager() != null
                ? Main.getInstance().getClanManager().getPlayerClan(player)
                : null;

        if (identifier.equalsIgnoreCase("clan_name_board")) {
            if (clan != null) {
                String color = LevelUtil.getClanLevel(clan.getExp()).getColor();
                return HexUtil.translateHexColorCodes(color + clan.getName());
            }
            return HexUtil.translateHexColorCodes(ConfigUtil.getString("noClanPlaceholder"));
        }

        if (identifier.equalsIgnoreCase("clan_name")) {
            if (clan != null) {
                String color = LevelUtil.getClanLevel(clan.getExp()).getColor();
                return HexUtil.translateHexColorCodes(color + clan.getName());
            }
            return "";
        }

        return identifier;
    }

    private static int parsePlace(String identifier) {
        int index = identifier.lastIndexOf('_');
        if (index < 0 || index == identifier.length() - 1) return -1;
        try {
            return Integer.parseInt(identifier.substring(index + 1).trim());
        } catch (NumberFormatException e) {
            // Битый placeholder (%elytrixclans_clan_top_exp_% ) не должен валить PAPI в спаме исключений.
            return -1;
        }
    }

    private String getTopClans(String identifier, int place, boolean byPoints, boolean showPoints) {
        if (place < 1 || place > TOP_SIZE) return EMPTY;
        List<Clan> top = sortedTop(byPoints);
        if (place > top.size()) return EMPTY;
        Clan clan = top.get(place - 1);
        if (clan == null) return EMPTY;
        String color = LevelUtil.getClanLevel(clan.getExp()).getColor();
        if (showPoints) {
            return HexUtil.translateHexColorCodes(color + FORMAT.get().format(clan.getPoints()));
        }
        if (identifier.contains("exp")) {
            return HexUtil.translateHexColorCodes(color + FORMAT.get().format(LevelUtil.getClanLevel(clan.getExp()).getLevel()));
        }
        return HexUtil.translateHexColorCodes(color + clan.getName());
    }

    private List<Clan> sortedTop(boolean byPoints) {
        if (Main.getInstance() == null || Main.getInstance().getClanManager() == null) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        List<Clan> cached = byPoints ? topByPoints : topByExp;
        if (now - topStamp < TOP_CACHE_MS && !cached.isEmpty()) return cached;

        List<Clan> clans = Main.getInstance().getClanManager().getClans();
        List<Clan> copy = new ArrayList<>(clans);
        copy.sort(byPoints
                ? Comparator.comparingDouble(Clan::getPoints).reversed()
                : Comparator.comparingDouble(Clan::getExp).reversed());
        List<Clan> top = Collections.unmodifiableList(new ArrayList<>(copy.subList(0, Math.min(TOP_SIZE, copy.size()))));

        if (byPoints) topByPoints = top; else topByExp = top;
        if (now - topStamp >= TOP_CACHE_MS) topStamp = now;
        return top;
    }
}