package uk.gov.moj.cpp.courtscheduler.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;

import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.everit.json.schema.loader.SchemaLoader;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WeekCommencingCreateSchemaTest {

    private static final String VALID = """
            {"courtCentreId":"f8254db1-1683-483e-afb3-b87fde5a0a26","businessType":"TRL",
             "startDate":"2027-01-04","endDate":"2027-02-01","capacityInMinutes":%s}""";

    private static Schema schema;

    @BeforeAll
    static void loadSchema() throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream("request-schemas/courtscheduler.create-week-commencing.sessions.json")) {
            schema = SchemaLoader.load(new JSONObject(new JSONTokener(in)));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 360, 359_640})
    void shouldAcceptCapacityWithinOneMinuteToNineHundredAndNinetyNineDays(final int capacity) {
        assertDoesNotThrow(() -> schema.validate(new JSONObject(VALID.formatted(capacity))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "359641", "1.5", "\"360\""})
    void shouldRejectCapacityOutsideTheRangeOrNotAnInteger(final String capacity) {
        assertThrows(ValidationException.class, () -> schema.validate(new JSONObject(VALID.formatted(capacity))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"capacity", "capacityDays"})
    void shouldRejectTheOldCapacityNames(final String oldName) {
        final JSONObject body = new JSONObject(VALID.formatted(360));
        body.remove("capacityInMinutes");
        body.put(oldName, 360);
        assertThrows(ValidationException.class, () -> schema.validate(body));
    }

    @Test
    void shouldAcceptOptionalOverbookingFlag() {
        final JSONObject body = new JSONObject(VALID.formatted(360)).put("isOverbookingAllowed", true);
        assertDoesNotThrow(() -> schema.validate(body));
    }

    @Test
    void shouldRejectABadCourtCentreId() {
        final JSONObject body = new JSONObject(VALID.formatted(360)).put("courtCentreId", "not-a-uuid");
        assertThrows(ValidationException.class, () -> schema.validate(body));
    }
}
