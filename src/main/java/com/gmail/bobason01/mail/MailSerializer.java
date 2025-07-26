package com.gmail.bobason01.mail;

import com.google.gson.*;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Mail 객체를 JSON 문자열로 저장하거나 불러오기 위한 유틸리티 클래스입니다.
 * LocalDateTime 및 ItemStack을 Gson에 맞춰 커스텀 직렬화/역직렬화 합니다.
 */
public class MailSerializer {

    // Gson 인스턴스: ItemStack과 LocalDateTime을 처리하기 위한 어댑터 등록
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ItemStack.class, new ItemStackAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    /**
     * Mail 객체를 JSON 문자열로 직렬화합니다.
     *
     * @param mail 직렬화할 Mail 객체
     * @return JSON 문자열
     */
    public static String serializeMail(Mail mail) {
        return gson.toJson(mail);
    }

    /**
     * JSON 문자열을 Mail 객체로 역직렬화합니다.
     *
     * @param json Mail JSON 문자열
     * @return 역직렬화된 Mail 객체 또는 실패 시 null
     */
    public static Mail deserializeMail(String json) {
        try {
            return gson.fromJson(json, Mail.class);
        } catch (JsonParseException e) {
            System.err.println("[MailSerializer] Mail 역직렬화 실패: " + e.getMessage());
            return null;
        }
    }

    /**
     * LocalDateTime <-> 문자열(ISO 형식) 변환 어댑터
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
                throw new JsonParseException("LocalDateTime 형식이 잘못되었습니다: " + json.getAsString(), e);
            }
        }
    }

    /**
     * ItemStack <-> Base64 문자열 변환 어댑터
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
                throw new JsonParseException("ItemStack 직렬화 실패", e);
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
                    throw new JsonParseException("복원된 객체가 ItemStack이 아닙니다.");
                }
            } catch (IOException | ClassNotFoundException e) {
                throw new JsonParseException("ItemStack 역직렬화 실패", e);
            }
        }
    }
}
