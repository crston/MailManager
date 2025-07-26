package com.gmail.bobason01.mail;

import com.google.gson.*;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Mail 객체와 관련된 직렬화/역직렬화 유틸리티.
 * LocalDateTime과 ItemStack을 Gson에 맞게 처리함.
 */
public class MailSerializer {

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ItemStack.class, new ItemStackAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    /**
     * Mail 객체를 JSON 문자열로 직렬화합니다.
     */
    public static String serializeMail(Mail mail) {
        return gson.toJson(mail);
    }

    /**
     * JSON 문자열을 Mail 객체로 역직렬화합니다.
     */
    public static Mail deserializeMail(String json) {
        try {
            return gson.fromJson(json, Mail.class);
        } catch (JsonParseException e) {
            System.err.println("[MailSerializer] Failed to deserialize Mail: " + e.getMessage());
            return null;
        }
    }

    /**
     * LocalDateTime <-> String (ISO 형식)
     */
    public static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.format(FORMATTER));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            try {
                return LocalDateTime.parse(json.getAsString(), FORMATTER);
            } catch (Exception e) {
                throw new JsonParseException("Invalid LocalDateTime format: " + json.getAsString(), e);
            }
        }
    }

    /**
     * ItemStack <-> Base64 문자열
     */
    public static class ItemStackAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {
        @Override
        public JsonElement serialize(ItemStack item, Type type, JsonSerializationContext context) {
            try (
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ObjectOutputStream oos = new ObjectOutputStream(baos)
            ) {
                oos.writeObject(item);
                oos.flush();
                return new JsonPrimitive(Base64.getEncoder().encodeToString(baos.toByteArray()));
            } catch (IOException e) {
                throw new JsonParseException("Failed to serialize ItemStack", e);
            }
        }

        @Override
        public ItemStack deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            byte[] data = Base64.getDecoder().decode(json.getAsString());
            try (
                    ByteArrayInputStream bais = new ByteArrayInputStream(data);
                    ObjectInputStream ois = new ObjectInputStream(bais)
            ) {
                Object obj = ois.readObject();
                if (obj instanceof ItemStack stack) {
                    return stack;
                } else {
                    throw new JsonParseException("Deserialized object is not an ItemStack");
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new JsonParseException("Failed to deserialize ItemStack", e);
            }
        }
    }
}
