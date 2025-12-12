package com.gmail.bobason01.database;

import com.gmail.bobason01.mail.Mail;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface MailStorage {

    // 배치 처리를 위한 레코드 클래스
    record MailRecord(UUID receiver, Mail mail) {}

    // 연결 및 종료
    void connect() throws Exception;
    void disconnect() throws Exception;
    void ensureSchema() throws Exception;

    // 메일 로직
    List<Mail> loadMails(UUID receiver) throws Exception;
    void batchInsertMails(List<MailRecord> list) throws Exception;
    void batchDeleteMails(List<MailRecord> list) throws Exception;

    // [초기화] 특정 플레이어의 모든 메일 삭제
    void deletePlayerMails(UUID receiver) throws Exception;

    // 설정 로직
    void saveNotifySetting(UUID uuid, boolean enabled) throws Exception;
    Boolean loadNotifySetting(UUID uuid) throws Exception;

    void saveBlacklist(UUID owner, Set<UUID> list) throws Exception;
    Set<UUID> loadBlacklist(UUID owner) throws Exception;

    void saveExclude(UUID owner, Set<UUID> list) throws Exception;
    Set<UUID> loadExclude(UUID owner) throws Exception;

    void savePlayerLanguage(UUID uuid, String lang) throws Exception;
    String loadPlayerLanguage(UUID uuid) throws Exception;

    // 가상 인벤토리
    void saveInventory(int id, ItemStack[] contents) throws Exception;
    ItemStack[] loadInventory(int id) throws Exception;

    // 멀티 서버 지원 (글로벌 플레이어 정보)
    void updateGlobalPlayer(UUID uuid, String name) throws Exception;
    UUID lookupGlobalUUID(String name) throws Exception;
    String lookupGlobalName(UUID uuid) throws Exception;

    // [전체 발송용] 모든 글로벌 유저 UUID 가져오기
    Set<UUID> getAllGlobalUUIDs() throws Exception;
}