package ru.rooyzee.elytrixclans.status;

public enum Status {
    ONLINE("&#F8BEFB▶ Онлайн"),
    OFFLINE("&c✖ Оффлайн");

    private final String name;

    Status(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}