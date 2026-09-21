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
import ru.rooyzee.elytrixclans.utils.MenuUtil;

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
        if (main == null || main.getClanManager() == null) {
            // Плейсхолдеры для неймтега обязаны отдавать ПУСТУЮ строку, а не прочерк:
            // иначе у игрока без клана (или пока плагин не прогрузился) над головой
            // повиснет «—» вместо чистого «донат + ник».
            if (isNameTagPlaceholder(identifier)) return "";
            return EMPTY;
        }

        // Топ по поинтам убран вместе с самими поинтами: осталось только количество опыта.
        if (identifier.startsWith("clan_top_exp_") || identifier.startsWith("clan_top_name_")) {
            return getTopClans(identifier, parsePlace(identifier));
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

        // Готовый тег клана для строки над головой.
        //
        // Ключевое свойство: БЕЗ клана возвращается пустая строка (не "Без клана",
        // не пробел). Поэтому в неймтеге у игрока без клана не остаётся ни лишнего
        // пробела, ни скобок — там просто донат и ник.
        if (identifier.equalsIgnoreCase("clan_tag")) {
            if (clan == null) return "";
            String color = LevelUtil.getClanLevel(clan.getExp()).getColor();
            // Формат настраивается в config.yml: clanTagFormat.
            String format = main.getConfig().getString("clanTagFormat", "&7[%color%%clan%&7] ");
            if (format == null || format.isEmpty()) return "";
            return HexUtil.translateHexColorCodes(format
                    .replace("%color%", color)
                    .replace("%clan%", clan.getName()));
        }

        // Голое название без цветов и скобок: если оформление задаётся в самом TAB.
        if (identifier.equalsIgnoreCase("clan_name_plain")) {
            return clan != null ? clan.getName() : "";
        }

        if (identifier.equalsIgnoreCase("clan_level")) {
            return clan != null
                    ? String.valueOf(LevelUtil.getClanLevel(clan.getExp()).getLevel()) : "";
        }

        if (identifier.equalsIgnoreCase("clan_exp")) {
            return clan != null ? MenuUtil.exp(clan.getExp()) : "0";
        }

        if (identifier.equalsIgnoreCase("clan_role")) {
            // player бывает null: PAPI умеет запрашивать плейсхолдеры без игрока.
            if (clan == null || player == null) return "";
            ru.rooyzee.elytrixclans.clans.ClanMember member =
                    main.getClanManager().getMember(clan, player.getName());
            return member != null
                    ? ru.rooyzee.elytrixclans.role.ClanRoles.normalize(member.getRole().getName())
                    : "";
        }

        return identifier;
    }

    /** Плейсхолдеры, у которых «нет данных» должно означать пустую строку. */
    private static boolean isNameTagPlaceholder(String identifier) {
        return identifier.equalsIgnoreCase("clan_tag")
                || identifier.equalsIgnoreCase("clan_name")
                || identifier.equalsIgnoreCase("clan_name_plain")
                || identifier.equalsIgnoreCase("clan_level")
                || identifier.equalsIgnoreCase("clan_role");
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

    private String getTopClans(String identifier, int place) {
        if (place < 1 || place > TOP_SIZE) return EMPTY;
        List<Clan> top = sortedTop();
        if (place > top.size()) return EMPTY;
        Clan clan = top.get(place - 1);
        if (clan == null) return EMPTY;
        String color = LevelUtil.getClanLevel(clan.getExp()).getColor();
        if (identifier.contains("exp")) {
            return HexUtil.translateHexColorCodes(color + MenuUtil.exp(clan.getExp()));
        }
        return HexUtil.translateHexColorCodes(color + clan.getName());
    }

    private List<Clan> sortedTop() {
        if (Main.getInstance() == null || Main.getInstance().getClanManager() == null) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        if (now - topStamp < TOP_CACHE_MS && !topByExp.isEmpty()) return topByExp;

        List<Clan> clans = Main.getInstance().getClanManager().getClans();
        List<Clan> copy = new ArrayList<>(clans);
        copy.sort(Comparator.comparingDouble(Clan::getExp).reversed());
        List<Clan> top = Collections.unmodifiableList(new ArrayList<>(copy.subList(0, Math.min(TOP_SIZE, copy.size()))));

        topByExp = top;
        topStamp = now;
        return top;
    }
}