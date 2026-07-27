package uk.gov.moj.cpp.courtscheduler.api;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.moj.cpp.courtscheduler.config.JacksonObjectMapperConfig;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleView;

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
        final CourtSchedule courtSchedule = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId("30f5b5af-2844-40bd-9bf6-397ad99182c2")
                .withIsDraft(true)
                .withIsOverbookingAllowed(true)
                .build();

        final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(courtSchedule));

        assertThat(json.path("draft").asBoolean(), is(true));
        assertThat(json.path("overbookingAllowed").asBoolean(), is(true));
        assertThat(json.has("isDraft"), is(false));
        assertThat(json.has("isOverbookingAllowed"), is(false));
    }

    @Test
    void courtScheduleViewKeepsLegacyIsPrefixedNamesForDraftAndOverbookingFlags() throws Exception {
        final CourtScheduleView view = new CourtScheduleView.CourtScheduleViewBuilder()
                .withCourtScheduleId("30f5b5af-2844-40bd-9bf6-397ad99182c2")
                .withIsDraft(Boolean.TRUE)
                .withIsOverbookingAllowed(true)
                .build();

        final JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(view));

        assertThat(json.path("isDraft").asBoolean(), is(true));
        assertThat(json.path("isOverbookingAllowed").asBoolean(), is(true));
        assertThat(json.has("draft"), is(false));
        assertThat(json.has("overbookingAllowed"), is(false));
    }
}
