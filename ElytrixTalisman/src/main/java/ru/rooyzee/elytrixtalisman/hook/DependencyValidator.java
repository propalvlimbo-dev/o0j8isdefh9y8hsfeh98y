package ru.rooyzee.elytrixtalisman.hook;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixtalisman.hook.impl.ElytrixClansHook;
import ru.rooyzee.elytrixtalisman.hook.impl.EssentialsHook;
import ru.rooyzee.elytrixtalisman.hook.impl.WorldEditHook;
import ru.rooyzee.elytrixtalisman.hook.impl.VirtualEntityHook;
import ru.rooyzee.elytrixtalisman.hook.impl.WorldGuardHook;

import java.util.Arrays;
import java.util.List;

public class DependencyValidator {

    public static boolean validate() {
        List<IHook> hooks = Arrays.asList(
                new WorldEditHook(),
                new WorldGuardHook(),
                new ElytrixClansHook(),
                new EssentialsHook(),
                new VirtualEntityHook()
        );
        boolean valid = true;
        for (IHook hook : hooks) {
            if (!hook.isAvailable()) {
                Bukkit.getLogger().severe("[ElytrixTalisman] " + hook.getName() + " не обнаружен!");
                valid = false;
            }
        }
        return valid;
    }
}