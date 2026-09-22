package ru.rooyzee.elytrixtalisman.hook.impl;

import org.bukkit.Bukkit;
import ru.rooyzee.elytrixtalisman.hook.IHook;

/**
 * Проверка VirtualEntityApi — на нём рисуются тотемы захватчиков.
 *
 * Помимо самого плагина проверяется наличие класса: jar может лежать в plugins, но
 * не подняться (не та версия сервера, нет BLib). Без этой проверки первая же попытка
 * создать тотем падала бы с NoClassDefFoundError прямо в тике ивента.
 */
public class VirtualEntityHook implements IHook {

    @Override
    public boolean isAvailable() {
        if (Bukkit.getPluginManager().getPlugin("VirtualEntityApi") == null) return false;
        try {
            Class.forName("dev.by1337.virtualentity.api.virtual.decoration.VirtualArmorStand");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "VirtualEntityApi";
    }
}
