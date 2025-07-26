package com.gmail.bobason01.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 각 플레이어별 개인 데이터 (블랙리스트, 설정 등) 저장용.
 */
public class PlayerData {

    private static final Map<UUID, PlayerData> DATA = new ConcurrentHashMap<>();

    /**
     * 플레이어 UUID에 해당하는 데이터를 반환합니다. 없으면 생성합니다.
     */
    public static PlayerData get(UUID uuid) {
        return DATA.computeIfAbsent(uuid, k -> new PlayerData());
    }

    /**
     * 데이터가 존재하는지 확인합니다.
     */
    public static boolean has(UUID uuid) {
        return DATA.containsKey(uuid);
    }

    /**
     * 특정 플레이어의 데이터를 제거합니다.
     */
    public static void remove(UUID uuid) {
        DATA.remove(uuid);
    }

    // ====== 블랙리스트 관련 ======

    private final Set<UUID> blacklist = ConcurrentHashMap.newKeySet();

    /**
     * 블랙리스트에 등록된 UUID 목록을 반환합니다. (읽기 전용)
     */
    public Set<UUID> getBlacklist() {
        return Collections.unmodifiableSet(blacklist);
    }

    /**
     * 해당 UUID를 블랙리스트에 추가합니다.
     */
    public void addToBlacklist(UUID target) {
        blacklist.add(target);
    }

    /**
     * 해당 UUID를 블랙리스트에서 제거합니다.
     */
    public void removeFromBlacklist(UUID target) {
        blacklist.remove(target);
    }

    /**
     * 해당 UUID가 블랙리스트에 포함되어 있는지 확인합니다.
     */
    public boolean isBlacklisted(UUID target) {
        return blacklist.contains(target);
    }

    // ====== GUI 설정: 읽은 메일 숨기기 ======

    private boolean hideReadMail;

    public boolean isHideReadMailEnabled() {
        return hideReadMail;
    }

    public void setHideReadMailEnabled(boolean hide) {
        this.hideReadMail = hide;
    }

    // ====== GUI 설정: 알림 끄기 ======

    private boolean muteMailNotification;

    public boolean isNotificationMuted() {
        return muteMailNotification;
    }

    public void setNotificationMuted(boolean mute) {
        this.muteMailNotification = mute;
    }
}
