package ru.rooyzee.elytrixtalisman.model;

public class RewardTier {

    private final int place;
    private final double exp;
    private final double points;

    public RewardTier(int place, double exp, double points) {
        this.place = place;
        this.exp = exp;
        this.points = points;
    }

    public int getPlace() {
        return place;
    }

    public double getExp() {
        return exp;
    }

    public double getPoints() {
        return points;
    }
}