package com.gmail.bobason01.database;

import com.gmail.bobason01.mail.Mail;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MailStorage {

    // 데이터베이스 연결 및 종료
    void connect() throws Exception;
    void disconnect() throws Exception;

    // 테이블 생성 등 스키마 초기화
    void ensureSchema() throws Exception;

    // 메일 관련 메서드
    List<Mail> loadMails(UUID receiver) throws Exception;

    // 배치 처리를 위한 삽입/삭제
    void batchInsertMails(List<MailRecord> records) throws Exception;
    void batchDeleteMails(List<MailRecord> records) throws Exception;

    // 특정 플레이어의 모든 메일 삭제 (초기화용)
    void deletePlayerMails(UUID receiver) throws Exception;

    // 설정 관련 메서드
    void saveNotifySetting(UUID uuid, boolean enabled) throws Exception;
    Boolean loadNotifySetting(UUID uuid) throws Exception;

    void saveBlacklist(UUID owner, Set<UUID> list) throws Exception;
    Set<UUID> loadBlacklist(UUID owner) throws Exception;

    void saveExclude(UUID owner, Set<UUID> list) throws Exception;
    Set<UUID> loadExclude(UUID owner) throws Exception;

    void savePlayerLanguage(UUID uuid, String lang) throws Exception;
    String loadPlayerLanguage(UUID uuid) throws Exception;

    // 인벤토리 백업/복구 (GUI 임시 저장 등)
    void saveInventory(int id, ItemStack[] contents) throws Exception;
    ItemStack[] loadInventory(int id) throws Exception;

    // 글로벌 플레이어 데이터 (닉네임/UUID 매핑)
    void updateGlobalPlayer(UUID uuid, String name) throws Exception;
    UUID lookupGlobalUUID(String name) throws Exception;
    String lookupGlobalName(UUID uuid) throws Exception;
    Set<UUID> getAllGlobalUUIDs() throws Exception;

    // 데이터 전송을 위한 레코드 (Java 16+ Record 사용)
    // 만약 Java 14 미만 버전을 사용 중이라면 일반 static class로 변경해야 합니다.
    record MailRecord(UUID receiver, Mail mail) {}
}