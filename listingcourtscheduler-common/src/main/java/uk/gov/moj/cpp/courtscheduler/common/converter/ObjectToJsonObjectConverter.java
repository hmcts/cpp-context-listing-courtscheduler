package uk.gov.moj.cpp.courtscheduler.common.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import org.springframework.stereotype.Component;

/**
 * In-place replacement for {@code uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter}.
 * Same API surface — call sites do not change. Backed by Jackson + jakarta.json.
 */
@Component
public class ObjectToJsonObjectConverter {

    private final ObjectMapper objectMapper;

    public ObjectToJsonObjectConverter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonObject convert(final Object source) {
        if (source == null) {
            return Json.createObjectBuilder().build();
        }
        try {
            final String json = objectMapper.writeValueAsString(source);
            try (var reader = Json.createReader(new StringReader(json))) {
                return reader.readObject();
            }
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to convert object to JsonObject", ioe);
        }
    }
}
