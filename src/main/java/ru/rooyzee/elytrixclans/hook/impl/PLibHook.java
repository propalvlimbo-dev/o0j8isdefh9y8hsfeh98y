package ru.rooyzee.elytrixclans.hook.impl;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixclans.hook.IHook;

public class PLibHook implements IHook {
    @Override
    public boolean initialized() {
        if (Bukkit.getServer().getPluginManager().getPlugin("ProtocolLib") == null) {
            Bukkit.getLogger().warning("[ElytrixClans] ProtocolLib не обнаружен!");
            return false;
        }
        return true;
    }
}