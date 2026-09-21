package ru.rooyzee.elytrixtalisman.config.file;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.rooyzee.elytrixtalisman.Main;

import java.io.File;

public class MessagesFile {

    private final Main plugin;
    private FileConfiguration config;
    private File file;

    public MessagesFile(Main plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration get() {
        return config;
    }
}