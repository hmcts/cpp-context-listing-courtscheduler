package uk.gov.moj.cpp.courtscheduler.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;

/**
 * Serializes {@link JsonValue} (and subtypes) directly as JSON instead of letting Jackson
 * reflect on their bean properties — which produced shapes like
 * {@code "results":{"integral":true,"valueType":"NUMBER"}} for what the legacy app emitted
 * as {@code "results":1}.
 *
 * <p>Registered on the primary {@link com.fasterxml.jackson.databind.ObjectMapper} so every
 * controller returning a {@link jakarta.json.JsonObject} (directly or nested in a Map) gets
 * the canonical legacy wire shape.</p>
 */
public class JakartaJsonModule extends SimpleModule {

    public JakartaJsonModule() {
        addSerializer(JsonValue.class, new JsonValueSerializer());
        addSerializer(JsonObject.class, new JsonValueSerializer());
        addSerializer(JsonArray.class, new JsonValueSerializer());
        addSerializer(JsonNumber.class, new JsonValueSerializer());
        addSerializer(JsonString.class, new JsonValueSerializer());
    }

    private static final class JsonValueSerializer extends JsonSerializer<JsonValue> {
        @Override
        public void serialize(final JsonValue value,
                              final JsonGenerator gen,
                              final SerializerProvider serializers) throws IOException {
            if (value == null || value == JsonValue.NULL) {
                gen.writeNull();
                return;
            }
            switch (value.getValueType()) {
                case TRUE -> gen.writeBoolean(true);
                case FALSE -> gen.writeBoolean(false);
                case NULL -> gen.writeNull();
                case STRING -> gen.writeString(((JsonString) value).getString());
                case NUMBER -> {
                    final JsonNumber num = (JsonNumber) value;
                    if (num.isIntegral()) {
                        gen.writeNumber(num.bigIntegerValue());
                    } else {
                        gen.writeNumber(num.bigDecimalValue());
                    }
                }
                case OBJECT -> {
                    gen.writeStartObject();
                    final JsonObject obj = (JsonObject) value;
                    for (var entry : obj.entrySet()) {
                        gen.writeFieldName(entry.getKey());
                        serialize(entry.getValue(), gen, serializers);
                    }
                    gen.writeEndObject();
                }
                case ARRAY -> {
                    gen.writeStartArray();
                    final JsonArray arr = (JsonArray) value;
                    for (final JsonValue v : arr) {
                        serialize(v, gen, serializers);
                    }
                    gen.writeEndArray();
                }
            }
        }
    }
}
