package ru.rooyzee.elytrixclans.hook.impl;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixclans.hook.IHook;

public class PAPIHook implements IHook {
    @Override
    public boolean initialized() {
        if (Bukkit.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            Bukkit.getLogger().warning("[ElytrixClans] PlaceholderAPI не обнаружен!");
            return false;
        }
        return true;
    }
}