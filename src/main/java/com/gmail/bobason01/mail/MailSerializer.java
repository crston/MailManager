package com.gmail.bobason01.mail;

import org.bukkit.Bukkit;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.logging.Level;

public class MailSerializer {

    public static byte[] serialize(Mail mail) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(mail);
            return baos.toByteArray();
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE,
                    "[MailSerializer] Serialization failed for mail " + mail.getMailId(), e);
            return null;
        }
    }

    public static Mail deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            Object obj = ois.readObject();
            if (obj instanceof Mail) {
                return (Mail) obj;
            } else {
                Bukkit.getLogger().severe("[MailSerializer] Deserialized object is not of type Mail.");
                return null;
            }
        } catch (IOException | ClassNotFoundException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[MailSerializer] Deserialization failed.", e);
            return null;
        }
    }
}
