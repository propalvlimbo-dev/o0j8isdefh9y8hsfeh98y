package ru.rooyzee.elytrixclans.level;

public class Level {

    private int level;
    private int exp;
    private int maxMembers;
    private String color;

    public Level(int level, int exp, int maxMembers, String color) {
        this.level = level;
        this.exp = exp;
        this.maxMembers = maxMembers;
        this.color = color;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public int getMaxMembers() {
        return maxMembers;
    }

    public void setMaxMembers(int maxMembers) {
        this.maxMembers = maxMembers;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}