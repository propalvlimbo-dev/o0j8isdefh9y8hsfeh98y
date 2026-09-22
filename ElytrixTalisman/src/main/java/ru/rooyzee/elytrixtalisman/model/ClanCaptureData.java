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

    /**
     * Меняет очки клана. Amount может быть отрицательным: при убийстве личные очки
     * жертвы уходят клану убийцы и списываются у клана жертвы.
     *
     * Ниже нуля не опускаемся: жертва могла набрать очки, потом её клан потерял часть
     * в других стычках, и вычет оказался бы больше остатка — топ ушёл бы в минус.
     */
    public void addCapturePoints(int amount) {
        this.capturePoints = Math.max(0, this.capturePoints + amount);
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