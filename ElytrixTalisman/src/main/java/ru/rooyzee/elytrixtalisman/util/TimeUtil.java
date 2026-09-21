package ru.rooyzee.elytrixtalisman.util;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimeUtil {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    public static long secondsUntil(int hour, int minute) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        ZonedDateTime target = ZonedDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute), ZONE);
        if (now.isAfter(target)) {
            target = target.plusDays(1);
        }
        return Duration.between(now, target).getSeconds();
    }
}