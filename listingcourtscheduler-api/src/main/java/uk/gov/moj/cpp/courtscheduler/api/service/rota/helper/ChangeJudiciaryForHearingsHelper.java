package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.ListingCommandClient;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Helper class for building {@code listing.command.change-judiciary-for-hearings} payloads
 * for the listing context, from the court schedules whose judiciaries changed during rota
 * file processing.
 *
 * <p>Listing holds one judiciary per hearing, while a hearing may be allocated to several
 * sessions (court schedules). A hearing is therefore only sent to listing when every one of
 * its sessions has the same judiciary; hearings whose sessions differ are skipped.</p>
 */
@Service
public class ChangeJudiciaryForHearingsHelper {

    private static final String JUDICIARY_ASSIGNMENT_SOURCE_AUTO = "AUTO";

    private static final Logger logger = LoggerFactory.getLogger(ChangeJudiciaryForHearingsHelper.class);

    // Column positions of the rows returned by getHearingSessionJudiciariesForCourtSchedules
    private static final int HEARING_ID = 0;
    private static final int COURT_SCHEDULE_ID = 1;
    private static final int JUDICIARY_ID = 2;
    private static final int JUDICIARY_TYPE = 3;
    private static final int IS_BENCH_CHAIRMAN = 4;
    private static final int IS_DEPUTY = 5;

    @Inject
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Inject
    private ListingCommandClient listingCommandClient;

    /**
     * Finds the hearings allocated to the supplied court schedule IDs, along with the judiciaries
     * of every session each hearing is allocated to, and builds one
     * {@code listing.command.change-judiciary-for-hearings} payload per hearing whose sessions all
     * have the same judiciaries (judicialId, judicialRoleType, isBenchChairman, isDeputy).
     * A hearing whose sessions have no judiciaries at all still produces a payload, with an empty
     * {@code judiciary} array, so listing clears the judiciary from the hearing. A hearing whose
     * sessions have different judiciaries produces no payload.
     *
     * @param changedCourtScheduleIds the court schedule IDs whose judiciaries changed
     * @return the change-judiciary-for-hearings payloads, one per hearing with a consistent judiciary
     */
    public List<JsonObject> createChangeJudiciaryForHearingsPayloads(final List<String> changedCourtScheduleIds) {
        if (changedCourtScheduleIds == null || changedCourtScheduleIds.isEmpty()) {
            logger.info("No changed court schedule IDs provided - no change-judiciary-for-hearings payloads to build");
            return List.of();
        }

        final List<Object[]> hearingSessionJudiciaryRows =
                courtScheduleJudiciaryService.getHearingSessionJudiciariesForCourtSchedules(changedCourtScheduleIds);
        logger.info("Retrieved {} hearing/session/judiciary rows for {} changed court schedule IDs",
                hearingSessionJudiciaryRows.size(), changedCourtScheduleIds.size());

        final Map<String, Map<String, Map<String, JsonObject>>> sessionJudiciariesByHearingId =
                groupRowsByHearingIdAndSession(hearingSessionJudiciaryRows);

        final Map<Map<String, JsonObject>, List<String>> hearingIdsByJudiciarySet = new LinkedHashMap<>();
        sessionJudiciariesByHearingId.forEach((hearingId, judiciariesBySession) -> {
            final Set<Map<String, JsonObject>> distinctSessionJudiciaries = new HashSet<>(judiciariesBySession.values());
            if (distinctSessionJudiciaries.size() == 1) {
                hearingIdsByJudiciarySet
                        .computeIfAbsent(distinctSessionJudiciaries.iterator().next(), k -> new ArrayList<>())
                        .add(hearingId);
            } else {
                logger.info("Skipping change-judiciary-for-hearings for hearing {} - its {} sessions {} do not all have the same judiciaries",
                        hearingId, judiciariesBySession.size(), judiciariesBySession.keySet());
            }
        });

        final List<JsonObject> payloads = new ArrayList<>();
        hearingIdsByJudiciarySet.forEach((judiciarySet, hearingIds) ->
                payloads.add(buildChangeJudiciaryForHearingsPayload(hearingIds, judiciarySet)));

        logger.info("Built {} change-judiciary-for-hearings payloads covering {} hearings from {} changed court schedule IDs",
                payloads.size(), sessionJudiciariesByHearingId.size(), changedCourtScheduleIds.size());
        return payloads;
    }

    /**
     * Sends each {@code listing.command.change-judiciary-for-hearings} payload to the listing
     * context's command API. A failure on one payload is logged and does not stop the remaining
     * payloads from being sent.
     *
     * @param changeJudiciaryForHearingsPayloads the payloads built by
     *                                           {@link #createChangeJudiciaryForHearingsPayloads}
     * @return the number of payloads sent successfully
     */
    public int sendChangeJudiciaryForHearingsCommands(final List<JsonObject> changeJudiciaryForHearingsPayloads) {
        if (changeJudiciaryForHearingsPayloads == null || changeJudiciaryForHearingsPayloads.isEmpty()) {
            logger.debug("No change-judiciary-for-hearings payloads to send");
            return 0;
        }

        int sentCount = 0;
        for (final JsonObject payload : changeJudiciaryForHearingsPayloads) {
            try {
                listingCommandClient.changeJudiciaryForHearings(payload);
                sentCount++;
            } catch (final RuntimeException ex) {
                logger.error("Failed to send change-judiciary-for-hearings command for payload: {}", payload, ex);
            }
        }

        logger.info("Sent {} of {} change-judiciary-for-hearings commands to the listing context",
                sentCount, changeJudiciaryForHearingsPayloads.size());
        return sentCount;
    }

    /**
     * Groups the rows by hearing ID and then by session (court schedule ID), de-duplicating the
     * judiciaries per session. Rows with a null judiciary ID (a session without active judiciaries)
     * still register the session, with no judiciaries.
     */
    private Map<String, Map<String, Map<String, JsonObject>>> groupRowsByHearingIdAndSession(final List<Object[]> hearingSessionJudiciaryRows) {
        final Map<String, Map<String, Map<String, JsonObject>>> sessionJudiciariesByHearingId = new LinkedHashMap<>();

        hearingSessionJudiciaryRows.forEach(row -> {
            final Map<String, JsonObject> sessionJudiciariesByJudiciaryId = sessionJudiciariesByHearingId
                    .computeIfAbsent((String) row[HEARING_ID], key -> new LinkedHashMap<>())
                    .computeIfAbsent((String) row[COURT_SCHEDULE_ID], key -> new LinkedHashMap<>());

            final String judiciaryId = (String) row[JUDICIARY_ID];
            if (judiciaryId != null) {
                sessionJudiciariesByJudiciaryId.computeIfAbsent(judiciaryId, id -> buildJudicialRole(id, row));
            }
        });

        return sessionJudiciariesByHearingId;
    }

    private JsonObject buildJudicialRole(final String judiciaryId, final Object[] row) {
        // judicialRoleType is typed by listing's judicialRole.json as a judicialRoleType.json
        // object, not a bare string
        final JsonObjectBuilder judicialRoleBuilder = Json.createObjectBuilder()
                .add("judicialId", judiciaryId)
                .add("judicialRoleType", Json.createObjectBuilder()
                        .add("judiciaryType", (String) row[JUDICIARY_TYPE]));

        if (row[IS_BENCH_CHAIRMAN] != null) {
            judicialRoleBuilder.add("isBenchChairman", (Boolean) row[IS_BENCH_CHAIRMAN]);
        }
        if (row[IS_DEPUTY] != null) {
            judicialRoleBuilder.add("isDeputy", (Boolean) row[IS_DEPUTY]);
        }
        return judicialRoleBuilder.build();
    }

    private JsonObject buildChangeJudiciaryForHearingsPayload(final List<String> hearingIds, final Map<String, JsonObject> judiciariesByJudiciaryId) {
        final JsonArrayBuilder hearingsBuilder = Json.createArrayBuilder();
        hearingIds.forEach(hearingsBuilder::add);

        final JsonArrayBuilder judiciaryBuilder = Json.createArrayBuilder();
        judiciariesByJudiciaryId.values().forEach(judiciaryBuilder::add);

        return Json.createObjectBuilder()
                .add("hearings", hearingsBuilder)
                .add("judiciary", judiciaryBuilder)
                .add("judiciaryAssignmentSource", JUDICIARY_ASSIGNMENT_SOURCE_AUTO)
                .build();
    }
}
