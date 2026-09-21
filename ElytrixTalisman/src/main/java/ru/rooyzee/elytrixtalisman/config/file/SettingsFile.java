package ru.rooyzee.elytrixtalisman.config.file;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixtalisman.Main;

import java.io.File;

public class SettingsFile {

    private final Main plugin;
    private FileConfiguration config;

    public SettingsFile(Main plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public FileConfiguration get() {
        return config;
    }
}