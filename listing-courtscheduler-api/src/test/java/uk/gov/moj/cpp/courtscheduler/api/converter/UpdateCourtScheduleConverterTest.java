package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;

import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UpdateCourtScheduleConverterTest {

    @InjectMocks
    private UpdateCourtScheduleConverter updateCourtScheduleConverter;

    @Test
    public void shouldConvertJsonObject_ToUpdateCourtSchedule() {

        JsonObject jsonObject = Json.createObjectBuilder()
                .add("courtScheduleId", UUID.randomUUID().toString())
                .add("courtRoomId", "2")
                .add("businessType", "BusType")
                .add("courtSession", "AM")
                .add("panel", "ADULT")
                .add("maxSlots", 1)
                .add("maxDuration", 1)
                .build();

        UpdateCourtSchedule updateCourtSchedule = updateCourtScheduleConverter.convert(jsonObject);

        assertEquals("2", updateCourtSchedule.getCourtRoomId());
    }
}