package uk.gov.moj.cpp.courtscheduler.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;

class AvailabilityDayOfWeekJsonTest {

    private static final String REPEAT_DAYS = "repeatDays";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeRepeatDaysAsTitleCaseWireValues() {
        final JudiciaryAvailabilityRuleResponse response = new JudiciaryAvailabilityRuleResponse();
        response.setRepeatDays(List.of(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.FRIDAY));

        // Same generic conversion JudiciaryAvailabilityApi#toFlatMap uses for the GET responses.
        final Map<String, Object> flat = objectMapper.convertValue(response, new TypeReference<>() { });

        assertEquals(List.of("Monday", "Friday"), flat.get(REPEAT_DAYS));
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
