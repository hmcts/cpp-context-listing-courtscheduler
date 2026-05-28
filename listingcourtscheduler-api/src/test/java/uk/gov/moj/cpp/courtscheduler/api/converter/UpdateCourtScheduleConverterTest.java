package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;

import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateCourtScheduleConverterTest {

    @InjectMocks
    private UpdateCourtScheduleConverter updateCourtScheduleConverter;

    @Test
    void shouldConvertJsonObject_ToUpdateCourtSchedule() {

        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("courtScheduleId", UUID.randomUUID().toString())
                .add("courtRoomId", "2")
                .add("businessType", "BusType")
                .add("courtSession", "AM")
                .add("panel", "ADULT")
                .add("jurisdiction", "MAGISTRATES")
                .add("maxSlots", 1)
                .add("maxDuration", 1)
                .add("sessionStartTime", "11:00")
                .add("sessionEndTime", "17:00")
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals("2", updateCourtSchedule.getCourtRoomId());
        assertEquals("11:00", updateCourtSchedule.getSessionStartTime());
        assertEquals("17:00", updateCourtSchedule.getSessionEndTime());
        assertEquals("MAGISTRATES", updateCourtSchedule.getJurisdiction());
    }

    @Test
    void shouldConvertJsonObject_WithCrownJurisdictionAndIsDraft() {

        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("courtScheduleId", UUID.randomUUID().toString())
                .add("courtRoomId", "2")
                .add("businessType", "BusType")
                .add("courtSession", "AM")
                .add("panel", "ADULT")
                .add("jurisdiction", "CROWN")
                .add("isDraft", true)
                .add("maxSlots", 1)
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals("CROWN", updateCourtSchedule.getJurisdiction());
        assertEquals(Boolean.TRUE, updateCourtSchedule.getIsDraft());
    }

    @Test
    void shouldConvertJsonObject_WithoutIsDraft() {

        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("courtScheduleId", UUID.randomUUID().toString())
                .add("courtRoomId", "2")
                .add("businessType", "BusType")
                .add("courtSession", "AM")
                .add("panel", "ADULT")
                .add("jurisdiction", "MAGISTRATES")
                .add("maxSlots", 1)
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals("MAGISTRATES", updateCourtSchedule.getJurisdiction());
        assertNull(updateCourtSchedule.getIsDraft());
    }

    @Test
    void shouldConvertJsonObject_WithCrownJurisdictionWithoutPanel() {
        // Panel is optional for CROWN jurisdiction
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("courtScheduleId", UUID.randomUUID().toString())
                .add("courtRoomId", "2")
                .add("businessType", "BusType")
                .add("courtSession", "AM")
                .add("jurisdiction", "CROWN")
                .add("isDraft", true)
                .add("maxSlots", 1)
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals("CROWN", updateCourtSchedule.getJurisdiction());
        assertNull(updateCourtSchedule.getPanel()); // Panel should be null when not supplied
    }

    @Test
    void shouldConvertJsonObject_WithCrownJurisdictionAndAdultPanel() {
        // CROWN with ADULT panel should work
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("courtScheduleId", UUID.randomUUID().toString())
                .add("courtRoomId", "2")
                .add("businessType", "BusType")
                .add("courtSession", "AM")
                .add("panel", "ADULT")
                .add("jurisdiction", "CROWN")
                .add("isDraft", true)
                .add("maxSlots", 1)
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals("CROWN", updateCourtSchedule.getJurisdiction());
        assertEquals("ADULT", updateCourtSchedule.getPanel());
    }

    @Test
    void shouldThrowException_WhenCrownJurisdictionHasNonAdultPanel() {
        // CROWN with YOUTH panel should throw exception
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add("courtScheduleId", UUID.randomUUID().toString())
                .add("courtRoomId", "2")
                .add("businessType", "BusType")
                .add("courtSession", "AM")
                .add("panel", "YOUTH")
                .add("jurisdiction", "CROWN")
                .add("isDraft", true)
                .add("maxSlots", 1)
                .build();

        assertThrows(ConverterException.class, () -> {
            updateCourtScheduleConverter.convert(jsonObject);
        });
    }
}