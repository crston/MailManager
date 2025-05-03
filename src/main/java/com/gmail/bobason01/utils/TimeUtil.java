package com.gmail.bobason01.utils;

import java.util.Map;
import java.util.StringJoiner;

public class TimeUtil {

    private static final String[] UNIT_KEYS = {"year", "month", "day", "hour", "minute", "second"};

    public static String format(Map<String, Integer> timeData) {
        if (timeData == null || timeData.isEmpty()) {
            return LangUtil.get("format.permanent");
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (String unit : UNIT_KEYS) {
            int value = timeData.getOrDefault(unit, 0);
            if (value > 0) {
                String template = LangUtil.get("format.unit." + unit);
                String label = template.replace("{value}", String.valueOf(value));
                joiner.add(label);
            }
        }

        String result = joiner.toString();
        return result.isEmpty() ? LangUtil.get("format.permanent") : result;
    }
}
