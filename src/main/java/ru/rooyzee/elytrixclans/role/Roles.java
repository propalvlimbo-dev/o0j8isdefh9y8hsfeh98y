package ru.rooyzee.elytrixclans.role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import ru.rooyzee.elytrixclans.permission.Permissions;

@SerializableAs("role")
public class Roles implements ConfigurationSerializable {

    private String name;
    private List<Permissions> permissions;

    public Roles(String name, Permissions... permissions) {
        this.name = name;
        // Копия, а не Arrays.asList(...): у списка из asList нельзя ни добавлять, ни удалять,
        // а права ролям меняют прямо из меню участника.
        this.permissions = new ArrayList<>(Arrays.asList(permissions));
    }

    public Roles(String name) {
        this.name = name;
        this.permissions = new ArrayList<>();
    }

    public List<Permissions> getPermissions() {
        if (permissions == null) permissions = new ArrayList<>();
        return permissions;
    }

    public String getName() {
        return name == null ? "Участник" : name;
    }

    /** Есть ли у роли право. Null-безопасно: у роли без списка прав нет никаких. */
    public boolean hasPermission(Permissions permission) {
        if (permission == null) return false;
        return getPermissions().contains(permission);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPermissions(List<Permissions> permissions) {
        this.permissions = permissions != null ? new ArrayList<>(permissions) : new ArrayList<>();
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", getName());
        List<String> permissionNames = new ArrayList<>();
        for (Permissions permission : getPermissions()) {
            if (permission != null) permissionNames.add(permission.getName());
        }
        map.put("permissions", permissionNames);
        return map;
    }

    public static Roles deserialize(Map<String, Object> args) {
        if (args == null) return ClanRoles.rookie();
        Object nameObj = args.get("name");
        String name = nameObj instanceof String ? (String) nameObj : ClanRoles.ROOKIE;
        // Права берём из описания роли, а не из файла: набор прав у роли задан в коде,
        // поэтому старые сохранения («Участник» с произвольным списком) чинятся сами.
        String normalized = ClanRoles.normalize(name);
        // Набор прав роли всегда берётся из ClanRoles, даже если название уже актуально.
        // Иначе кланы, сохранённые до расширения прав роли (например, Модератор получил
        // GLOW), навсегда остались бы со старым списком из файла.
        return ClanRoles.create(normalized);
    }
}