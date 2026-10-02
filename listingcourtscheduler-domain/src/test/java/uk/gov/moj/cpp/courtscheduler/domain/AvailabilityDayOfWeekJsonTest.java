package uk.gov.moj.cpp.courtscheduler.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;

class AvailabilityDayOfWeekJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeAsTitleCaseWireValues() throws Exception {
        assertEquals("[\"Monday\",\"Friday\"]",
                objectMapper.writeValueAsString(List.of(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.FRIDAY)));
    }

    @Test
    void shouldDeserializeTitleCaseWireValues() throws Exception {
        final List<AvailabilityDayOfWeek> days = objectMapper.readValue(
                "[\"Monday\",\"Tuesday\",\"Wednesday\",\"Thursday\",\"Friday\"]", new TypeReference<>() { });

        assertEquals(List.of(AvailabilityDayOfWeek.values()), days);
    }

    @Test
    void shouldRejectUnknownDay() {
        assertThrows(ValueInstantiationException.class,
                () -> objectMapper.readValue("\"MONDAY\"", AvailabilityDayOfWeek.class));
    }
}
