package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;

import java.util.List;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import org.junit.jupiter.api.Test;

class AssignJudiciariesRequestConverterTest {

    private final AssignJudiciariesRequestConverter converter = new AssignJudiciariesRequestConverter();

    @Test
    void shouldConvertPayload() {
        final JsonArrayBuilder sessionIds = Json.createArrayBuilder()
                .add("8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111")
                .add("1b2c3d44-7e8f-4b9a-8c7d-2a3b4c5d6666");
        final JsonObject payload = Json.createObjectBuilder()
                .add("judiciaries", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("judiciaryId", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                .add("sessionIds", sessionIds)))
                .build();

        final AssignJudiciariesRequest request = converter.convert(payload);

        assertEquals(1, request.getJudiciaries().size());
        assertEquals("3fa85f64-5717-4562-b3fc-2c963f66afa6", request.getJudiciaries().get(0).getJudiciaryId());
        assertEquals(List.of(
                "8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111",
                "1b2c3d44-7e8f-4b9a-8c7d-2a3b4c5d6666"), request.getJudiciaries().get(0).getSessionIds());
    }

    @Test
    void shouldReturnEmptyRequestWhenPayloadMissing() {
        final AssignJudiciariesRequest request = converter.convert(Json.createObjectBuilder().build());

        assertTrue(request.getJudiciaries().isEmpty());
    }
}

