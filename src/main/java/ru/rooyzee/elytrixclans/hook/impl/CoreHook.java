package ru.rooyzee.elytrixclans.hook.impl;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixclans.hook.IHook;

public class CoreHook implements IHook {

    @Override
    public boolean initialized() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Essentials") == null
                && Bukkit.getServer().getPluginManager().getPlugin("CMI") == null) {
            Bukkit.getLogger().info("[ElytrixClans] Essentials или CMI не обнаружен!");
            return false;
        }
        return true;
    }

    public String getCore() {
        return Bukkit.getServer().getPluginManager().getPlugin("Essentials") != null ? "Essentials" : "CMI";
    }
}