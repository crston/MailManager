package com.gmail.bobason01.mail;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 플레이어별 메일 차단 목록 관리.
 */
public class BlacklistManager {

    // 각 플레이어(UUID)가 차단한 대상 UUID 목록
    private static final Map<UUID, Set<UUID>> blacklistMap = new ConcurrentHashMap<>();

    // ===== 추가/제거 =====

    /**
     * 차단 대상 추가
     */
    public static void add(UUID owner, UUID target) {
        getOrCreate(owner).add(target);
    }

    /**
     * 차단 대상 제거
     */
    public static void remove(UUID owner, UUID target) {
        Set<UUID> set = blacklistMap.get(owner);
        if (set != null) {
            set.remove(target);
            if (set.isEmpty()) {
                blacklistMap.remove(owner);
            }
        }
    }

    /**
     * 차단 상태 토글 (차단 ⇄ 해제)
     */
    public static void toggle(UUID owner, UUID target) {
        if (isBlocked(owner, target)) {
            remove(owner, target);
        } else {
            add(owner, target);
        }
    }

    // ===== 조회 =====

    /**
     * 대상이 차단되었는지 확인
     */
    public static boolean isBlocked(UUID owner, UUID target) {
        return blacklistMap.getOrDefault(owner, Collections.emptySet()).contains(target);
    }

    /**
     * 차단 목록 조회 (읽기 전용)
     */
    public static Set<UUID> getBlacklist(UUID owner) {
        return Collections.unmodifiableSet(blacklistMap.getOrDefault(owner, Collections.emptySet()));
    }

    /**
     * 차단 목록이 비어 있는지 확인
     */
    public static boolean isEmpty(UUID owner) {
        Set<UUID> list = blacklistMap.get(owner);
        return list == null || list.isEmpty();
    }

    /**
     * 특정 플레이어의 차단 목록 초기화
     */
    public static void clear(UUID owner) {
        blacklistMap.remove(owner);
    }

    /**
     * 전체 차단 목록 초기화 (주의: 관리자 용도)
     */
    public static void clearAll() {
        blacklistMap.clear();
    }

    // ===== 내부 유틸 =====

    private static Set<UUID> getOrCreate(UUID owner) {
        return blacklistMap.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet());
    }
}
