package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for comparing judiciary court schedule maps from different sources
 * (rota feed vs database) to identify missing court schedule IDs.
 */
@ApplicationScoped
public class JudiciaryCourtScheduleMapComparator {

    private static final Logger logger = LoggerFactory.getLogger(JudiciaryCourtScheduleMapComparator.class);

    /**
     * Compares the rota feed map and database map to find court schedule IDs that are present
     * in the rota feed but missing in the database for each judiciary ID.
     *
     * @param rotaFeedMap the map from rota feed with judiciary IDs as keys and lists of court schedule IDs as values
     * @param databaseMap the map from database with judiciary IDs as keys and lists of court schedule IDs as values
     * @return a map of judiciary IDs to lists of missing court schedule IDs
     */
    public Map<String, List<UUID>> findMissingCourtScheduleIdsInDB(final Map<String, List<UUID>> rotaFeedMap,
                                                                   final Map<String, List<UUID>> databaseMap) {
        if (rotaFeedMap == null || rotaFeedMap.isEmpty()) {
            logger.debug("No rota feed map provided to find missing court schedule IDs");
            return Collections.emptyMap();
        }

        final Map<String, List<UUID>> result = new ConcurrentHashMap<>();

        rotaFeedMap.forEach((judiciaryId, rotaFeedCourtScheduleIds) -> {
            if (rotaFeedCourtScheduleIds == null || rotaFeedCourtScheduleIds.isEmpty()) {
                logger.debug("No court schedule IDs in rota feed for judiciary ID: {}", judiciaryId);
                return;
            }

            // Get the court schedule IDs from database for this judiciary ID, or empty list if not found
            final List<UUID> databaseCourtScheduleIds = databaseMap != null
                    ? databaseMap.getOrDefault(judiciaryId, Collections.emptyList())
                    : Collections.emptyList();

            // Find court schedule IDs that are in rota feed but not in database
            final List<UUID> missingCourtScheduleIds = rotaFeedCourtScheduleIds.stream()
                    .filter(courtScheduleId -> !databaseCourtScheduleIds.contains(courtScheduleId))
                    .toList();

            if (!missingCourtScheduleIds.isEmpty()) {
                result.put(judiciaryId, new ArrayList<>(missingCourtScheduleIds));
                logger.debug("Found {} missing court schedule ID(s) for judiciary ID: {}",
                        missingCourtScheduleIds.size(), judiciaryId);
            } else {
                logger.debug("No missing court schedule IDs for judiciary ID: {}", judiciaryId);
            }
        });

        final int totalMissing = result.values().stream()
                .mapToInt(List::size)
                .sum();
        logger.debug("Found {} total missing court schedule IDs across {} judiciary IDs",
                totalMissing, result.size());

        return result;
    }

    /**
     * Compares the database map and rota feed map to find court schedule IDs that are present
     * in the database but missing in the rota feed for each judiciary ID.
     *
     * @param databaseMap the map from database with judiciary IDs as keys and lists of court schedule IDs as values
     * @param rotaFeedMap the map from rota feed with judiciary IDs as keys and lists of court schedule IDs as values
     * @return a map of judiciary IDs to lists of missing court schedule IDs (present in database but not in rota feed)
     */
    public Map<String, List<UUID>> findMissingCourtScheduleIdsInRotaFeed(final Map<String, List<UUID>> databaseMap,
                                                                         final Map<String, List<UUID>> rotaFeedMap) {
        if (databaseMap == null || databaseMap.isEmpty()) {
            logger.debug("No database map provided to find missing court schedule IDs in rota feed");
            return Collections.emptyMap();
        }

        final Map<String, List<UUID>> result = new ConcurrentHashMap<>();

        databaseMap.forEach((judiciaryId, databaseCourtScheduleIds) -> {
            if (databaseCourtScheduleIds == null || databaseCourtScheduleIds.isEmpty()) {
                logger.debug("No court schedule IDs in database for judiciary ID: {}", judiciaryId);
                return;
            }

            // Get the court schedule IDs from rota feed for this judiciary ID, or empty list if not found
            final List<UUID> rotaFeedCourtScheduleIds = rotaFeedMap != null
                    ? rotaFeedMap.getOrDefault(judiciaryId, Collections.emptyList())
                    : Collections.emptyList();

            // Find court schedule IDs that are in database but not in rota feed
            final List<UUID> missingCourtScheduleIds = databaseCourtScheduleIds.stream()
                    .filter(courtScheduleId -> !rotaFeedCourtScheduleIds.contains(courtScheduleId))
                    .toList();

            if (!missingCourtScheduleIds.isEmpty()) {
                result.put(judiciaryId, new ArrayList<>(missingCourtScheduleIds));
                logger.debug("Found {} court schedule ID(s) in database missing in rota feed for judiciary ID: {}",
                        missingCourtScheduleIds.size(), judiciaryId);
            } else {
                logger.debug("No missing court schedule IDs in rota feed for judiciary ID: {}", judiciaryId);
            }
        });

        final int totalMissing = result.values().stream()
                .mapToInt(List::size)
                .sum();
        logger.debug("Found {} total court schedule IDs in database missing in rota feed across {} judiciary IDs",
                totalMissing, result.size());

        return result;
    }
}

