package uk.gov.moj.cpp.courtscheduler.config;

import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep-converts {@link JsonValue} graphs into plain Java types ({@link Map}, {@link List},
 * {@link String}, {@link Number}, {@link Boolean}, {@code null}) so Spring Boot 4's Jackson 3
 * HTTP converter renders the canonical legacy wire shape — instead of reflecting on
 * {@link JsonNumber}'s bean properties and emitting {@code {"integral":true,"valueType":"NUMBER"}}.
 */
public final class JsonValueConverter {

    private JsonValueConverter() {
    }

    public static Map<String, Object> toMap(final JsonObject obj) {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (obj == null) {
            return out;
        }
        for (final Map.Entry<String, JsonValue> entry : obj.entrySet()) {
            out.put(entry.getKey(), toJava(entry.getValue()));
        }
        return out;
    }

    public static List<Object> toList(final JsonArray arr) {
        final List<Object> out = new ArrayList<>();
        if (arr == null) {
            return out;
        }
        for (final JsonValue v : arr) {
            out.add(toJava(v));
        }
        return out;
    }

    public static Object toJava(final JsonValue value) {
        if (value == null) {
            return null;
        }
        return switch (value.getValueType()) {
            case NULL -> null;
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case STRING -> ((JsonString) value).getString();
            case NUMBER -> {
                final JsonNumber num = (JsonNumber) value;
                yield num.isIntegral() ? num.bigIntegerValue() : num.bigDecimalValue();
            }
            case OBJECT -> toMap((JsonObject) value);
            case ARRAY -> toList((JsonArray) value);
        };
    }
}
