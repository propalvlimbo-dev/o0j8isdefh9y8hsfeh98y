package ru.rooyzee.elytrixtalisman.model;

public class ScheduleEntry {

    private final int hour;
    private final int minute;

    public ScheduleEntry(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
    }

    public int getHour() {
        return hour;
    }

    public int getMinute() {
        return minute;
    }
}