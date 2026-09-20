package ru.rooyzee.elytrixclans.clans;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.rooyzee.elytrixclans.role.Roles;
import ru.rooyzee.elytrixclans.status.Status;

@SerializableAs("clan_member")
public class ClanMember implements ConfigurationSerializable {

    private String name;
    private final Status status;
    private Roles role;
    private int kills;
    private int deaths;
    private double kda;
    private double level;
    private double points;

    public ClanMember(String name, Status status, double points, double level, double kda, int deaths, int kills, Roles role) {
        this.name = name;
        this.status = status != null ? status : Status.OFFLINE;
        this.points = Math.max(0, safe(points));
        this.level = Math.max(0, safe(level));
        this.kda = safe(kda);
        this.deaths = Math.max(0, deaths);
        this.kills = Math.max(0, kills);
        this.role = role != null ? role : new Roles("Участник");
    }

    private static double safe(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? 0 : value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Status getStatus() {
        return status;
    }

    public double getPoints() {
        return points;
    }

    public void setPoints(double points) {
        this.points = Math.max(0, safe(points));
    }

    public double getLevel() {
        return level;
    }

    public void setLevel(double level) {
        this.level = Math.max(0, safe(level));
    }

    public double getKDA() {
        return kda;
    }

    public void setKDA(double kda) {
        this.kda = safe(kda);
    }

    public int getDeaths() {
        return deaths;
    }

    public void setDeaths(int deaths) {
        this.deaths = Math.max(0, deaths);
    }

    public int getKills() {
        return kills;
    }

    public void setKills(int kills) {
        this.kills = Math.max(0, kills);
    }

    public Roles getRole() {
        if (role == null) role = new Roles("Участник");
        return role;
    }

    public void setRole(Roles role) {
        this.role = role != null ? role : new Roles("Участник");
    }

    // ---Vanish-хуки (Essentials/CMI)-------------------------------------------------------
    // Раньше на каждого участника при каждом открытии меню заново искались Method-объекты
    // через getMethod(). Кэшируем найденное; если плагинов ещё нет — повторяем попытку не чаще
    // раза в 30 секунд (они могут загружаться позже нас).

    private static volatile boolean essentialsResolved = false;
    private static volatile boolean cmiResolved = false;
    private static volatile long nextVanishProbe = 0L;
    private static Object essentialsInstance;
    private static Method essentialsVanishedMethod;
    private static Object cmiInstance;
    private static Method cmiGetVanishManager;
    private static Method cmiGetAllVanished;

    private static void probeVanishHooks() {
        if (essentialsResolved && cmiResolved) return;
        long now = System.currentTimeMillis();
        if (now < nextVanishProbe) return;
        nextVanishProbe = now + 30_000L;
        synchronized (ClanMember.class) {
            if (essentialsResolved && cmiResolved) return;
            try {
                Plugin essentials = Bukkit.getPluginManager().getPlugin("Essentials");
                if (essentials != null && essentials.isEnabled()) {
                    Method method = essentials.getClass().getMethod("getVanishedPlayers");
                    essentialsInstance = essentials;
                    essentialsVanishedMethod = method;
                }
                essentialsResolved = true;
            } catch (Throwable ignored) {
            }
            try {
                Plugin cmi = Bukkit.getPluginManager().getPlugin("CMI");
                if (cmi != null && cmi.isEnabled()) {
                    Class<?> cmiClass = Class.forName("com.Zrips.CMI.CMI");
                    Method getInstance = cmiClass.getMethod("getInstance");
                    Object instance = getInstance.invoke(null);
                    Method getVanishManager = cmiClass.getMethod("getVanishManager");
                    Object vanishManager = getVanishManager.invoke(instance);
                    if (instance != null && vanishManager != null) {
                        cmiInstance = instance;
                        cmiGetVanishManager = getVanishManager;
                        cmiGetAllVanished = vanishManager.getClass().getMethod("getAllVanished");
                    }
                    cmiResolved = true;
                }
            } catch (Throwable ignored) {
                cmiResolved = true;
            }
        }
    }

    public static Status getStatus(ClanMember member) {
        if (member == null) return Status.OFFLINE;
        Player player = member.getPlayer();
        if (player == null || !player.isOnline()) {
            return Status.OFFLINE;
        }

        probeVanishHooks();

        try {
            Method method = essentialsVanishedMethod;
            Object instance = essentialsInstance;
            if (method != null && instance != null) {
                Object result = method.invoke(instance);
                if (result instanceof Collection && ((Collection<?>) result).contains(player.getName())) {
                    return Status.OFFLINE;
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Object instance = cmiInstance;
            Method vanishManagerMethod = cmiGetVanishManager;
            Method allVanishedMethod = cmiGetAllVanished;
            if (instance != null && vanishManagerMethod != null && allVanishedMethod != null) {
                Object vanishManager = vanishManagerMethod.invoke(instance);
                Object vanished = allVanishedMethod.invoke(vanishManager);
                if (vanished instanceof Collection && ((Collection<?>) vanished).contains(player.getUniqueId())) {
                    return Status.OFFLINE;
                }
            }
        } catch (Throwable ignored) {
        }

        return Status.ONLINE;
    }

    public Player getPlayer() {
        if (name == null) return null;
        return Bukkit.getPlayerExact(name);
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("status", status.name());
        map.put("role", getRole().serialize());
        map.put("kills", kills);
        map.put("deaths", deaths);
        map.put("KDA", kda);
        map.put("level", level);
        map.put("points", points);
        return map;
    }

    public static ClanMember deserialize(Map<String, Object> map) {
        if (map == null) throw new IllegalArgumentException("member map cannot be null");
        String name = map.get("name") instanceof String ? (String) map.get("name") : null;
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("member name is missing");
        }
        Status status = Status.OFFLINE;
        Object statusObj = map.get("status");
        if (statusObj instanceof String) {
            try {
                status = Status.valueOf(((String) statusObj).toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Roles role = null;
        Object roleObj = map.get("role");
        if (roleObj instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> roleMap = (Map<String, Object>) roleObj;
                role = Roles.deserialize(roleMap);
            } catch (Throwable ignored) {
            }
        }
        int kills = number(map.get("kills"), 0);
        int deaths = number(map.get("deaths"), 0);
        double kda = number(map.get("KDA"), 0.0);
        double level = number(map.get("level"), 0.0);
        double points = number(map.get("points"), 0.0);
        return new ClanMember(name, status, points, level, kda, deaths, kills,
                role != null ? role : new Roles("Участник"));
    }

    /** Минимально восстановимый участник, если основные поля повреждены. */
    public static ClanMember fallbackFrom(Map<String, Object> map) {
        if (map == null) return null;
        Object nameObj = map.get("name");
        if (!(nameObj instanceof String) || ((String) nameObj).isEmpty()) return null;
        return new ClanMember((String) nameObj, Status.OFFLINE, 0, 0, 0, 0, 0, new Roles("Участник"));
    }

    private static int number(Object value, int def) {
        return value instanceof Number ? ((Number) value).intValue() : def;
    }

    private static double number(Object value, double def) {
        return value instanceof Number ? ((Number) value).doubleValue() : def;
    }
}