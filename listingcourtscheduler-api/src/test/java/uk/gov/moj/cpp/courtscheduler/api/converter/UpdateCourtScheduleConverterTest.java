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
    private static final String ADULT_2 = "ADULT";
    private static final String BUS_TYPE = "BusType";
    private static final String CROWN_2 = "CROWN";
    private static final String MAGISTRATES_2 = "MAGISTRATES";
    private static final String BUSINESS_TYPE = "businessType";
    private static final String COURT_ROOM_ID = "courtRoomId";
    private static final String COURT_SCHEDULE_ID = "courtScheduleId";
    private static final String COURT_SESSION = "courtSession";
    private static final String IS_DRAFT = "isDraft";
    private static final String JURISDICTION = "jurisdiction";
    private static final String MAX_SLOTS = "maxSlots";
    private static final String PANEL = "panel";


    @InjectMocks
    private UpdateCourtScheduleConverter updateCourtScheduleConverter;

    @Test
    void shouldConvertJsonObject_ToUpdateCourtSchedule() {

        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_ID, UUID.randomUUID().toString())
                .add(COURT_ROOM_ID, "2")
                .add(BUSINESS_TYPE, BUS_TYPE)
                .add(COURT_SESSION, "AM")
                .add(PANEL, ADULT_2)
                .add(JURISDICTION, MAGISTRATES_2)
                .add(MAX_SLOTS, 1)
                .add("maxDuration", 1)
                .add("sessionStartTime", "11:00")
                .add("sessionEndTime", "17:00")
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals("2", updateCourtSchedule.getCourtRoomId());
        assertEquals("11:00", updateCourtSchedule.getSessionStartTime());
        assertEquals("17:00", updateCourtSchedule.getSessionEndTime());
        assertEquals(MAGISTRATES_2, updateCourtSchedule.getJurisdiction());
    }

    @Test
    void shouldConvertJsonObject_WithCrownJurisdictionAndIsDraft() {

        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_ID, UUID.randomUUID().toString())
                .add(COURT_ROOM_ID, "2")
                .add(BUSINESS_TYPE, BUS_TYPE)
                .add(COURT_SESSION, "AM")
                .add(PANEL, ADULT_2)
                .add(JURISDICTION, CROWN_2)
                .add(IS_DRAFT, true)
                .add(MAX_SLOTS, 1)
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals(CROWN_2, updateCourtSchedule.getJurisdiction());
        assertEquals(Boolean.TRUE, updateCourtSchedule.isDraft());
    }

    @Test
    void shouldConvertJsonObject_WithoutIsDraft() {

        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_ID, UUID.randomUUID().toString())
                .add(COURT_ROOM_ID, "2")
                .add(BUSINESS_TYPE, BUS_TYPE)
                .add(COURT_SESSION, "AM")
                .add(PANEL, ADULT_2)
                .add(JURISDICTION, MAGISTRATES_2)
                .add(MAX_SLOTS, 1)
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals(MAGISTRATES_2, updateCourtSchedule.getJurisdiction());
        assertNull(updateCourtSchedule.isDraft());
    }

    @Test
    void shouldConvertJsonObject_WithCrownJurisdictionWithoutPanel() {
        // Panel is optional for CROWN jurisdiction
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_ID, UUID.randomUUID().toString())
                .add(COURT_ROOM_ID, "2")
                .add(BUSINESS_TYPE, BUS_TYPE)
                .add(COURT_SESSION, "AM")
                .add(JURISDICTION, CROWN_2)
                .add(IS_DRAFT, true)
                .add(MAX_SLOTS, 1)
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals(CROWN_2, updateCourtSchedule.getJurisdiction());
        assertNull(updateCourtSchedule.getPanel()); // Panel should be null when not supplied
    }

    @Test
    void shouldConvertJsonObject_WithCrownJurisdictionAndAdultPanel() {
        // CROWN with ADULT panel should work
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_ID, UUID.randomUUID().toString())
                .add(COURT_ROOM_ID, "2")
                .add(BUSINESS_TYPE, BUS_TYPE)
                .add(COURT_SESSION, "AM")
                .add(PANEL, ADULT_2)
                .add(JURISDICTION, CROWN_2)
                .add(IS_DRAFT, true)
                .add(MAX_SLOTS, 1)
                .build();

        final UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals(CROWN_2, updateCourtSchedule.getJurisdiction());
        assertEquals(ADULT_2, updateCourtSchedule.getPanel());
    }

    @Test
    void shouldThrowException_WhenCrownJurisdictionHasNonAdultPanel() {
        // CROWN with YOUTH panel should throw exception
        final JsonObject jsonObject = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_ID, UUID.randomUUID().toString())
                .add(COURT_ROOM_ID, "2")
                .add(BUSINESS_TYPE, BUS_TYPE)
                .add(COURT_SESSION, "AM")
                .add(PANEL, "YOUTH")
                .add(JURISDICTION, CROWN_2)
                .add(IS_DRAFT, true)
                .add(MAX_SLOTS, 1)
                .build();

        assertThrows(ConverterException.class, () -> {
            updateCourtScheduleConverter.convert(jsonObject);
        });
    }
}