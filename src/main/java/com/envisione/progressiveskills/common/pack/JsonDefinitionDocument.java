package com.envisione.progressiveskills.common.pack;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class JsonDefinitionDocument {
    private JsonDefinitionDocument() {
    }

    public static Map<String, Object> read(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) > SourceBundle.MAX_FILE_BYTES) {
            throw new IllegalArgumentException("JSON source is not a bounded regular file");
        }
        byte[] bytes = Files.readAllBytes(path);
        try (JsonReader reader = new JsonReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
            reader.setLenient(false);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new IllegalArgumentException("JSON definition root must be an object");
            }
            Map<String, Object> result = object(reader, 0, new Counter());
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new IllegalArgumentException("JSON definition contains trailing data");
            }
            return result;
        } catch (IOException exception) {
            throw new IllegalArgumentException("JSON definition syntax is invalid", exception);
        }
    }

    private static Map<String, Object> object(JsonReader reader, int depth, Counter counter) throws IOException {
        requireBudget(depth, counter);
        var result = new LinkedHashMap<String, Object>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            Object value = value(reader, depth + 1, counter);
            if (result.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("JSON definition contains a duplicate key");
            }
        }
        reader.endObject();
        return result;
    }

    private static Object value(JsonReader reader, int depth, Counter counter) throws IOException {
        requireBudget(depth, counter);
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> object(reader, depth, counter);
            case BEGIN_ARRAY -> array(reader, depth, counter);
            case BOOLEAN -> reader.nextBoolean();
            case STRING -> reader.nextString();
            case NUMBER -> number(reader.nextString());
            case NULL -> throw new IllegalArgumentException("JSON definition null values are not supported");
            default -> throw new IllegalArgumentException("JSON definition contains an invalid value");
        };
    }

    private static List<Object> array(JsonReader reader, int depth, Counter counter) throws IOException {
        reader.beginArray();
        var result = new ArrayList<Object>();
        while (reader.hasNext()) {
            result.add(value(reader, depth + 1, counter));
        }
        reader.endArray();
        return result;
    }

    private static Object number(String value) {
        if (value.length() > 128) {
            throw new IllegalArgumentException("JSON definition number exceeds its text bound");
        }
        BigDecimal number = new BigDecimal(value);
        if (number.precision() > 128 || Math.abs((long) number.scale()) > 1024L) {
            throw new IllegalArgumentException("JSON definition number exceeds its precision bound");
        }
        return number.scale() <= 0 ? number.longValueExact() : number;
    }

    private static void requireBudget(int depth, Counter counter) {
        counter.nodes++;
        if (depth > TomlDocument.MAX_DEPTH || counter.nodes > TomlDocument.MAX_NODES) {
            throw new IllegalArgumentException("JSON definition exceeds its structure budget");
        }
    }

    private static final class Counter {
        private int nodes;
    }
}
