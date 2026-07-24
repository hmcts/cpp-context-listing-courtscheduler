package uk.gov.moj.cpp.courtscheduler.common.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import java.io.IOException;
import org.springframework.stereotype.Component;

/**
 * In-place replacement for {@code uk.gov.justice.services.common.converter.JsonObjectToObjectConverter}.
 * Same API surface — call sites do not change. Backed by Jackson.
 */
@Component
public class JsonObjectToObjectConverter {

    private final ObjectMapper objectMapper;

    public JsonObjectToObjectConverter(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T convert(final JsonObject jsonObject, final Class<T> clazz) {
        if (jsonObject == null) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonObject.toString(), clazz);
        } catch (IOException ioe) {
            throw new IllegalStateException("Failed to convert JsonObject to " + clazz.getSimpleName(), ioe);
        }
    }
}
