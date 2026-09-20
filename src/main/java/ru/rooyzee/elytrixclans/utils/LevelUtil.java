package ru.rooyzee.elytrixclans.utils;

import java.util.Map;
import ru.rooyzee.elytrixclans.Main;
import ru.rooyzee.elytrixclans.level.Level;
import ru.rooyzee.elytrixclans.level.config.LevelConfiguration;

public class LevelUtil {

    /**
     * Уровень клана по опыту. Никогда не возвращает null: раньше при пустом/битом levels.yml
     * метод отдавал null, и любое обращение к уровню (меню клана, /clan accept, плейсхолдеры,
     * сообщения о повышении) падало с NullPointerException.
     */
    public static Level getClanLevel(double exp) {
        Map<Integer, Level> levels = levels();
        if (levels.isEmpty()) return fallbackLevel();

        Level result = null;
        for (Map.Entry<Integer, Level> entry : levels.entrySet()) {
            Level level = entry.getValue();
            if (level == null) continue;
            if (exp < 0 && level.getLevel() == 1) {
                return level;
            }
            if (entry.getKey() <= exp) {
                if (result == null || result.getExp() < level.getExp()) {
                    result = level;
                }
            }
        }
        if (result != null) return result;
        // Опыта меньше, чем требует самый первый уровень — считаем его нулевым.
        Level lowest = null;
        for (Level level : levels.values()) {
            if (level == null) continue;
            if (lowest == null || level.getExp() < lowest.getExp()) lowest = level;
        }
        return lowest != null ? lowest : fallbackLevel();
    }

    public static Level getNextClanLevel(double exp) {
        Level current = getClanLevel(exp);
        if (current == null) return null;
        for (Level level : levels().values()) {
            if (level != null && level.getLevel() == current.getLevel() + 1) {
                return level;
            }
        }
        return null;
    }

    private static Map<Integer, Level> levels() {
        Main main = Main.getInstance();
        if (main == null) return java.util.Collections.emptyMap();
        LevelConfiguration configuration = main.getLevelConfiguration();
        if (configuration == null) return java.util.Collections.emptyMap();
        Map<Integer, Level> levels = configuration.levels();
        return levels != null ? levels : java.util.Collections.emptyMap();
    }

    private static Level fallbackLevel() {
        int maxMembers = 10;
        Main main = Main.getInstance();
        if (main != null) {
            maxMembers = Math.max(1, main.getConfig().getInt("level_1_max_members", 10));
        }
        return new Level(1, 0, maxMembers, "&7");
    }
}