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
    private static final String UUID_3FA85F64 = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
    private static final String UUID_8A9F3E44 = "8a9f3e44-2d6a-4f4b-b7d1-9e6b9fbf1111";
    private static final String MAGISTRATE_2 = "MAGISTRATE";
    private static final String COURT_SCHEDULE_IDS = "courtScheduleIds";
    private static final String JUDICIAL_ID = "judicialId";
    private static final String JUDICIAL_ROLE_TYPE = "judicialRoleType";
    private static final String JUDICIARY = "judiciary";
    private static final String JUDICIARY_TYPE = "judiciaryType";


    @InjectMocks
    private AssignJudiciaryToSessionsConverter converter;

    @Test
    void shouldExtractJudicialRoleTypeJudiciaryType() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_IDS, Json.createArrayBuilder()
                        .add(UUID_8A9F3E44)
                        .build())
                .add(JUDICIARY, Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ID, UUID_3FA85F64)
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder()
                                        .add(JUDICIARY_TYPE, "DISTRICT_JUDGE")
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
        assertEquals(UUID_3FA85F64, j.getJudicialId());
        assertEquals("DISTRICT_JUDGE", j.getJudiciaryType());
        assertTrue(j.isBenchChairman());
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
                .add(COURT_SCHEDULE_IDS, Json.createArrayBuilder()
                        .add("not-a-uuid")
                        .add(UUID_8A9F3E44)
                        .add("")
                        .build())
                .build();

        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);

        assertEquals(1, r.getCourtScheduleIds().size());
        assertEquals(UUID_8A9F3E44, r.getCourtScheduleIds().get(0));
    }

    @Test
    void shouldParseMultipleSessionsAndJudiciaryLines() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_IDS, Json.createArrayBuilder()
                        .add(UUID_8A9F3E44)
                        .add("1b2c3d44-7e8f-4b9a-8c7d-2a3b4c5d6666")
                        .build())
                .add(JUDICIARY, Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ID, UUID_3FA85F64)
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder().add(JUDICIARY_TYPE, MAGISTRATE_2).build())
                                .build())
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ID, "7e6f4a11-1111-2222-3333-444455556666")
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder().add(JUDICIARY_TYPE, MAGISTRATE_2).build())
                                .build())
                        .build())
                .build();

        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);

        assertEquals(2, r.getCourtScheduleIds().size());
        assertEquals(2, r.getJudiciary().size());
        assertEquals(MAGISTRATE_2, r.getJudiciary().get(1).getJudiciaryType());
    }

    @Test
    void shouldReturnEmptyJudiciaryWhenKeyMissing() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_IDS, Json.createArrayBuilder()
                        .add(UUID_8A9F3E44)
                        .build())
                .build();

        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);

        assertEquals(1, r.getCourtScheduleIds().size());
        assertTrue(r.getJudiciary().isEmpty());
    }

    @Test
    void shouldLeaveJudiciaryTypeNullWhenRoleTypeMissing() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_IDS, Json.createArrayBuilder()
                        .add(UUID_8A9F3E44)
                        .build())
                .add(JUDICIARY, Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ID, UUID_3FA85F64)
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
                Json.createObjectBuilder().addNull(COURT_SCHEDULE_IDS).build());
        assertTrue(r.getCourtScheduleIds().isEmpty());
    }

    @Test
    void shouldIgnoreNonStringElementsInCourtScheduleIdsArray() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(COURT_SCHEDULE_IDS, Json.createArrayBuilder()
                        .add(JsonValue.TRUE)
                        .add(UUID_8A9F3E44)
                        .build())
                .build();
        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);
        assertEquals(1, r.getCourtScheduleIds().size());
    }

    @Test
    void shouldReturnEmptyJudiciaryWhenJudiciaryKeyIsJsonNull() {
        assertTrue(converter.convert(Json.createObjectBuilder()
                .addNull(JUDICIARY)
                .build()).getJudiciary().isEmpty());
    }

    @Test
    void shouldSkipNonObjectElementsInJudiciaryArray() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(JUDICIARY, Json.createArrayBuilder()
                        .add("not-an-object")
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ID, UUID_3FA85F64)
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder().add(JUDICIARY_TYPE, "RECORDER").build())
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
                .add(JUDICIARY, Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ID, UUID_3FA85F64)
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder().build())
                                .build())
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ID, "7e6f4a11-1111-2222-3333-444455556666")
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder().addNull(JUDICIARY_TYPE).build())
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
                .add(JUDICIARY, Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder().add(JUDICIARY_TYPE, MAGISTRATE_2).build())
                                .build())
                        .add(createObjectBuilder()
                                .addNull(JUDICIAL_ID)
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder().add(JUDICIARY_TYPE, MAGISTRATE_2).build())
                                .addNull("isDeputy")
                                .addNull("isBenchChairman")
                                .build())
                        .build())
                .build();
        final AssignJudiciaryToSessionsRequest r = converter.convert(payload);
        assertNull(r.getJudiciary().get(0).getJudicialId());
        assertEquals(MAGISTRATE_2, r.getJudiciary().get(0).getJudiciaryType());
        assertNull(r.getJudiciary().get(0).isDeputy());
        assertNull(r.getJudiciary().get(0).isBenchChairman());

        assertNull(r.getJudiciary().get(1).getJudicialId());
        assertNull(r.getJudiciary().get(1).isDeputy());
        assertNull(r.getJudiciary().get(1).isBenchChairman());
    }

    @Test
    void shouldParseIsDeputyWhenPresent() {
        final JsonObject payload = Json.createObjectBuilder()
                .add(JUDICIARY, Json.createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(JUDICIAL_ID, UUID_3FA85F64)
                                .add(JUDICIAL_ROLE_TYPE, createObjectBuilder().add(JUDICIARY_TYPE, "DJ").build())
                                .add("isDeputy", true)
                                .add("isBenchChairman", false)
                                .build())
                        .build())
                .build();
        final SessionJudiciary j = converter.convert(payload).getJudiciary().get(0);
        assertTrue(j.isDeputy());
        assertFalse(j.isBenchChairman());
    }
}
