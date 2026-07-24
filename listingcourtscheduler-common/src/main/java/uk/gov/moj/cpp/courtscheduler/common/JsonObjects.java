package uk.gov.moj.cpp.courtscheduler.common;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.util.Optional;

/**
 * Minimal in-place replacement for {@code uk.gov.justice.services.messaging.JsonObjects}
 * — only the methods used by this codebase are exposed. Removes the dependency on
 * the Justice Services messaging library.
 */
public final class JsonObjects {

    private JsonObjects() { }

    public static Optional<JsonArray> getJsonArray(final JsonObject obj, final String key) {
        if (obj == null || !obj.containsKey(key) || obj.isNull(key)) {
            return Optional.empty();
        }
        final JsonValue value = obj.get(key);
        return value.getValueType() == JsonValue.ValueType.ARRAY
                ? Optional.of(value.asJsonArray())
                : Optional.empty();
    }

    public static Optional<JsonObject> getJsonObject(final JsonObject obj, final String key) {
        if (obj == null || !obj.containsKey(key) || obj.isNull(key)) {
            return Optional.empty();
        }
        final JsonValue value = obj.get(key);
        return value.getValueType() == JsonValue.ValueType.OBJECT
                ? Optional.of(value.asJsonObject())
                : Optional.empty();
    }

    public static Optional<String> getString(final JsonObject obj, final String key) {
        if (obj == null || !obj.containsKey(key) || obj.isNull(key)) {
            return Optional.empty();
        }
        return Optional.ofNullable(obj.getString(key, null));
    }
}
