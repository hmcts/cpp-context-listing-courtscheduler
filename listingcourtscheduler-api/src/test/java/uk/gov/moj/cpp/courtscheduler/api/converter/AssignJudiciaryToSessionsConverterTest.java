package uk.gov.moj.cpp.courtscheduler.api.converter;

import static jakarta.json.Json.createObjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciaryToSessionsRequest;
import uk.gov.moj.cpp.courtscheduler.domain.SessionJudiciary;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignJudiciaryToSessionsConverterTest {

    @InjectMocks
    private AssignJudiciaryToSessionsConverter converter;

    @Test
    void shouldExtractJudicialRoleTypeJudiciaryType() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("courtScheduleIds", Json.createArrayBuilder()
                        .add("8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111")
                        .build())
                .add("judiciary", Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judicialId", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                .add("judicialRoleType", createObjectBuilder()
                                        .add("judiciaryType", "DISTRICT_JUDGE")
                                        .build())
                                .add("isBenchChairman", true)
                                .add("isDeputy", false)
                                .build())
                        .build())
                .build();

        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);

        assertEquals(1, r.getCourtScheduleIds().size());
        assertEquals(1, r.getJudiciary().size());
        final SessionJudiciary j = r.getJudiciary().get(0);
        assertEquals("3fa85f64-5717-4562-b3fc-2c963f66afa6", j.getJudicialId());
        assertEquals("DISTRICT_JUDGE", j.getJudiciaryType());
        assertTrue(j.getIsBenchChairman());
    }

    @Test
    void shouldReturnEmptyWhenPayloadNull() {
        final AssignJudiciaryToSessionsRequest r = converter.convert(null);
        assertTrue(r.getCourtScheduleIds().isEmpty());
        assertTrue(r.getJudiciary().isEmpty());
    }

    @Test
    void shouldFilterOutNonUuidCourtScheduleIds() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("courtScheduleIds", Json.createArrayBuilder()
                        .add("not-a-uuid")
                        .add("8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111")
                        .add("")
                        .build())
                .build();

        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);

        assertEquals(1, r.getCourtScheduleIds().size());
        assertEquals("8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111", r.getCourtScheduleIds().get(0));
    }

    @Test
    void shouldParseMultipleSessionsAndJudiciaryLines() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("courtScheduleIds", Json.createArrayBuilder()
                        .add("8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111")
                        .add("1b2c3d44-7e8f-4b9a-8c7d-2a3b4c5d6666")
                        .build())
                .add("judiciary", Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judicialId", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                .add("judicialRoleType", createObjectBuilder().add("judiciaryType", "MAGISTRATE").build())
                                .build())
                        .add(createObjectBuilder()
                                .add("judicialId", "7e6f4a11-1111-2222-3333-444455556666")
                                .add("judicialRoleType", createObjectBuilder().add("judiciaryType", "MAGISTRATE").build())
                                .build())
                        .build())
                .build();

        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);

        assertEquals(2, r.getCourtScheduleIds().size());
        assertEquals(2, r.getJudiciary().size());
        assertEquals("MAGISTRATE", r.getJudiciary().get(1).getJudiciaryType());
    }

    @Test
    void shouldReturnEmptyJudiciaryWhenKeyMissing() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("courtScheduleIds", Json.createArrayBuilder()
                        .add("8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111")
                        .build())
                .build();

        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);

        assertEquals(1, r.getCourtScheduleIds().size());
        assertTrue(r.getJudiciary().isEmpty());
    }

    @Test
    void shouldLeaveJudiciaryTypeNullWhenRoleTypeMissing() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("courtScheduleIds", Json.createArrayBuilder()
                        .add("8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111")
                        .build())
                .add("judiciary", Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judicialId", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                .build())
                        .build())
                .build();

        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);

        assertEquals(1, r.getJudiciary().size());
        assertNull(r.getJudiciary().get(0).getJudiciaryType());
    }

    @Test
    void shouldReturnEmptyCourtScheduleIdsWhenKeyNullOrAbsent() {
        assertTrue(converter.convert(Json.createObjectBuilder().build()).getCourtScheduleIds().isEmpty());
        final AssignJudiciaryToSessionsRequest r = converter.convert(
                Json.createObjectBuilder().addNull("courtScheduleIds").build());
        assertTrue(r.getCourtScheduleIds().isEmpty());
    }

    @Test
    void shouldIgnoreNonStringElementsInCourtScheduleIdsArray() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("courtScheduleIds", Json.createArrayBuilder()
                        .add(JsonValue.TRUE)
                        .add("8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111")
                        .build())
                .build();
        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);
        assertEquals(1, r.getCourtScheduleIds().size());
    }

    @Test
    void shouldReturnEmptyJudiciaryWhenJudiciaryKeyIsJsonNull() {
        assertTrue(converter.convert(Json.createObjectBuilder()
                .addNull("judiciary")
                .build()).getJudiciary().isEmpty());
    }

    @Test
    void shouldSkipNonObjectElementsInJudiciaryArray() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("judiciary", Json.createArrayBuilder()
                        .add("not-an-object")
                        .add(createObjectBuilder()
                                .add("judicialId", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                .add("judicialRoleType", createObjectBuilder().add("judiciaryType", "RECORDER").build())
                                .build())
                        .build())
                .build();
        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);
        assertEquals(1, r.getJudiciary().size());
        assertEquals("RECORDER", r.getJudiciary().get(0).getJudiciaryType());
    }

    @Test
    void shouldLeaveJudiciaryTypeNullWhenNestedObjectIncomplete() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("judiciary", Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judicialId", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                .add("judicialRoleType", createObjectBuilder().build())
                                .build())
                        .add(createObjectBuilder()
                                .add("judicialId", "7e6f4a11-1111-2222-3333-444455556666")
                                .add("judicialRoleType", createObjectBuilder().addNull("judiciaryType").build())
                                .build())
                        .build())
                .build();
        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);
        assertEquals(2, r.getJudiciary().size());
        assertNull(r.getJudiciary().get(0).getJudiciaryType());
        assertNull(r.getJudiciary().get(1).getJudiciaryType());
    }

    @Test
    void shouldLeaveJudicialIdAndFlagsNullWhenOmittedOrNull() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("judiciary", Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judicialRoleType", createObjectBuilder().add("judiciaryType", "MAGISTRATE").build())
                                .build())
                        .add(createObjectBuilder()
                                .addNull("judicialId")
                                .add("judicialRoleType", createObjectBuilder().add("judiciaryType", "MAGISTRATE").build())
                                .addNull("isDeputy")
                                .addNull("isBenchChairman")
                                .build())
                        .build())
                .build();
        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);
        assertNull(r.getJudiciary().get(0).getJudicialId());
        assertEquals("MAGISTRATE", r.getJudiciary().get(0).getJudiciaryType());
        assertNull(r.getJudiciary().get(0).getIsDeputy());
        assertNull(r.getJudiciary().get(0).getIsBenchChairman());

        assertNull(r.getJudiciary().get(1).getJudicialId());
        assertNull(r.getJudiciary().get(1).getIsDeputy());
        assertNull(r.getJudiciary().get(1).getIsBenchChairman());
    }

    @Test
    void shouldParseIsDeputyWhenPresent() {
        final JsonObject payload = Json.createObjectBuilder()
                .add("judiciary", Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add("judicialId", "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                                .add("judicialRoleType", createObjectBuilder().add("judiciaryType", "DJ").build())
                                .add("isDeputy", true)
                                .add("isBenchChairman", false)
                                .build())
                        .build())
                .build();
        final SessionJudiciary j = converter.convert(payload).getJudiciary().get(0);
        assertTrue(j.getIsDeputy());
        assertFalse(j.getIsBenchChairman());
    }
}
