package ru.rooyzee.elytrixclans.level.config;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.rooyzee.elytrixclans.level.Level;

public class LevelConfiguration {

    private final JavaPlugin plugin;
    private File configFile;
    private FileConfiguration fileConfiguration;

    // levels() раньше пересобиралась на КАЖДЫЙ вызов: метод дёргается при каждом открытии меню,
    // при каждой смерти игрока и на каждом запросе плейсхолдеров скорборда.
    private volatile Map<Integer, Level> cachedLevels;

    public LevelConfiguration(JavaPlugin plugin) {
        this.plugin = plugin;
        setupYml();
    }

    public void reloadYml() {
        fileConfiguration = YamlConfiguration.loadConfiguration(configFile);
        cachedLevels = null;
    }

    private void setupYml() {
        configFile = new File(plugin.getDataFolder(), "levels.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            try {
                // Не создаём пустой файл: без уровней плагин не может посчитать ни один уровень клана.
                plugin.saveResource("levels.yml", false);
                if (!configFile.exists()) {
                    configFile.createNewFile();
                }
            } catch (IOException | IllegalArgumentException e) {
                plugin.getLogger().warning("Не удалось создать levels.yml: " + e.getMessage());
            }
        }
        fileConfiguration = YamlConfiguration.loadConfiguration(configFile);
        cachedLevels = null;
    }

    public Map<Integer, Level> levels() {
        Map<Integer, Level> local = cachedLevels;
        if (local != null) return local;
        local = parseLevels();
        cachedLevels = local;
        return local;
    }

    private Map<Integer, Level> parseLevels() {
        Map<Integer, Level> levels = new HashMap<>();
        if (fileConfiguration == null) return Collections.unmodifiableMap(levels);
        ConfigurationSection section = fileConfiguration.getConfigurationSection("levels");
        if (section == null) return Collections.unmodifiableMap(levels);
        for (String sec : section.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(sec.trim());
            } catch (NumberFormatException e) {
                // Соседняя секция с нечисловым ключом не должна ронять все уровни кланов.
                plugin.getLogger().warning("ElytrixClans: пропущен уровень с нечисловым ключом '" + sec + "' в levels.yml");
                continue;
            }
            int exp = fileConfiguration.getInt("levels." + sec + ".exp");
            int maxMembers = fileConfiguration.getInt("levels." + sec + ".max_members", 10);
            String color = fileConfiguration.getString("levels." + sec + ".color", "&7");
            levels.put(exp, new Level(level, exp, Math.max(0, maxMembers), color));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(levels));
    }
}