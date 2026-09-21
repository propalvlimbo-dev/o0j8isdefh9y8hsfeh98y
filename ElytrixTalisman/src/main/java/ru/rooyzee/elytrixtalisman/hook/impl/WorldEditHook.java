package ru.rooyzee.elytrixtalisman.hook.impl;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixtalisman.hook.IHook;

public class WorldEditHook implements IHook {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
    }

    @Override
    public String getName() {
        return "WorldEdit";
    }
}