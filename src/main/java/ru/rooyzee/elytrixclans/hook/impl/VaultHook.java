package ru.rooyzee.elytrixclans.hook.impl;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import ru.rooyzee.elytrixclans.hook.IHook;

public class VaultHook implements IHook {

    private static Economy economy;

    public static Economy getEconomy() {
        if (economy == null) {
            // Повторная попытка: плагин-экономика (EssentialsX и т.п.) мог зарегистрировать
            // сервис уже ПОСЛЕ того, как мы закончили включение. Иначе /clan create падал с NPE.
            try {
                RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
                if (rsp != null) economy = rsp.getProvider();
            } catch (Exception ignored) {
            }
        }
        return economy;
    }

    @Override
    public boolean initialized() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            Bukkit.getLogger().warning("[ElytrixClans] Vault не обнаружен!");
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }
}