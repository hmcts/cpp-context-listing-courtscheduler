package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class ValidateSessionAvailabilityRequestParamConverterTest {
    private static final String COURT_SCHEDULE_ID = "courtScheduleId";
    private static final String UUID_F8254DB1 = "f8254db1-1683-483e-afb3-b87fde5a0a26";


    private final ValidateSessionAvailabilityRequestParamConverter converter =
            new ValidateSessionAvailabilityRequestParamConverter();

    @Test
    void shouldConvertListWithDuration() {
        final JsonObject json = Json.createObjectBuilder()
                .add("courtScheduleIdList", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add(COURT_SCHEDULE_ID, UUID_F8254DB1)))
                .add("duration", 30)
                .build();

        final ValidateSessionAvailabilityRequestParam result = converter.convert(json);

        assertEquals(1, result.courtScheduleIds().size());
        assertEquals(UUID_F8254DB1, result.courtScheduleIds().get(0));
        assertEquals(30, result.slotsOrDuration());
    }

    @Test
    void shouldConvertListWithoutDuration() {
        final JsonObject json = Json.createObjectBuilder()
                .add("courtScheduleIdList", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add(COURT_SCHEDULE_ID, UUID_F8254DB1)))
                .build();

        final ValidateSessionAvailabilityRequestParam result = converter.convert(json);

        assertEquals(1, result.courtScheduleIds().size());
        assertEquals(UUID_F8254DB1, result.courtScheduleIds().get(0));
        assertNull(result.slotsOrDuration());
    }

    @Test
    void shouldConvertMultipleIds() {
        final JsonObject json = Json.createObjectBuilder()
                .add("courtScheduleIdList", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder().add(COURT_SCHEDULE_ID, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                        .add(Json.createObjectBuilder().add(COURT_SCHEDULE_ID, "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")))
                .add("duration", 60)
                .build();

        final ValidateSessionAvailabilityRequestParam result = converter.convert(json);

        assertEquals(2, result.courtScheduleIds().size());
        assertEquals(60, result.slotsOrDuration());
    }
}
