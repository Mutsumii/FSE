package edu.zju.se.management.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.InputStream;

public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {
    }

    public static <T> T read(InputStream in, Class<T> type) throws IOException {
        return MAPPER.readValue(in, type);
    }

    public static byte[] bytes(Object value) throws IOException {
        return MAPPER.writeValueAsBytes(value);
    }

    public static String string(Object value) throws IOException {
        return MAPPER.writeValueAsString(value);
    }

    public static JsonNode tree(String value) throws IOException {
        return MAPPER.readTree(value);
    }
}
