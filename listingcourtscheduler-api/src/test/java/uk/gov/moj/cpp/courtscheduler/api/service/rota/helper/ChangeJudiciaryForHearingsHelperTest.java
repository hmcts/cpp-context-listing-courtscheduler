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
        @DisplayName("Should build one payload per court schedule, de-duplicating hearings and judiciaries")
        void shouldBuildOnePayloadPerCourtSchedule() {
            // given
            final String courtScheduleIdA = randomUUID().toString();
            final String courtScheduleIdB = randomUUID().toString();
            final String hearingId1 = randomUUID().toString();
            final String hearingId2 = randomUUID().toString();
            final String hearingId3 = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleIdA, courtScheduleIdB);

            // The join returns one row per (hearing, judiciary) combination - schedule A has
            // 2 hearings x 2 judiciaries = 4 rows, schedule B has 1 x 1.
            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{courtScheduleIdA, hearingId1, "jud-1", "Magistrate", true, false},
                    new Object[]{courtScheduleIdA, hearingId1, "jud-2", "District Judge", false, true},
                    new Object[]{courtScheduleIdA, hearingId2, "jud-1", "Magistrate", true, false},
                    new Object[]{courtScheduleIdA, hearingId2, "jud-2", "District Judge", false, true},
                    new Object[]{courtScheduleIdB, hearingId3, "jud-3", "Magistrate", null, null});
            when(courtScheduleJudiciaryService.getJudiciaryHearingInfoForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when
            final List<JsonObject> payloads =
                    changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds);

            // then
            assertEquals(2, payloads.size());

            final JsonObject payloadA = payloads.get(0);
            final JsonArray hearingsA = payloadA.getJsonArray("hearings");
            assertEquals(List.of(hearingId1, hearingId2), toStringList(hearingsA));

            final JsonArray judiciaryA = payloadA.getJsonArray("judiciary");
            assertEquals(2, judiciaryA.size());
            final Map<String, JsonObject> judiciaryAById = toJudicialRoleMap(judiciaryA);
            final JsonObject judicialRole1 = judiciaryAById.get("jud-1");
            assertEquals("Magistrate", judicialRole1.getJsonObject("judicialRoleType").getString("judiciaryType"));
            assertTrue(judicialRole1.getBoolean("isBenchChairman"));
            assertFalse(judicialRole1.getBoolean("isDeputy"));
            final JsonObject judicialRole2 = judiciaryAById.get("jud-2");
            assertEquals("District Judge", judicialRole2.getJsonObject("judicialRoleType").getString("judiciaryType"));
            assertFalse(judicialRole2.getBoolean("isBenchChairman"));
            assertTrue(judicialRole2.getBoolean("isDeputy"));

            final JsonObject payloadB = payloads.get(1);
            assertEquals(List.of(hearingId3), toStringList(payloadB.getJsonArray("hearings")));
            final JsonObject judicialRole3 = payloadB.getJsonArray("judiciary").getJsonObject(0);
            assertEquals("jud-3", judicialRole3.getString("judicialId"));
            assertEquals("Magistrate", judicialRole3.getJsonObject("judicialRoleType").getString("judiciaryType"));
            // Optional booleans must be omitted when the columns are null
            assertFalse(judicialRole3.containsKey("isBenchChairman"));
            assertFalse(judicialRole3.containsKey("isDeputy"));

            // judiciaryAssignmentSource must be "AUTO" so listing treats the command as rota-driven
            payloads.forEach(payload -> assertEquals("AUTO", payload.getString("judiciaryAssignmentSource")));
        }

        @Test
        @DisplayName("Should build payload with empty judiciary array when all judiciaries were removed from a schedule")
        void shouldBuildPayloadWithEmptyJudiciaryWhenAllJudiciariesRemoved() {
            // given - the LEFT JOIN returns the allocated hearings with null judiciary columns
            // for a changed court schedule whose active judiciaries were all removed
            final String courtScheduleId = randomUUID().toString();
            final String hearingId1 = randomUUID().toString();
            final String hearingId2 = randomUUID().toString();
            final List<String> changedCourtScheduleIds = List.of(courtScheduleId);

            final List<Object[]> rows = List.<Object[]>of(
                    new Object[]{courtScheduleId, hearingId1, null, null, null, null},
                    new Object[]{courtScheduleId, hearingId2, null, null, null, null});
            when(courtScheduleJudiciaryService.getJudiciaryHearingInfoForCourtSchedules(eq(changedCourtScheduleIds)))
                    .thenReturn(rows);

            // when
            final List<JsonObject> payloads =
                    changeJudiciaryForHearingsHelper.createChangeJudiciaryForHearingsPayloads(changedCourtScheduleIds);

            // then - the command is still sent, with an empty judiciary array so listing
            // clears the judiciary from the hearings
            assertEquals(1, payloads.size());
            final JsonObject payload = payloads.get(0);
            assertEquals(List.of(hearingId1, hearingId2), toStringList(payload.getJsonArray("hearings")));
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
            when(courtScheduleJudiciaryService.getJudiciaryHearingInfoForCourtSchedules(eq(changedCourtScheduleIds)))
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
