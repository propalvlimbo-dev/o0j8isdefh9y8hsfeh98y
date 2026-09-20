package ru.rooyzee.elytrixclans.permission;

public enum Permissions {
    SETHOME("sethome"),
    INVITE("invite"),
    KICK("kick"),
    DELETE("delete"),
    SHOP("shop"),
    PVP("pvp"),
    PROMOTE("promote"),
    DEMOTE("demote"),
    GLOW("glow"),
    SETLEADER("setleader");

    private final String name;

    Permissions(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}