package ru.rooyzee.elytrixtalisman.hook.impl;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixtalisman.hook.IHook;

public class EssentialsHook implements IHook {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("Essentials") != null;
    }

    @Override
    public String getName() {
        return "Essentials";
    }
}