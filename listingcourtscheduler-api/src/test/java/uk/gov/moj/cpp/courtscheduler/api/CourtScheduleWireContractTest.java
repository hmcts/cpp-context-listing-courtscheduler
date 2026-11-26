package uk.gov.moj.cpp.courtscheduler.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.moj.cpp.courtscheduler.config.JacksonObjectMapperConfig;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleView;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MiCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalBookingInfo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

/**
 * Pins the JSON wire contract of the court-schedule response payloads.
 *
 * <p>The legacy WildFly service serialized {@link CourtSchedule} with plain Jackson bean naming,
 * so every consumer of the hearing-slots and sessions-by-id responses — cpp-context-listing,
 * cpp-apitests, and the {@code courtscheduler.get.hearing.slots} schema — reads {@code draft}
 * and {@code overbookingAllowed}. {@link CourtScheduleView} (the get-court-schedule sessions
 * view) deliberately differs: its get-style getter and explicit {@code @JsonProperty} keep
 * {@code isDraft}/{@code isOverbookingAllowed}, also matching legacy. Renaming either side
 * breaks cross-context consumers even though every test in this repo stays green.
 */
class CourtScheduleWireContractTest {

    private final ObjectMapper objectMapper = new JacksonObjectMapperConfig().objectMapper();

    @Test
    void courtScheduleKeepsLegacyBeanNamesForDraftAndOverbookingFlags() throws Exception {
        final CourtSchedule courtSchedule = new CourtSchedule()
                .courtScheduleId("30f5b5af-2844-40bd-9bf6-397ad99182c2")
                .draft(true)
                .overbookingAllowed(true);

        final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(courtSchedule));

        assertThat(json.path("draft").asBoolean(), is(true));
        assertThat(json.path("overbookingAllowed").asBoolean(), is(true));
        assertThat(json.has("isDraft"), is(false));
        assertThat(json.has("isOverbookingAllowed"), is(false));
    }

    @Test
    void courtScheduleViewKeepsLegacyIsPrefixedNamesForDraftAndOverbookingFlags() throws Exception {
        final CourtScheduleView view = new CourtScheduleView()
                .courtScheduleId("30f5b5af-2844-40bd-9bf6-397ad99182c2")
                .isDraft(Boolean.TRUE)
                .isOverbookingAllowed(true);

        final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(view));

        assertThat(json.path("isDraft").asBoolean(), is(true));
        assertThat(json.path("isOverbookingAllowed").asBoolean(), is(true));
        assertThat(json.has("draft"), is(false));
        assertThat(json.has("overbookingAllowed"), is(false));
    }

    @Test
    void courtScheduleJudiciaryKeepsLegacyIsPrefixedNamesForBenchChairmanAndDeputy() throws Exception {
        final CourtScheduleJudiciary judiciary = new CourtScheduleJudiciary()
                .judiciaryId("89202350-af98-3d0e-b93e-a79697accf55")
                .isBenchChairman(true)
                .isDeputy(false);

        final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(judiciary));

        assertThat(json.path("isBenchChairman").asBoolean(), is(true));
        assertThat(json.path("isDeputy").isBoolean(), is(true));
        assertThat(json.has("benchChairman"), is(false));
        assertThat(json.has("deputy"), is(false));
    }

    @Test
    void courtScheduleKeepsLegacyMillisecondUtcTimestampFormat() throws Exception {
        final CourtSchedule courtSchedule = new CourtSchedule()
                .sessionStartTime(OffsetDateTime.of(2020, 12, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                .sessionEndTime(OffsetDateTime.of(2020, 12, 1, 14, 0, 0, 0, ZoneOffset.ofHours(1)))
                .nationalBreakTime(OffsetDateTime.of(2020, 12, 1, 13, 0, 0, 0, ZoneOffset.UTC))
                .createdOn(OffsetDateTime.of(2020, 12, 1, 9, 30, 15, 123_000_000, ZoneOffset.UTC));

        final String wire = objectMapper.writeValueAsString(courtSchedule);
        final JsonNode json = objectMapper.readTree(wire);

        assertThat(json.path("sessionStartTime").asText(), is("2020-12-01T10:00:00.000+00:00"));
        assertThat(json.path("sessionEndTime").asText(), is("2020-12-01T13:00:00.000+00:00"));
        assertThat(json.path("nationalBreakTime").asText(), is("2020-12-01T13:00:00.000+00:00"));
        assertThat(json.path("createdOn").asText(), is("2020-12-01T09:30:15.123+00:00"));
        assertThat(objectMapper.readValue(wire, CourtSchedule.class).getSessionStartTime().toInstant(),
                is(courtSchedule.getSessionStartTime().toInstant()));
    }

    @Test
    void allocatedSlotBindsLegacyPoliceAndSlotBasedKeys() throws Exception {
        final AllocatedSlot slot = objectMapper.readValue(
                "{\"hearingId\":\"h1\",\"police\":true,\"slotBased\":true}", AllocatedSlot.class);

        assertThat(slot.getPolice(), is(true));
        assertThat(slot.getSlotBased(), is(true));
    }

    @Test
    void provisionalBookingInfoKeepsInheritedCourtScheduleDefaults() throws Exception {
        final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                new ProvisionalBookingInfo().courtScheduleId("356e3370-efa2-391c-9540-2bc99325481e")));

        assertThat(json.path("slotBased").isBoolean(), is(true));
        assertThat(json.path("active").isBoolean(), is(true));
        assertThat(json.path("allDaySplit").isBoolean(), is(true));
        assertThat(json.path("overbookingAllowed").isBoolean(), is(true));
        assertThat(json.path("draft").isBoolean(), is(true));
        assertThat(json.path("totalBooked").asInt(-1), is(0));
        assertThat(json.path("maxDurationForMorning").asInt(-1), is(0));
        assertThat(json.path("slotStartTimes").isArray(), is(true));
    }

    @Test
    void miCourtScheduleKeepsPreformattedDateStringsVerbatim() throws Exception {
        final MiCourtSchedule row = objectMapper.convertValue(
                Map.of("session_start", "2024-06-01T00:00Z", "created_on", "2024-06-01T09:00Z"),
                MiCourtSchedule.class);

        final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(row));

        assertThat(json.path("session_start").asText(), is("2024-06-01T00:00Z"));
        assertThat(json.path("created_on").asText(), is("2024-06-01T09:00Z"));
    }
}
