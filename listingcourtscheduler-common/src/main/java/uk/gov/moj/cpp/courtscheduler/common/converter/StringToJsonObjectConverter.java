package uk.gov.moj.cpp.courtscheduler.common.converter;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import org.springframework.stereotype.Component;

/**
 * In-place replacement for {@code uk.gov.justice.services.common.converter.StringToJsonObjectConverter}.
 * Same API surface — call sites do not change. Backed by JSR-353 (jakarta.json).
 */
@Component
public class StringToJsonObjectConverter {

    public JsonObject convert(final String json) {
        if (json == null || json.isBlank()) {
            return Json.createObjectBuilder().build();
        }
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }
}
