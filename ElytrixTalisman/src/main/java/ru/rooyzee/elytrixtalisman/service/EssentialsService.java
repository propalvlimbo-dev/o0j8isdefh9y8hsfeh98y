package ru.rooyzee.elytrixtalisman.service;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import org.bukkit.entity.Player;
import ru.rooyzee.elytrixtalisman.config.ConfigManager;

public class EssentialsService {

    private final boolean disableGod;
    private final boolean disableFly;

    public EssentialsService(ConfigManager configManager) {
        this.disableGod = configManager.getConfig().getBoolean("disable-god", true);
        this.disableFly = configManager.getConfig().getBoolean("disable-fly", true);
    }

    public void strip(Player player) {
        if (player.hasPermission("elytrixtalisman.bypass")) return;
        try {
            User user = ((Essentials) Essentials.getPlugin(Essentials.class)).getUser(player);
            if (disableGod && user.isGodModeEnabled()) {
                user.setGodModeEnabled(false);
            }
            if (disableFly && player.isFlying()) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
        } catch (Exception ignored) {
        }
    }
}