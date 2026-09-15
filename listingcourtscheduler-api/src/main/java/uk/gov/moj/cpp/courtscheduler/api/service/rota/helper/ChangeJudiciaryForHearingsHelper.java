package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.ListingCommandClient;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 */
@Service
public class ChangeJudiciaryForHearingsHelper {

    public static final String CHANGE_JUDICIARY_FOR_HEARINGS_COMMAND = "listing.command.change-judiciary-for-hearings";

    private static final Logger logger = LoggerFactory.getLogger(ChangeJudiciaryForHearingsHelper.class);

    // Column positions of the rows returned by getJudiciaryHearingInfoForCourtSchedules
    private static final int COURT_SCHEDULE_ID = 0;
    private static final int HEARING_ID = 1;
    private static final int JUDICIARY_ID = 2;
    private static final int JUDICIARY_TYPE = 3;
    private static final int IS_BENCH_CHAIRMAN = 4;
    private static final int IS_DEPUTY = 5;

    @Inject
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Inject
    private ListingCommandClient listingCommandClient;

    /**
     * Queries court_schedule, court_schedule_judiciary and allocated_listings for the supplied
     * court schedule IDs and builds one {@code listing.command.change-judiciary-for-hearings}
     * payload per court schedule: the hearing IDs allocated to that schedule, and the judiciaries
     * currently assigned to it (judicialId, judicialRoleType, isBenchChairman, isDeputy).
     * Court schedules without an allocated hearing or without an active judiciary produce no payload.
     *
     * @param changedCourtScheduleIds the court schedule IDs whose judiciaries changed
     * @return the change-judiciary-for-hearings payloads, one per court schedule with data
     */
    public List<JsonObject> createChangeJudiciaryForHearingsPayloads(final List<String> changedCourtScheduleIds) {
        if (changedCourtScheduleIds == null || changedCourtScheduleIds.isEmpty()) {
            logger.debug("No changed court schedule IDs provided - no change-judiciary-for-hearings payloads to build");
            return List.of();
        }

        final List<Object[]> judiciaryHearingRows =
                courtScheduleJudiciaryService.getJudiciaryHearingInfoForCourtSchedules(changedCourtScheduleIds);
        logger.info("Retrieved {} judiciary/hearing rows for {} changed court schedule IDs",
                judiciaryHearingRows.size(), changedCourtScheduleIds.size());

        final Map<String, ScheduleJudiciaryHearings> scheduleDataByCourtScheduleId =
                groupRowsByCourtScheduleId(judiciaryHearingRows);

        final List<JsonObject> payloads = scheduleDataByCourtScheduleId.values().stream()
                .map(this::buildChangeJudiciaryForHearingsPayload)
                .toList();

        logger.info("Built {} change-judiciary-for-hearings payloads from {} changed court schedule IDs",
                payloads.size(), changedCourtScheduleIds.size());
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
     * Groups the (hearing, judiciary) rows by court schedule ID, de-duplicating the hearing IDs
     * and judiciaries that the join repeats for every combination.
     */
    private Map<String, ScheduleJudiciaryHearings> groupRowsByCourtScheduleId(final List<Object[]> judiciaryHearingRows) {
        final Map<String, ScheduleJudiciaryHearings> scheduleDataByCourtScheduleId = new LinkedHashMap<>();

        judiciaryHearingRows.forEach(row -> {
            final String courtScheduleId = (String) row[COURT_SCHEDULE_ID];
            final ScheduleJudiciaryHearings scheduleData = scheduleDataByCourtScheduleId
                    .computeIfAbsent(courtScheduleId, key -> new ScheduleJudiciaryHearings(new LinkedHashSet<>(), new LinkedHashMap<>()));

            scheduleData.hearingIds().add((String) row[HEARING_ID]);
            scheduleData.judiciariesByJudiciaryId().computeIfAbsent((String) row[JUDICIARY_ID],
                    judiciaryId -> buildJudicialRole(judiciaryId, row));
        });

        return scheduleDataByCourtScheduleId;
    }

    private JsonObject buildJudicialRole(final String judiciaryId, final Object[] row) {
        final JsonObjectBuilder judicialRoleBuilder = Json.createObjectBuilder()
                .add("judicialId", judiciaryId)
                .add("judicialRoleType", (String) row[JUDICIARY_TYPE]);

        if (row[IS_BENCH_CHAIRMAN] != null) {
            judicialRoleBuilder.add("isBenchChairman", (Boolean) row[IS_BENCH_CHAIRMAN]);
        }
        if (row[IS_DEPUTY] != null) {
            judicialRoleBuilder.add("isDeputy", (Boolean) row[IS_DEPUTY]);
        }
        return judicialRoleBuilder.build();
    }

    private JsonObject buildChangeJudiciaryForHearingsPayload(final ScheduleJudiciaryHearings scheduleData) {
        final JsonArrayBuilder hearingsBuilder = Json.createArrayBuilder();
        scheduleData.hearingIds().forEach(hearingsBuilder::add);

        final JsonArrayBuilder judiciaryBuilder = Json.createArrayBuilder();
        scheduleData.judiciariesByJudiciaryId().values().forEach(judiciaryBuilder::add);

        return Json.createObjectBuilder()
                .add("hearings", hearingsBuilder)
                .add("judiciary", judiciaryBuilder)
                .build();
    }

    /**
     * The de-duplicated hearing IDs and judiciaries for one court schedule.
     *
     * @param hearingIds               the hearing IDs allocated to the court schedule
     * @param judiciariesByJudiciaryId the judicialRole payload fragments, keyed by judiciary ID
     */
    private record ScheduleJudiciaryHearings(Set<String> hearingIds, Map<String, JsonObject> judiciariesByJudiciaryId) {
    }
}
