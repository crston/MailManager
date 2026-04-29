package com.gmail.bobason01.mail;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailSerializer {

    public static byte[] serialize(Mail mail) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {

            out.writeLong(mail.getMailId().getMostSignificantBits());
            out.writeLong(mail.getMailId().getLeastSignificantBits());
            out.writeLong(mail.getSender().getMostSignificantBits());
            out.writeLong(mail.getSender().getLeastSignificantBits());
            out.writeLong(mail.getSentAt().toEpochSecond(ZoneOffset.UTC));
            out.writeLong(mail.getExpireAt() != null ? mail.getExpireAt().toEpochSecond(ZoneOffset.UTC) : -1);

            List<ItemStack> items = mail.getItems();
            out.writeInt(items.size());
            for (ItemStack item : items) {
                byte[] itemData = serializeItem(item);
                out.writeInt(itemData.length);
                out.write(itemData);
            }
            return baos.toByteArray();
        }
    }

    public static Mail deserialize(byte[] data, UUID receiver) throws IOException, ClassNotFoundException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            UUID id = new UUID(in.readLong(), in.readLong());
            UUID sender = new UUID(in.readLong(), in.readLong());
            long sent = in.readLong();
            long expire = in.readLong();

            int size = in.readInt();
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                byte[] itemBytes = new byte[in.readInt()];
                in.readFully(itemBytes);
                items.add(deserializeItem(itemBytes));
            }
            return new Mail(id, sender, receiver, items,
                    LocalDateTime.ofEpochSecond(sent, 0, ZoneOffset.UTC),
                    expire == -1 ? null : LocalDateTime.ofEpochSecond(expire, 0, ZoneOffset.UTC));
        }
    }

    private static byte[] serializeItem(ItemStack item) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(bos)) {
            oos.writeObject(item);
            return bos.toByteArray();
        }
    }

    private static ItemStack deserializeItem(byte[] data) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream ois = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return (ItemStack) ois.readObject();
        }
    }
}