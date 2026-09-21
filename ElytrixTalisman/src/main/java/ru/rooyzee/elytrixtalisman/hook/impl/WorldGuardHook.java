package ru.rooyzee.elytrixtalisman.hook.impl;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixtalisman.hook.IHook;

public class WorldGuardHook implements IHook {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }

    @Override
    public String getName() {
        return "WorldGuard";
    }
}