package uk.gov.moj.cpp.courtscheduler.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CourtScheduleJsonTest {

    // Mirrors the api module's JacksonObjectMapperConfig.
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Instant EXPECTED = Instant.parse("2026-09-28T09:30:00Z");

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-09-28T09:30:00.000+00:00",
            "2026-09-28T09:30:00.000Z",
            "2026-09-28T09:30:00Z",
            "2026-09-28T10:30:00.000+01:00"
    })
    void shouldDeserializeInstantFieldsInAnyIsoOffsetForm(final String timestamp) throws Exception {
        final String json = """
                {"createdOn":"%1$s","updatedOn":"%1$s","sessionStartTime":"%1$s",
                 "sessionEndTime":"%1$s","nationalBreakTime":"%1$s"}""".formatted(timestamp);

        final CourtSchedule courtSchedule = objectMapper.readValue(json, CourtSchedule.class);

        assertEquals(EXPECTED, courtSchedule.getCreatedOn());
        assertEquals(EXPECTED, courtSchedule.getUpdatedOn());
        assertEquals(EXPECTED, courtSchedule.getSessionStartTime());
        assertEquals(EXPECTED, courtSchedule.getSessionEndTime());
        assertEquals(EXPECTED, courtSchedule.getNationalBreakTime());
    }

    @Test
    void shouldSerializeInstantFieldsWithMillisAndNumericUtcOffset() throws Exception {
        final CourtSchedule courtSchedule = new CourtSchedule();
        courtSchedule.setCreatedOn(EXPECTED);
        courtSchedule.setUpdatedOn(EXPECTED);
        courtSchedule.setSessionStartTime(EXPECTED);
        courtSchedule.setSessionEndTime(EXPECTED);
        courtSchedule.setNationalBreakTime(EXPECTED);

        final JsonNode json = objectMapper.valueToTree(courtSchedule);

        for (final String field : new String[]{"createdOn", "updatedOn", "sessionStartTime", "sessionEndTime", "nationalBreakTime"}) {
            assertEquals("2026-09-28T09:30:00.000+00:00", json.get(field).asText(), field);
        }
    }
}
