package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class ValidateSessionAvailabilityRequestParamConverterTest {

    private final ValidateSessionAvailabilityRequestParamConverter converter =
            new ValidateSessionAvailabilityRequestParamConverter();

    @Test
    void shouldConvertListWithDuration() {
        JsonObject json = Json.createObjectBuilder()
                .add("courtScheduleIdList", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("courtScheduleId", "f8254db1-1683-483e-afb3-b87fde5a0a26")))
                .add("duration", 30)
                .build();

        ValidateSessionAvailabilityRequestParam result = converter.convert(json);

        assertEquals(1, result.courtScheduleIds().size());
        assertEquals("f8254db1-1683-483e-afb3-b87fde5a0a26", result.courtScheduleIds().get(0));
        assertEquals(30, result.slotsOrDuration());
    }

    @Test
    void shouldConvertListWithoutDuration() {
        JsonObject json = Json.createObjectBuilder()
                .add("courtScheduleIdList", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("courtScheduleId", "f8254db1-1683-483e-afb3-b87fde5a0a26")))
                .build();

        ValidateSessionAvailabilityRequestParam result = converter.convert(json);

        assertEquals(1, result.courtScheduleIds().size());
        assertEquals("f8254db1-1683-483e-afb3-b87fde5a0a26", result.courtScheduleIds().get(0));
        assertNull(result.slotsOrDuration());
    }

    @Test
    void shouldConvertMultipleIds() {
        JsonObject json = Json.createObjectBuilder()
                .add("courtScheduleIdList", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add("courtScheduleId", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                        .add(Json.createObjectBuilder().add("courtScheduleId", "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")))
                .add("duration", 60)
                .build();

        ValidateSessionAvailabilityRequestParam result = converter.convert(json);

        assertEquals(2, result.courtScheduleIds().size());
        assertEquals(60, result.slotsOrDuration());
    }
}
