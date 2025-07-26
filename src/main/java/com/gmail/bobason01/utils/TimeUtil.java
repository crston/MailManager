package com.gmail.bobason01.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class TimeUtil {

    private static final String[] UNITS = {"year", "month", "day", "hour", "minute", "second"};
    private static final String[] UNIT_LABELS = {"year", "month", "day", "hour", "minute", "second"};

    public static String format(Map<String, Integer> timeData) {
        if (timeData == null || timeData.isEmpty()) {
            return "permanent";
        }

        StringBuilder builder = new StringBuilder();
        boolean hasValid = false;

        for (int i = 0; i < UNITS.length; i++) {
            String unit = UNITS[i];
            int value = timeData.getOrDefault(unit, 0);
            if (value > 0) {
                hasValid = true;
                builder.append(value).append(" ").append(UNIT_LABELS[i]);
                if (value > 1) builder.append("s"); // plural
                builder.append(" ");
            }
        }

        return hasValid ? builder.toString().trim() : "permanent";
    }

    public static String formatDateTime(long epochMillis) {
        if (epochMillis <= 0) return "permanent";

        LocalDateTime time = Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
