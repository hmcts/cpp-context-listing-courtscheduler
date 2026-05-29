package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.RotaPeriodDateInfoProvider;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for location, OU code, and rota period processing operations from rota file records.
 * Handles extraction of location IDs, mapping them to organizational unit codes, and managing rota period operations.
 */
@Service
public class RotaLocationPeriodHelper {

    private static final Logger logger = LoggerFactory.getLogger(RotaLocationPeriodHelper.class);

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    // ============================================================================
    // Location and OU Code Operations
    // ============================================================================

    /**
     * Extracts location IDs from the parsed rota file records.
     *
     * @param records the parsed rota file records
     * @return list of location IDs extracted from the records
     */
    public List<String> getLocationFromRecords(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> locations = records.get(RotaPayload.LOCATION);

        if (locations == null) {
            logger.debug("No location records found in rota file");
            return new ArrayList<>();
        }

        final List<String> locationIds = locations.entrySet().stream()
                .flatMap(e -> e.getValue().keySet().stream())
                .toList();
        
        logger.debug("Extracted {} location IDs from rota file records", locationIds.size());
        return locationIds;
    }

    /**
     * Gets OU codes from court room mappings by location ID.
     * Maps location IDs from the rota file to organizational unit codes using court room mappings.
     *
     * @param locationIds the list of location IDs from the rota file
     * @param requester   the requester for making reference data queries
     * @return list of OU codes corresponding to the provided location IDs
     */
    public List<String> getOuCodesFromCourtRoomMappingsByLocationId(final List<String> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            logger.debug("No location IDs provided for OU code resolution");
            return new ArrayList<>();
        }

        final Map<String, String> locationIdOuCodeMap = buildLocationIdToOuCodeMap();
        final List<String> ouCodes = resolveOuCodes(locationIds, locationIdOuCodeMap);
        
        logger.info("Resolved {} OU codes from {} location IDs", ouCodes.size(), locationIds.size());
        return ouCodes;
    }

    /**
     * Builds a map of location IDs to OU codes from court room mappings.
     *
     * @param requester the requester for making reference data queries
     * @return map of location ID (as String) to OU code
     */
    private Map<String, String> buildLocationIdToOuCodeMap() {
        final Map<String, String> locationIdOuCodeMap = new HashMap<>();
        referenceDataMapperService.getCourtRoomsMap().values()
                .forEach(courtRoom -> {
                    final String locationId = String.valueOf(courtRoom.getRotaLocationId());
                    if (!locationIdOuCodeMap.containsKey(locationId)) {
                        locationIdOuCodeMap.put(locationId, courtRoom.getOucode());
                    }
                });
        return locationIdOuCodeMap;
    }

    /**
     * Resolves OU codes for the given location IDs.
     *
     * @param locationIds        the list of location IDs to resolve
     * @param locationIdOuCodeMap the map of location IDs to OU codes
     * @return list of OU codes for the provided location IDs
     */
    private List<String> resolveOuCodes(final List<String> locationIds, final Map<String, String> locationIdOuCodeMap) {
        final List<String> ouCodes = new ArrayList<>();
        locationIdOuCodeMap.keySet()
                .forEach(locationId -> {
                    if (locationIds.contains(locationId)) {
                        ouCodes.add(locationIdOuCodeMap.get(locationId));
                    }
                });
        return ouCodes;
    }

    // ============================================================================
    // Rota Period Operations
    // ============================================================================

    /**
     * Extracts rota period dates from the parsed rota file records.
     *
     * @param records the parsed rota file records
     * @return RotaPeriodDateInfoProvider containing the rota period start and end dates
     */
    public RotaPeriodDateInfoProvider getRotaPeriodDates(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        return new RotaPeriodDateInfoProvider(records);
    }

    /**
     * Deletes unallocated court schedule judiciaries for the given rota period and OU codes.
     *
     * @param rotaPeriodStartDate the start date of the rota period
     * @param rotaPeriodEndDate   the end date of the rota period
     * @param ouCodes             the list of OU codes to process
     * @return the number of deleted unallocated court schedule judiciaries
     */
    public int deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(
            final LocalDate rotaPeriodStartDate,
            final LocalDate rotaPeriodEndDate,
            final List<String> ouCodes) {
        logger.debug("Deleting unallocated court schedule judiciaries for rota period {} to {} with {} OU codes",
                rotaPeriodStartDate, rotaPeriodEndDate, ouCodes.size());
        
        final int deletedCount = courtScheduleJudiciaryService
                .deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                        rotaPeriodStartDate, rotaPeriodEndDate, ouCodes);
        
        logger.info("Deleted {} unallocated court schedule judiciaries for rota period {} to {}",
                deletedCount, rotaPeriodStartDate, rotaPeriodEndDate);
        
        return deletedCount;
    }
}

