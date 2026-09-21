package ru.rooyzee.elytrixtalisman.config.file;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixtalisman.Main;

import java.io.File;

public class SchedulesFile {

    private final Main plugin;
    private FileConfiguration config;
    private File file;

    public SchedulesFile(Main plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        file = new File(plugin.getDataFolder(), "schedules.yml");
        if (!file.exists()) plugin.saveResource("schedules.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration get() {
        return config;
    }
}