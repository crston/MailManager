package com.gmail.bobason01.utils;

import com.gmail.bobason01.lang.LangManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class TimeUtil {

    private static final String[] UNITS = {"year", "month", "day", "hour", "minute", "second"};
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private TimeUtil() {}

    public static String format(Map<String, Integer> timeData, String lang) {
        if (timeData == null || timeData.isEmpty()) {
            return LangManager.get(lang, "time.permanent");
        }

        StringBuilder builder = new StringBuilder(32);
        boolean hasValid = false;

        for (String unit : UNITS) {
            int value = timeData.getOrDefault(unit, 0);
            if (value > 0) {
                hasValid = true;
                builder.append(value)
                        .append(LangManager.get(lang, "time.unit." + unit))
                        .append(' ');
            }
        }

        return hasValid ? builder.toString().trim() : LangManager.get(lang, "time.permanent");
    }

    public static String formatDateTime(long epochMillis, String lang) {
        if (epochMillis <= 0 || epochMillis >= LocalDateTime.now().plusYears(99).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) {
            return LangManager.get(lang, "gui.send.time.no_expire");
        }

        LocalDateTime time = Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return FORMATTER.format(time);
    }
}