package com.gmail.bobason01.mail;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class MailSerializer {

    // Mail 직렬화
    public static byte[] serialize(Mail mail) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {

            out.writeLong(mail.getMailId().getMostSignificantBits());
            out.writeLong(mail.getMailId().getLeastSignificantBits());

            UUID sender = mail.getSender();
            out.writeBoolean(sender != null);
            if (sender != null) {
                out.writeLong(sender.getMostSignificantBits());
                out.writeLong(sender.getLeastSignificantBits());
            }

            UUID receiver = mail.getReceiver();
            out.writeLong(receiver.getMostSignificantBits());
            out.writeLong(receiver.getLeastSignificantBits());

            out.writeLong(mail.getSentAt().toEpochSecond(ZoneOffset.UTC));
            out.writeLong(mail.getExpireAt() != null ? mail.getExpireAt().toEpochSecond(ZoneOffset.UTC) : -1);

            List<ItemStack> items = mail.getItems();
            out.writeInt(items.size());
            for (ItemStack item : items) {
                if (item == null) {
                    out.writeInt(0);
                } else {
                    byte[] data = serializeItem(item);
                    out.writeInt(data.length);
                    out.write(data);
                }
            }

            return baos.toByteArray();
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[MailSerializer] Serialization failed: " + mail.getMailId(), e);
            return new byte[0];
        }
    }

    // Mail 역직렬화
    public static Mail deserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            UUID mailId = new UUID(in.readLong(), in.readLong());

            UUID sender = null;
            if (in.readBoolean()) {
                sender = new UUID(in.readLong(), in.readLong());
            }

            UUID receiver = new UUID(in.readLong(), in.readLong());

            long sent = in.readLong();
            long expire = in.readLong();

            int size = in.readInt();
            List<ItemStack> items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int len = in.readInt();
                if (len > 0) {
                    byte[] data = new byte[len];
                    in.readFully(data);
                    ItemStack item = deserializeItem(data);
                    if (item != null) items.add(item);
                }
            }

            return new Mail(mailId, sender, receiver, items,
                    LocalDateTime.ofEpochSecond(sent, 0, ZoneOffset.UTC),
                    expire == -1 ? null : LocalDateTime.ofEpochSecond(expire, 0, ZoneOffset.UTC));
        } catch (IOException e) {
            Bukkit.getLogger().log(Level.SEVERE, "[MailSerializer] Deserialization failed.", e);
            return null;
        }
    }

    // ItemStack 직렬화
    private static byte[] serializeItem(ItemStack item) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeObject(item);
            return baos.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    // ItemStack 역직렬화
    private static ItemStack deserializeItem(byte[] data) {
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return (ItemStack) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            return null;
        }
    }
}
