package ru.rooyzee.elytrixclans.function.impl.sethome;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

@SerializableAs("clan_home")
public class Home implements ConfigurationSerializable {

    private String world;
    private double x, y, z, yaw, pitch;

    public Home(String world, double x, double y, double z, double yaw, double pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public double getYaw() { return yaw; }
    public double getPitch() { return pitch; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("world", world);
        map.put("yaw", yaw);
        map.put("pitch", pitch);
        return map;
    }

    public static Home deserialize(Map<String, Object> map) {
        if (map == null) return null;
        String world = map.get("world") instanceof String ? (String) map.get("world") : null;
        // Значения читаем мягко: пропущенное или записанное строкой поле не должно
        // обрывать загрузку всего клана.
        double x = number(map.get("x"));
        double y = number(map.get("y"));
        double z = number(map.get("z"));
        double pitch = number(map.get("pitch"));
        double yaw = number(map.get("yaw"));
        if (world == null) return null;
        return new Home(world, x, y, z, yaw, pitch);
    }

    private static double number(Object value) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }
}