package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.util.ColorUtil;
import ru.rooyzee.elytrixtalisman.util.PlaceholderUtil;

import java.util.List;
import java.util.Map;

public class MessageService {

    private final ConfigManager configManager;

    public MessageService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String getPrefix() {
        return configManager.getMessages().getString("prefix", "");
    }

    public void broadcast(String path, Map<String, String> placeholders) {
        placeholders.put("%prefix%", getPrefix());
        List<String> lines = configManager.getMessages().getStringList(path);
        for (String line : lines) {
            String msg = ColorUtil.colorize(PlaceholderUtil.replace(line, placeholders));
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        }
    }

    public void send(Player player, String path, Map<String, String> placeholders) {
        placeholders.put("%prefix%", getPrefix());
        String raw = configManager.getMessages().getString(path, "");
        player.sendMessage(ColorUtil.colorize(PlaceholderUtil.replace(raw, placeholders)));
    }

    public void sendList(Player player, String path, Map<String, String> placeholders) {
        placeholders.put("%prefix%", getPrefix());
        List<String> lines = configManager.getMessages().getStringList(path);
        for (String line : lines) {
            player.sendMessage(ColorUtil.colorize(PlaceholderUtil.replace(line, placeholders)));
        }
    }

    public String getRaw(String path) {
        return configManager.getMessages().getString(path, "");
    }
}