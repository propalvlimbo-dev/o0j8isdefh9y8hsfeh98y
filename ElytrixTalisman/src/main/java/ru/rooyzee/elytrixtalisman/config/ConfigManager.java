package ru.rooyzee.elytrixtalisman.config;

import org.bukkit.configuration.file.FileConfiguration;
import ru.rooyzee.elytrixtalisman.Main;
import ru.rooyzee.elytrixtalisman.config.file.*;

public class ConfigManager {

    private final SettingsFile settingsFile;
    private final MessagesFile messagesFile;
    private final RewardsFile rewardsFile;
    private final SchedulesFile schedulesFile;
    private final BossBarFile bossBarFile;
    private final RegionFile regionFile;

    public ConfigManager(Main plugin) {
        this.settingsFile = new SettingsFile(plugin);
        this.messagesFile = new MessagesFile(plugin);
        this.rewardsFile = new RewardsFile(plugin);
        this.schedulesFile = new SchedulesFile(plugin);
        this.bossBarFile = new BossBarFile(plugin);
        this.regionFile = new RegionFile(plugin);
    }

    public void loadAll() {
        settingsFile.reload();
        messagesFile.reload();
        rewardsFile.reload();
        schedulesFile.reload();
        bossBarFile.reload();
        regionFile.reload();
    }

    public FileConfiguration getConfig() {
        return settingsFile.get();
    }

    public FileConfiguration getMessages() {
        return messagesFile.get();
    }

    public FileConfiguration getRewards() {
        return rewardsFile.get();
    }

    public FileConfiguration getSchedules() {
        return schedulesFile.get();
    }

    public FileConfiguration getBossbar() {
        return bossBarFile.get();
    }

    public FileConfiguration getRegion() {
        return regionFile.get();
    }
}