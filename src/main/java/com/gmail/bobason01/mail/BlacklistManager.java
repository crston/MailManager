package com.gmail.bobason01.mail;

import java.util.*;

public class BlacklistManager {

    // 각 플레이어(UUID)가 차단한 대상 UUID 목록
    private static final Map<UUID, Set<UUID>> blacklistMap = new HashMap<>();

    /**
     * 차단 목록에 대상 추가
     */
    public static void add(UUID owner, UUID target) {
        blacklistMap.computeIfAbsent(owner, k -> new HashSet<>()).add(target);
    }

    /**
     * 차단 목록에서 대상 제거
     */
    public static void remove(UUID owner, UUID target) {
        Set<UUID> list = blacklistMap.get(owner);
        if (list != null) {
            list.remove(target);
            if (list.isEmpty()) blacklistMap.remove(owner);
        }
    }

    /**
     * 차단 여부 확인
     */
    public static boolean isBlocked(UUID owner, UUID target) {
        return blacklistMap.getOrDefault(owner, Collections.emptySet()).contains(target);
    }

    /**
     * 대상이 차단되어 있다면 제거, 아니라면 추가 (토글)
     */
    public static void toggle(UUID owner, UUID target) {
        if (isBlocked(owner, target)) {
            remove(owner, target);
        } else {
            add(owner, target);
        }
    }

    /**
     * 특정 플레이어의 차단 목록 가져오기 (읽기 전용)
     */
    public static Set<UUID> getBlockedList(UUID owner) {
        return Collections.unmodifiableSet(blacklistMap.getOrDefault(owner, Collections.emptySet()));
    }

    /**
     * 특정 플레이어의 차단 목록 초기화
     */
    public static void clear(UUID owner) {
        blacklistMap.remove(owner);
    }
}
