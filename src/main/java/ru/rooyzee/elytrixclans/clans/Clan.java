package ru.rooyzee.elytrixclans.clans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;
import ru.rooyzee.elytrixclans.function.impl.sethome.Home;

@SerializableAs("clan")
public class Clan implements ConfigurationSerializable {

    private String name;
    private String owner;
    private CopyOnWriteArrayList<ClanMember> memberList;
    private boolean pvp;
    private boolean glow;
    /** Цвет подсветки в формате #RRGGBB. Раньше жил только в памяти и терялся при рестарте. */
    private String glowColor;
    private double exp;
    private Home home;

    public Clan(double exp, Home home, String owner, CopyOnWriteArrayList<ClanMember> memberList, boolean pvp, boolean glow, String glowColor, String name) {
        this.owner = owner;
        this.exp = Math.max(0, sanitize(exp));
        // Пустой список вместо null: на memberList завязаны все циклы по клану.
        this.memberList = memberList != null ? memberList : new CopyOnWriteArrayList<>();
        this.name = name;
        this.home = home;
        this.pvp = pvp;
        this.glow = glow;
        this.glowColor = glowColor;
    }

    public double getExp() { return exp; }
    public void setExp(double exp) { this.exp = Math.max(0, sanitize(exp)); }
    public Home getHome() { return home; }
    public void setHome(Home home) { this.home = home; }
    public String getGlowColor() { return glowColor; }
    public void setGlowColor(String glowColor) { this.glowColor = glowColor; }
    public boolean isGlow() { return glow; }
    public void setGlow(boolean glow) { this.glow = glow; }
    public boolean isPvp() { return pvp; }
    public void setPvp(boolean pvp) { this.pvp = pvp; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public CopyOnWriteArrayList<ClanMember> getMemberList() { return memberList; }
    public void setMemberList(CopyOnWriteArrayList<ClanMember> memberList) {
        this.memberList = memberList != null ? memberList : new CopyOnWriteArrayList<>();
    }

    /** exp — double; NaN/Infinity превратились бы в битый YAML и NPE при округлениях. */
    private static double sanitize(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        return value;
    }

    @Override
    public Map<String, Object> serialize() {
        HashMap<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("owner", owner);
        List<Map<String, Object>> members = new ArrayList<>();
        for (ClanMember member : memberList) {
            if (member == null) continue;
            Map<String, Object> serialized = member.serialize();
            if (serialized != null) members.add(serialized);
        }
        map.put("memberList", members);
        map.put("pvp", pvp);
        map.put("glow", glow);
        if (glowColor != null) map.put("glowColor", glowColor);
        map.put("exp", exp);
        map.put("home", home != null ? home.serialize() : null);
        return map;
    }

    public static Clan deserialize(Map<String, Object> map) {
        if (map == null) throw new IllegalArgumentException("Input map cannot be null");
        String name = map.get("name") instanceof String ? (String) map.get("name") : "";
        String owner = map.get("owner") instanceof String ? (String) map.get("owner") : "";
        CopyOnWriteArrayList<ClanMember> memberList = new CopyOnWriteArrayList<>();
        Object membersObj = map.get("memberList");
        if (membersObj instanceof List) {
            for (Object obj : (List<?>) membersObj) {
                if (!(obj instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> memberMap = (Map<String, Object>) obj;
                try {
                    memberList.add(ClanMember.deserialize(memberMap));
                } catch (Throwable e) {
                    // Не теряем участника целиком из-за одного битого поля.
                    ClanMember fallback = ClanMember.fallbackFrom(memberMap);
                    if (fallback != null) memberList.add(fallback);
                }
            }
        }
        double exp = map.get("exp") instanceof Number ? ((Number) map.get("exp")).doubleValue() : 0;
        boolean pvp = map.get("pvp") instanceof Boolean && (Boolean) map.get("pvp");
        boolean glow = map.get("glow") instanceof Boolean && (Boolean) map.get("glow");
        String glowColor = map.get("glowColor") instanceof String ? (String) map.get("glowColor") : null;
        Home home = null;
        if (map.get("home") instanceof Map) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> homeMap = (Map<String, Object>) map.get("home");
                home = Home.deserialize(homeMap);
            } catch (Throwable e) {
                home = null;
            }
        }
        return new Clan(exp, home, owner, memberList, pvp, glow, glowColor, name);
    }
}