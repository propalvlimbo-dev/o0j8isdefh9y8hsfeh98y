package ru.rooyzee.elytrixclans.role;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        if (args == null) return new Roles("Участник");
        Object nameObj = args.get("name");
        String name = nameObj instanceof String ? (String) nameObj : "Участник";
        List<String> permissionsNames = null;
        Object permissionsObj = args.get("permissions");
        if (permissionsObj instanceof List) {
            permissionsNames = new ArrayList<>();
            for (Object entry : (List<?>) permissionsObj) {
                if (entry != null) permissionsNames.add(String.valueOf(entry));
            }
        }
        List<Permissions> permissions = new ArrayList<>();
        if (permissionsNames != null) {
            for (String permName : permissionsNames) {
                try {
                    permissions.add(Permissions.valueOf(permName.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return new Roles(name, permissions.toArray(new Permissions[0]));
    }
}