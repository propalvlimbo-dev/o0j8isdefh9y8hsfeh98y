package ru.rooyzee.elytrixtalisman.service;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;
import ru.rooyzee.elytrixtalisman.util.ColorUtil;
import ru.rooyzee.elytrixtalisman.util.PlaceholderUtil;

import java.util.Map;

public class BossBarService {

    private final BarColor color;
    private final BarStyle style;
    private final String titleTemplate;
    private BossBar bossBar;

    public BossBarService(ConfigManager configManager) {
        this.color = BarColor.valueOf(configManager.getBossbar().getString("bossbar.color", "BLUE"));
        this.style = BarStyle.valueOf(configManager.getBossbar().getString("bossbar.style", "SOLID"));
        this.titleTemplate = configManager.getBossbar().getString("bossbar.title", "Talisman");
    }

    public void create() {
        bossBar = Bukkit.createBossBar(ColorUtil.colorize(titleTemplate), color, style);
        bossBar.setVisible(true);
        bossBar.setProgress(0.0);
        Bukkit.getOnlinePlayers().forEach(bossBar::addPlayer);
    }

    public void update(Map<String, String> placeholders, double progress) {
        if (bossBar == null) return;
        String title = ColorUtil.colorize(PlaceholderUtil.replace(titleTemplate, placeholders));
        bossBar.setTitle(title);
        bossBar.setProgress(Math.min(Math.max(progress, 0.0), 1.0));
    }

    public void flashColor() {
        if (bossBar == null) return;
        bossBar.setColor(bossBar.getColor() == color ? BarColor.RED : color);
    }

    public void addPlayer(Player player) {
        if (bossBar != null) bossBar.addPlayer(player);
    }

    public void destroy() {
        if (bossBar != null) {
            bossBar.setVisible(false);
            bossBar.removeAll();
            bossBar = null;
        }
    }

    public boolean isActive() {
        return bossBar != null;
    }
}