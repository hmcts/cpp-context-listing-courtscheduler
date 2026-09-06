package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class HearingSlotSearchRequestConverterTest {

    private final HearingSlotSearchRequestConverter converter = new HearingSlotSearchRequestConverter();

    @Test
    public void shouldConvertAllFieldsIncludingBusinessType() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("hearingId", "5771a96b-1c5a-45d1-b647-1bec5212cafc")
                .add("courtCentreId", "B01LY00")
                .add("courtRoomId", "room-1")
                .add("hearingDate", "2025-05-13")
                .add("hearingSessionDateSearchCutOff", "2025-05-20")
                .add("hearingStartTime", "2025-05-13T10:00:00Z")
                .add("durationInMinutes", 20)
                .add("isPolice", true)
                .add("businessType", "ENF_AUTO")
                .build();

        final HearingSlotSearchRequest request = converter.convert(jsonObject);

        assertEquals("5771a96b-1c5a-45d1-b647-1bec5212cafc", request.hearingId());
        assertEquals("B01LY00", request.courtCentreId());
        assertEquals("room-1", request.courtRoomId());
        assertEquals("2025-05-13", request.hearingSessionDate());
        assertEquals("2025-05-20", request.hearingSessionDateSearchCutOff());
        assertEquals("2025-05-13T10:00:00Z", request.sessionStartTime());
        assertEquals(20, request.durationInMinutes());
        assertEquals(true, request.isPolice());
        assertEquals("ENF_AUTO", request.businessType());
    }

    @Test
    public void shouldDefaultBusinessTypeAndIsPoliceWhenAbsent() {
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("hearingId", "5771a96b-1c5a-45d1-b647-1bec5212cafc")
                .add("courtCentreId", "B01LY00")
                .add("hearingDate", "2025-05-13")
                .add("durationInMinutes", 20)
                .build();

        final HearingSlotSearchRequest request = converter.convert(jsonObject);

        assertNull(request.businessType());
        assertFalse(request.isPolice());
    }
}
