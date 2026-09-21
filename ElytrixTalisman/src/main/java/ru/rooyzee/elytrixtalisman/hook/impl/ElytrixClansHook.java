package ru.rooyzee.elytrixtalisman.hook.impl;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixtalisman.hook.IHook;

public class ElytrixClansHook implements IHook {

    @Override
    public boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("ElytrixClans") != null;
    }

    @Override
    public String getName() {
        return "ElytrixClans";
    }
}