package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

// (removed) Requester replaced by Spring CommonPlatformQueryClient

import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.RotaPeriodDateInfoProvider;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public RotaPeriodDateInfoProvider getRotaPeriodDates(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        return new RotaPeriodDateInfoProvider(records);
    }

    public Map<String, List<CourtScheduleJudiciary>> getCourtScheduleJudiciariesForRotaPeriod(
            final LocalDate rotaPeriodStartDate,
            final LocalDate rotaPeriodEndDate,
            final List<String> ouCodes) {
        final Map<String, List<CourtScheduleJudiciary>> courtScheduleJudiciaryMap =
                courtScheduleJudiciaryService.getCourtScheduleJudiciariesForRotaPeriod(
                        rotaPeriodStartDate, rotaPeriodEndDate, ouCodes);

        logger.info("Captured {} court schedules with unallocated judiciaries for rota period {} to {}",
                courtScheduleJudiciaryMap.size(), rotaPeriodStartDate, rotaPeriodEndDate);

        return courtScheduleJudiciaryMap;
    }

    public int deleteCourtScheduleJudiciariesForRotaPeriod(
            final LocalDate rotaPeriodStartDate,
            final LocalDate rotaPeriodEndDate,
            final List<String> ouCodes) {
        logger.debug("Deleting unallocated court schedule judiciaries for rota period {} to {} with {} OU codes",
                rotaPeriodStartDate, rotaPeriodEndDate, ouCodes.size());
        
        final int deletedCount = courtScheduleJudiciaryService
                .deleteCourtScheduleJudiciariesEntriesForRotaPeriod(
                        rotaPeriodStartDate, rotaPeriodEndDate, ouCodes);
        
        logger.info("Deleted {} unallocated court schedule judiciaries for rota period {} to {}",
                deletedCount, rotaPeriodStartDate, rotaPeriodEndDate);
        
        return deletedCount;
    }
}

