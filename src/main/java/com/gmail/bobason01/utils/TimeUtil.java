package com.gmail.bobason01.utils;

import java.util.Map;
import java.util.StringJoiner;

public class TimeUtil {

    private static final String[] UNITS = {"year", "month", "day", "hour", "minute", "second"};

    /**
     * 시간 데이터 맵을 읽어 사용자에게 표시할 수 있는 포맷으로 변환합니다.
     *
     * @param timeData 시간 단위를 키로 하고, 정수를 값으로 갖는 Map
     * @return "1일 3시간" 형식의 문자열. 유효 데이터가 없으면 "무제한" 반환
     */
    public static String format(Map<String, Integer> timeData) {
        if (timeData == null || timeData.isEmpty()) {
            return LangUtil.get("format.permanent");
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (String unit : UNITS) {
            int value = timeData.getOrDefault(unit, 0);
            if (value > 0) {
                String formatKey = "format.unit." + unit;
                String unitText = LangUtil.get(formatKey).replace("{value}", String.valueOf(value));
                joiner.add(unitText);
            }
        }

        return joiner.length() == 0 ? LangUtil.get("format.permanent") : joiner.toString();
    }
}
