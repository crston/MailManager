package com.gmail.bobason01.mail;

import org.bukkit.Bukkit;

import java.io.*;

public class MailSerializer {

    /**
     * Mail 객체를 byte[]로 직렬화합니다.
     * @param mail 직렬화할 Mail 객체
     * @return 직렬화된 byte 배열, 실패 시 null
     */
    public static byte[] serialize(Mail mail) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(mail);
            return baos.toByteArray();
        } catch (IOException e) {
            Bukkit.getLogger().severe("[MailSerializer] Serialization failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * byte[]를 Mail 객체로 역직렬화합니다.
     * @param bytes 직렬화된 데이터
     * @return 복원된 Mail 객체, 실패 시 null
     */
    public static Mail deserialize(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            Object obj = ois.readObject();
            if (obj instanceof Mail mail) {
                return mail;
            } else {
                Bukkit.getLogger().severe("[MailSerializer] Deserialized object is not Mail");
                return null;
            }
        } catch (IOException | ClassNotFoundException e) {
            Bukkit.getLogger().severe("[MailSerializer] Deserialization failed: " + e.getMessage());
            return null;
        }
    }
}
