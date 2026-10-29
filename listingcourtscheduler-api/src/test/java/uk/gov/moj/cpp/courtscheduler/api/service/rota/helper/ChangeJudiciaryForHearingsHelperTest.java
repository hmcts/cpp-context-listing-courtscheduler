package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.ListingCommandClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeJudiciaryForHearingsHelper Tests")
class ChangeJudiciaryForHearingsHelperTest {

    @Mock
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Mock
    private ListingCommandClient listingCommandClient;

    @InjectMocks
    private ChangeJudiciaryForHearingsHelper changeJudiciaryForHearingsHelper;

    @Nested
    @DisplayName("Payload Building Tests")
    class PayloadBuildingTests {

        @Test
        @DisplayName("Should build one payload per hearing, de-duplicating judiciaries")
        void shouldBuildOnePayloadPerHearing() {
            // given
            final String courtScheduleIdA = randomUUID().toString();
            final String courtScheduleIdB = randomUUID().toString();
            final String hearingId1 = randomUUID().toString();
            final String hearingId2 = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleIdA, courtScheduleIdB);

            // Single-session hearings: hearing 1 on schedule A with 2 judiciaries, hearing 2 on
            // schedule B with 1 judiciary
            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{hearingId1, courtScheduleIdA, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId1, courtScheduleIdA, "jud-2", "District Judge", false, true},
                    new Object[]{hearingId1, courtScheduleIdA, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId2, courtScheduleIdB, "jud-3", "Magistrate", null, null});
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when
            final List<JsonObject> payloads =
                    changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds);

            // then
            assertEquals(2, payloads.size());

            final JsonObject payload1 = payloads.get(0);
            assertEquals(List.of(hearingId1), toStringList(payload1.getJsonArray("hearings")));

            final JsonArray judiciary1 = payload1.getJsonArray("judiciary");
            assertEquals(2, judiciary1.size());
            final Map<String, JsonObject> judiciary1ById = toJudicialRoleMap(judiciary1);
            final JsonObject judicialRole1 = judiciary1ById.get("jud-1");
            assertEquals("Magistrate", judicialRole1.getJsonObject("judicialRoleType").getString("judiciaryType"));
            assertTrue(judicialRole1.getBoolean("isBenchChairman"));
            assertFalse(judicialRole1.getBoolean("isDeputy"));
            final JsonObject judicialRole2 = judiciary1ById.get("jud-2");
            assertEquals("District Judge", judicialRole2.getJsonObject("judicialRoleType").getString("judiciaryType"));
            assertFalse(judicialRole2.getBoolean("isBenchChairman"));
            assertTrue(judicialRole2.getBoolean("isDeputy"));

            final JsonObject payload2 = payloads.get(1);
            assertEquals(List.of(hearingId2), toStringList(payload2.getJsonArray("hearings")));
            final JsonObject judicialRole3 = payload2.getJsonArray("judiciary").getJsonObject(0);
            assertEquals("jud-3", judicialRole3.getString("judicialId"));
            assertEquals("Magistrate", judicialRole3.getJsonObject("judicialRoleType").getString("judiciaryType"));
            // Optional booleans must be omitted when the columns are null
            assertFalse(judicialRole3.containsKey("isBenchChairman"));
            assertFalse(judicialRole3.containsKey("isDeputy"));

            // judiciaryAssignmentSource must be "AUTO" so listing treats the command as rota-driven
            payloads.forEach(payload -> assertEquals("AUTO", payload.getString("judiciaryAssignmentSource")));
        }

        @Test
        @DisplayName("Should build one payload for a multi-session hearing when all its sessions have the same judiciaries")
        void shouldBuildOnePayloadForMultiSessionHearingWithSameJudiciaries() {
            // given - the hearing is allocated to 3 sessions, only one of which changed, and the
            // judiciaries come back in a different order per session
            final String changedCourtScheduleId = randomUUID().toString();
            final String otherCourtScheduleId1 = randomUUID().toString();
            final String otherCourtScheduleId2 = randomUUID().toString();
            final String hearingId = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(changedCourtScheduleId);

            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{hearingId, changedCourtScheduleId, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId, changedCourtScheduleId, "jud-2", "Magistrate", false, false},
                    new Object[]{hearingId, otherCourtScheduleId1, "jud-2", "Magistrate", false, false},
                    new Object[]{hearingId, otherCourtScheduleId1, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId, otherCourtScheduleId2, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId, otherCourtScheduleId2, "jud-2", "Magistrate", false, false});
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when
            final List<JsonObject> payloads =
                    changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds);

            // then
            assertEquals(1, payloads.size());
            final JsonObject payload = payloads.get(0);
            assertEquals(List.of(hearingId), toStringList(payload.getJsonArray("hearings")));
            assertEquals(Set.of("jud-1", "jud-2"), toJudicialRoleMap(payload.getJsonArray("judiciary")).keySet());
        }

        @Test
        @DisplayName("Should skip a multi-session hearing whose sessions have different judiciaries")
        void shouldSkipMultiSessionHearingWithDifferentJudiciaries() {
            // given
            final String courtScheduleIdA = randomUUID().toString();
            final String courtScheduleIdB = randomUUID().toString();
            final String hearingId = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleIdA);

            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{hearingId, courtScheduleIdA, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId, courtScheduleIdB, "jud-9", "Magistrate", true, false});
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when / then
            assertTrue(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds).isEmpty());
        }

        @Test
        @DisplayName("Should skip a multi-session hearing whose sessions have the same judiciary with different roles")
        void shouldSkipMultiSessionHearingWithDifferentJudicialRoles() {
            // given - same judiciary on both sessions, but bench chairman on one only
            final String courtScheduleIdA = randomUUID().toString();
            final String courtScheduleIdB = randomUUID().toString();
            final String hearingId = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleIdA);

            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{hearingId, courtScheduleIdA, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId, courtScheduleIdB, "jud-1", "Magistrate", false, false});
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when / then
            assertTrue(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds).isEmpty());
        }

        @Test
        @DisplayName("Should skip a multi-session hearing when only some of its sessions have judiciaries")
        void shouldSkipMultiSessionHearingWhenSomeSessionsHaveNoJudiciaries() {
            // given - the LEFT JOIN returns null judiciary columns for session B
            final String courtScheduleIdA = randomUUID().toString();
            final String courtScheduleIdB = randomUUID().toString();
            final String hearingId = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleIdB);

            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{hearingId, courtScheduleIdA, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId, courtScheduleIdB, null, null, null, null});
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when / then
            assertTrue(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds).isEmpty());
        }

        @Test
        @DisplayName("Should send only the consistent hearings when consistent and inconsistent hearings are mixed")
        void shouldSendOnlyConsistentHearings() {
            // given
            final String courtScheduleIdA = randomUUID().toString();
            final String courtScheduleIdB = randomUUID().toString();
            final String consistentHearingId = randomUUID().toString();
            final String inconsistentHearingId = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleIdA);

            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{consistentHearingId, courtScheduleIdA, "jud-1", "Magistrate", true, false},
                    new Object[]{consistentHearingId, courtScheduleIdB, "jud-1", "Magistrate", true, false},
                    new Object[]{inconsistentHearingId, courtScheduleIdA, "jud-1", "Magistrate", true, false},
                    new Object[]{inconsistentHearingId, courtScheduleIdB, "jud-2", "Magistrate", true, false});
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when
            final List<JsonObject> payloads =
                    changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds);

            // then
            assertEquals(1, payloads.size());
            assertEquals(List.of(consistentHearingId), toStringList(payloads.get(0).getJsonArray("hearings")));
        }

        @Test
        @DisplayName("Should group hearings with identical judiciary sets into one payload")
        void shouldGroupHearingsWithSameJudiciaryIntoOnePayload() {
            // given - two single-session hearings on separate court schedules, both assigned jud-1
            final String courtScheduleIdA = randomUUID().toString();
            final String courtScheduleIdB = randomUUID().toString();
            final String hearingId1 = randomUUID().toString();
            final String hearingId2 = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleIdA, courtScheduleIdB);

            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{hearingId1, courtScheduleIdA, "jud-1", "Magistrate", true, false},
                    new Object[]{hearingId2, courtScheduleIdB, "jud-1", "Magistrate", true, false});
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when
            final List<JsonObject> payloads =
                    changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds);

            // then - one payload because both hearings share the same judiciary set
            assertEquals(1, payloads.size());
            final JsonObject payload = payloads.get(0);
            assertEquals(Set.copyOf(List.of(hearingId1, hearingId2)),
                    Set.copyOf(toStringList(payload.getJsonArray("hearings"))));
            assertEquals(1, payload.getJsonArray("judiciary").size());
            assertEquals("jud-1", payload.getJsonArray("judiciary").getJsonObject(0).getString("judicialId"));
            assertEquals("AUTO", payload.getString("judiciaryAssignmentSource"));
        }

        @Test
        @DisplayName("Should build payload with empty judiciary array when no session of the hearing has judiciaries")
        void shouldBuildPayloadWithEmptyJudiciaryWhenAllJudiciariesRemoved() {
            // given - the LEFT JOIN returns null judiciary columns for every session of the hearing
            final String courtScheduleIdA = randomUUID().toString();
            final String courtScheduleIdB = randomUUID().toString();
            final String hearingId = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleIdA);

            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{hearingId, courtScheduleIdA, null, null, null, null},
                    new Object[]{hearingId, courtScheduleIdB, null, null, null, null});
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when
            final List<JsonObject> payloads =
                    changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds);

            // then - the command is still sent, with an empty judiciary array so listing
            // clears the judiciary from the hearing
            assertEquals(1, payloads.size());
            final JsonObject payload = payloads.get(0);
            assertEquals(List.of(hearingId), toStringList(payload.getJsonArray("hearings")));
            assertTrue(payload.getJsonArray("judiciary").isEmpty());
            assertEquals("AUTO", payload.getString("judiciaryAssignmentSource"));
        }

        @Test
        @DisplayName("Should return empty payload list when no changed court schedule IDs provided")
        void shouldReturnEmptyPayloadListWhenNoChangedCourtScheduleIds() {
            assertTrue(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(List.of()).isEmpty());
            assertTrue(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(null).isEmpty());
            verifyNoInteractions(courtScheduleJudiciaryService);
        }

        @Test
        @DisplayName("Should return empty payload list when the query returns no rows")
        void shouldReturnEmptyPayloadListWhenQueryReturnsNoRows() {
            // given
            final List<String> changedCourtScheduleIds = List.of(randomUUID().toString());
            when(courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(List.of());

            // when / then
            assertTrue(changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds).isEmpty());
        }

        private List<String> toStringList(final JsonArray jsonArray) {
            return jsonArray.getValuesAs(JsonValue::toString).stream()
                    .map(value -> value.replace("\"", ""))
                    .collect(Collectors.toList());
        }

        private Map<String, JsonObject> toJudicialRoleMap(final JsonArray judiciary) {
            return judiciary.getValuesAs(JsonObject.class).stream()
                    .collect(Collectors.toMap(role -> role.getString("judicialId"), role -> role));
        }
    }

    @Nested
    @DisplayName("Command Sending Tests")
    class CommandSendingTests {

        private final JsonObject payload1 = payloadFor(randomUUID().toString());
        private final JsonObject payload2 = payloadFor(randomUUID().toString());
        private final JsonObject payload3 = payloadFor(randomUUID().toString());

        @Test
        @DisplayName("Should send every payload and return the sent count")
        void shouldSendEveryPayload() {
            // given
            doNothing().when(listingCommandClient).changeJudiciaryForHearings(any(JsonObject.class));

            // when
            final int sentCount = changeJudiciaryForHearingsHelper
                    .sendChangeJudiciaryForHearingsCommands(List.of(payload1, payload2));

            // then
            assertEquals(2, sentCount);
            verify(listingCommandClient).changeJudiciaryForHearings(eq(payload1));
            verify(listingCommandClient).changeJudiciaryForHearings(eq(payload2));
        }

        @Test
        @DisplayName("Should continue sending remaining payloads when one fails")
        void shouldContinueSendingWhenOnePayloadFails() {
            // given
            doNothing().when(listingCommandClient).changeJudiciaryForHearings(eq(payload1));
            doThrow(new RuntimeException("listing unavailable"))
                    .when(listingCommandClient).changeJudiciaryForHearings(eq(payload2));
            doNothing().when(listingCommandClient).changeJudiciaryForHearings(eq(payload3));

            // when
            final int sentCount = changeJudiciaryForHearingsHelper
                    .sendChangeJudiciaryForHearingsCommands(List.of(payload1, payload2, payload3));

            // then
            assertEquals(2, sentCount);
            verify(listingCommandClient, times(3)).changeJudiciaryForHearings(any(JsonObject.class));
        }

        @Test
        @DisplayName("Should send nothing when there are no payloads")
        void shouldSendNothingWhenThereAreNoPayloads() {
            assertEquals(0, changeJudiciaryForHearingsHelper.sendChangeJudiciaryForHearingsCommands(List.of()));
            assertEquals(0, changeJudiciaryForHearingsHelper.sendChangeJudiciaryForHearingsCommands(null));
            verifyNoInteractions(listingCommandClient);
        }

        private JsonObject payloadFor(final String hearingId) {
            return Json.createObjectBuilder()
                    .add("hearings", Json.createArrayBuilder().add(hearingId))
                    .add("judiciary", Json.createArrayBuilder()
                            .add(Json.createObjectBuilder()
                                    .add("judicialId", randomUUID().toString())
                                    .add("judicialRoleType", Json.createObjectBuilder()
                                            .add("judiciaryType", "Magistrate"))))
                    .build();
        }
    }
}
