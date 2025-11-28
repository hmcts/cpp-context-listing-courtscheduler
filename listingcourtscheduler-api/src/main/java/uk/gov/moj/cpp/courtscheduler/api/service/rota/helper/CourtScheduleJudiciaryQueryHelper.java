package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for querying court schedule judiciary data from the database.
 */
@ApplicationScoped
public class CourtScheduleJudiciaryQueryHelper {

    private static final Logger logger = LoggerFactory.getLogger(CourtScheduleJudiciaryQueryHelper.class);

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    /**
     * Queries the court_schedule_judiciary database table for all judiciary IDs from the provided map,
     * groups the court schedule IDs by judiciary ID, and returns them as a map.
     *
     * @param judiciaryCourtScheduleMap the map containing judiciary IDs as keys
     * @return a map of judiciary IDs to lists of CourtSchedule UUIDs from the database
     */
    public Map<String, List<UUID>> queryCourtScheduleIdsByJudiciaryIds(final Map<String, List<UUID>> judiciaryCourtScheduleMap) {
        if (judiciaryCourtScheduleMap == null || judiciaryCourtScheduleMap.isEmpty()) {
            logger.debug("No judiciary IDs provided to query court schedule IDs");
            return Collections.emptyMap();
        }

        final List<String> judiciaryIds = new ArrayList<>(judiciaryCourtScheduleMap.keySet());
        logger.debug("Querying court schedule IDs for {} judiciary IDs", judiciaryIds.size());

        try {
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary> courtScheduleJudiciaries =
                    courtScheduleJudiciaryRepository.findByJudiciaryIds(judiciaryIds);

            final Map<String, List<UUID>> result = new ConcurrentHashMap<>();

            courtScheduleJudiciaries.forEach(csj -> {
                final String judiciaryId = csj.getId().getJudiciaryId();
                final String courtScheduleId = csj.getId().getCourtScheduleId();

                try {
                    final UUID courtScheduleUuid = UUID.fromString(courtScheduleId);
                    result.computeIfAbsent(judiciaryId, k -> new ArrayList<>()).add(courtScheduleUuid);
                } catch (final IllegalArgumentException ex) {
                    logger.warn("Invalid UUID format for court schedule ID: {}", courtScheduleId);
                }
            });

            logger.debug("Queried and grouped {} court schedule IDs for {} judiciary IDs",
                    courtScheduleJudiciaries.size(), result.size());

            return result;
        } catch (final Exception ex) {
            logger.error("Error querying court schedule IDs by judiciary IDs: {}", ex.getMessage(), ex);
            return Collections.emptyMap();
        }
    }
}

