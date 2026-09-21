package ru.rooyzee.elytrixtalisman.model;

public class ClanCaptureData {

    private int capturePoints;
    private int kills;
    private int deaths;

    public ClanCaptureData() {
        this.capturePoints = 0;
        this.kills = 0;
        this.deaths = 0;
    }

    public int getCapturePoints() {
        return capturePoints;
    }

    public void addCapturePoints(int amount) {
        this.capturePoints += amount;
    }

    public int getKills() {
        return kills;
    }

    public void addKill() {
        this.kills++;
    }

    public int getDeaths() {
        return deaths;
    }

    public void addDeath() {
        this.deaths++;
    }
}