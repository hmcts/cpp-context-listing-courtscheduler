package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.DELIMITER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.time.LocalDate;
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
 * Helper class for creating court schedule maps from rota file records.
 */
@ApplicationScoped
public class RotaCourtScheduleHelper {

    private static final Logger logger = LoggerFactory.getLogger(RotaCourtScheduleHelper.class);

    @Inject
    private DateParsingUtility dateParsingUtility;

    @Inject
    private VenueCourtRoomHelper venueCourtRoomHelper;

    @Inject
    private SessionsService sessionsService;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    /**
     * Creates a map of court listing profile IDs to lists of CourtSchedule UUIDs.
     * Queries the repository using panel, sessionDate, session, and courtRoomId from each court listing.
     *
     * @param records     the parsed rota file records
     * @param requester   the requester for making reference data queries
     * @param executionId the execution ID for logging purposes
     * @return a map of court listing profile IDs to lists of CourtSchedule UUIDs
     */
    public Map<String, List<UUID>> createCourtScheduleMap(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                          final Requester requester,
                                                          final String executionId) {
        if (isEmptyRecords(records)) {
            logger.warn("No records provided to create court schedule map");
            return Collections.emptyMap();
        }

        final Map<String, Map<String, String>> courtListings = getRecordsByType(records, COURT_LISTING);
        if (courtListings.isEmpty()) {
            logger.debug("No court listings found in records");
            return Collections.emptyMap();
        }

        final Map<String, List<UUID>> courtScheduleMap = new ConcurrentHashMap<>();
        final Map<String, String> missingReferenceDataMappingMap = new ConcurrentHashMap<>();

        courtListings.forEach((listingProfileId, listingProfile) -> {
            try {
                processCourtListing(listingProfileId, listingProfile, requester, executionId,
                        courtScheduleMap, missingReferenceDataMappingMap);
            } catch (final Exception ex) {
                logger.error("Error processing court listing profile {}: {}", listingProfileId, ex.getMessage(), ex);
            }
        });

        logMissingReferenceData(missingReferenceDataMappingMap, executionId);

        logger.debug("Created court schedule map with {} entries from {} court listings",
                courtScheduleMap.size(), courtListings.size());

        return courtScheduleMap;
    }

    private void processCourtListing(final String listingProfileId,
                                     final Map<String, String> listingProfile,
                                     final Requester requester,
                                     final String executionId,
                                     final Map<String, List<UUID>> courtScheduleMap,
                                     final Map<String, String> missingReferenceDataMappingMap) {
        final String panel = listingProfile.get(PANEL);
        final String sessionDateStr = listingProfile.get(SESSION_DATE);
        final String session = listingProfile.get(SESSION);

        if (!isNotEmpty(panel) || !isNotEmpty(sessionDateStr) || !isNotEmpty(session)) {
            logger.debug("Skipping court listing {} - missing required fields (panel, sessionDate, or session)", listingProfileId);
            return;
        }

        final LocalDate sessionDate = dateParsingUtility.parseSessionDate(sessionDateStr);
        if (sessionDate == null) {
            logger.warn("Skipping court listing {} - invalid session date: {}", listingProfileId, sessionDateStr);
            return;
        }

        final CourtRoom courtRoom = venueCourtRoomHelper.getCourtRoom(listingProfile, requester, executionId, missingReferenceDataMappingMap);
        if (courtRoom == null) {
            logger.debug("Skipping court listing {} - could not determine court room", listingProfileId);
            return;
        }

        final List<CourtSchedule> courtSchedules = findCourtSchedule(courtRoom, sessionDate, session, panel);
        if (isNotEmpty(courtSchedules)) {
            final List<UUID> courtScheduleIds = courtSchedules.stream()
                    .map(cs -> UUID.fromString(cs.getCourtScheduleId()))
                    .toList();
            courtScheduleMap.put(listingProfileId, courtScheduleIds);
            logger.debug("Mapped court listing profile {} to {} court schedule(s)",
                    listingProfileId, courtScheduleIds.size());
        } else {
            logger.debug("No court schedule found for listing profile {} with panel: {}, sessionDate: {}, session: {}, courtRoomId: {}",
                    listingProfileId, panel, sessionDate, session, courtRoom.getCourtroomId());
        }
    }

    private List<CourtSchedule> findCourtSchedule(final CourtRoom courtRoom,
                                                  final LocalDate sessionDate,
                                                  final String session,
                                                  final String panel) {
        try {
            final String ouCode = courtRoom.getOucode();
            final String courtRoomId = courtRoom.getCourtroomId();

            if (!isNotEmpty(ouCode) || !isNotEmpty(courtRoomId)) {
                logger.debug("Missing ouCode or courtRoomId from court room");
                return Collections.emptyList();
            }

            final List<CourtSchedule> courtSchedules = sessionsService.getExtractedCourtSchedules(
                    List.of(ouCode), sessionDate, sessionDate);

            return filterCourtSchedules(courtSchedules, courtRoomId, sessionDate, session, panel);
        } catch (final Exception ex) {
            logger.warn("Error finding court schedule for courtRoomId: {}, sessionDate: {}, session: {}",
                    courtRoom.getCourtroomId(), sessionDate, session, ex);
            return Collections.emptyList();
        }
    }

    private List<CourtSchedule> filterCourtSchedules(final List<CourtSchedule> courtSchedules,
                                                     final String courtRoomId,
                                                     final LocalDate sessionDate,
                                                     final String session,
                                                     final String panel) {
        return courtSchedules.stream()
                .filter(cs -> panel.equals(cs.getPanel())
                        && courtRoomId.equals(cs.getCourtRoomId())
                        && sessionDate.equals(cs.getSessionDate())
                        && matchesSession(session, cs.getCourtSession()))
                .toList();
    }

    /**
     * Checks if the session values match, considering that 'AD' (All Day) can match both 'AM' and 'PM' sessions.
     * 
     * @param requestedSession the session value from the rota file (AM, PM, or AD)
     * @param courtScheduleSession the session value from the court schedule (AM, PM, or AD)
     * @return true if sessions match according to the matching rules
     */
    private boolean matchesSession(final String requestedSession, final String courtScheduleSession) {
        if (requestedSession == null || courtScheduleSession == null) {
            return false;
        }
        
        // Exact match
        if (requestedSession.equals(courtScheduleSession)) {
            return true;
        }
        
        // If requested session is AM, also match AD
        if (AM_SESSION.equals(requestedSession) && ALL_DAY.equals(courtScheduleSession)) {
            return true;
        }
        
        // If requested session is PM, also match AD
        if (PM_SESSION.equals(requestedSession) && ALL_DAY.equals(courtScheduleSession)) {
            return true;
        }
        
        return false;
    }

    private void logMissingReferenceData(final Map<String, String> missingReferenceDataMappingMap, final String executionId) {
        if (!missingReferenceDataMappingMap.isEmpty() && isNotEmpty(executionId)) {
            final String venueDetails = missingReferenceDataMappingMap.entrySet()
                    .stream()
                    .filter(e -> REF_DATA_VENUE_NOT_FOUND.code().equals(e.getValue()))
                    .map(Map.Entry::getKey)
                    .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                    .distinct()
                    .collect(joining(format(DELIMITER)));
            if (isNotEmpty(venueDetails)) {
                final String msg = REF_DATA_VENUE_NOT_FOUND.format(venueDetails);
                rotaProcessLogService.saveRotaProcessLog(
                        rotaProcessLog()
                                .withExecutionId(executionId)
                                .withErrorCode(REF_DATA_VENUE_NOT_FOUND.code())
                                .withErrorText(msg)
                                .build()
                );
            }
        }
    }

    private boolean isEmptyRecords(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        return records == null || records.isEmpty();
    }

    private Map<String, Map<String, String>> getRecordsByType(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                              final RotaPayload payloadType) {
        return records.getOrDefault(payloadType, Collections.emptyMap());
    }
}

