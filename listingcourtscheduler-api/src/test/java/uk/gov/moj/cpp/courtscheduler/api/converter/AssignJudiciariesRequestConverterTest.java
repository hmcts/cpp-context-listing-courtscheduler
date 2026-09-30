package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;

import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class AssignJudiciariesRequestConverterTest {
    private static final String UUID_3FA85F64 = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String UUID_8A9F3E44 = "8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111";
    private static final String JUDICIARIES = "judiciaries";
    private static final String JUDICIARY_ID = "judiciaryId";
    private static final String SESSION_IDS = "sessionIds";


    private final AssignJudiciariesRequestConverter converter = new AssignJudiciariesRequestConverter();

    @Test
    void shouldConvertPayload() {
        final JsonArrayBuilder sessionIds = Json.createArrayBuilder()
                .add(UUID_8A9F3E44)
                .add("1b2c3d44-7e8f-4b9a-8c7d-2a3b4c5d6666");
        final JsonObject payload = Json.createObjectBuilder()
                .add(JUDICIARIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(JUDICIARY_ID, UUID_3FA85F64)
                                .add(SESSION_IDS, sessionIds)))
                .build();

        final AssignJudiciariesRequest request = converter.convert(payload);

        assertEquals(1, request.getJudiciaries().size());
        assertEquals(UUID_3FA85F64, request.getJudiciaries().get(0).getJudiciaryId());
        assertEquals(List.of(
                UUID_8A9F3E44,
                "1b2c3d44-7e8f-4b9a-8c7d-2a3b4c5d6666"), request.getJudiciaries().get(0).getSessionIds());
    }

    @Test
    void shouldReturnEmptyRequestWhenPayloadMissing() {
        final AssignJudiciariesRequest request = converter.convert(Json.createObjectBuilder().build());

        assertTrue(request.getJudiciaries().isEmpty());
    }

    @Test
    void shouldConvertSkipValidationsWhenTrue() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(JUDICIARIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(JUDICIARY_ID, UUID_3FA85F64)
                                .add(SESSION_IDS, Json.createArrayBuilder()
                                        .add(UUID_8A9F3E44))))
                .add("skipValidations", true)
                .build();

        final AssignJudiciariesRequest request = converter.convert(payload);

        assertTrue(request.isSkipValidations());
    }

    @Test
    void shouldConvertSkipValidationsWhenFalse() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(JUDICIARIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(JUDICIARY_ID, UUID_3FA85F64)
                                .add(SESSION_IDS, Json.createArrayBuilder()
                                        .add(UUID_8A9F3E44))))
                .add("skipValidations", false)
                .build();

        final AssignJudiciariesRequest request = converter.convert(payload);

        assertFalse(request.isSkipValidations());
    }

    @Test
    void shouldDefaultSkipValidationsToFalseWhenNotProvided() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(JUDICIARIES, Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add(JUDICIARY_ID, UUID_3FA85F64)
                                .add(SESSION_IDS, Json.createArrayBuilder()
                                        .add(UUID_8A9F3E44))))
                .build();

        final AssignJudiciariesRequest request = converter.convert(payload);

        assertFalse(request.isSkipValidations());
    }
}

