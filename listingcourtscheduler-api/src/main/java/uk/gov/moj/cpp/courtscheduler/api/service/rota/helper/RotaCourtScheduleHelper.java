package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.DELIMITER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.MISSING_COURT_SESSION;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;

// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for creating court schedule maps from rota file records.
 */
@Service
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
     * Creates a map of court listing profile IDs to sets of CourtSchedule UUIDs.
     * Queries the repository using panel, sessionDate, session, and courtRoomId from each court listing.
     *
     * @param records     the parsed rota file records
     * @param requester   the requester for making reference data queries
     * @param executionId the execution ID for logging purposes
     * @return a map of court listing profile IDs to sets of CourtSchedule UUIDs
     */
    public Map<String, Set<UUID>> createCourtScheduleMap(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                          final String executionId) {
        if (RotaUtils.isEmptyRecords(records)) {
            logger.warn("No records provided to create court schedule map");
            return Collections.emptyMap();
        }

        final Map<String, Map<String, String>> courtListings = RotaUtils.getRecordsByType(records, COURT_LISTING);
        if (courtListings.isEmpty()) {
            logger.debug("No court listings found in records");
            return Collections.emptyMap();
        }

        final Map<String, Set<UUID>> courtScheduleMap = new ConcurrentHashMap<>();
        final Map<String, String> missingReferenceDataMappingMap = new ConcurrentHashMap<>();
        final Map<String, List<String>> missingSessionsByOuCode = new ConcurrentHashMap<>();

        courtListings.forEach((listingProfileId, listingProfile) -> {
            try {
                processCourtListing(listingProfileId, listingProfile, executionId,
                        courtScheduleMap, missingReferenceDataMappingMap, missingSessionsByOuCode);
            } catch (final Exception ex) {
                logger.error("Error processing court listing profile {}: {}", listingProfileId, ex.getMessage(), ex);
            }
        });

                RotaUtils.logMissingReferenceData(rotaProcessLogService, missingReferenceDataMappingMap, executionId, REF_DATA_VENUE_NOT_FOUND);
        logMissingCourtSessions(missingSessionsByOuCode, executionId);

        logger.info("Created court schedule map with {} entries from {} court listings",
                courtScheduleMap.size(), courtListings.size());

        return courtScheduleMap;
    }

    private void processCourtListing(final String listingProfileId,
                                     final Map<String, String> listingProfile,
                                     final String executionId,
                                     final Map<String, Set<UUID>> courtScheduleMap,
                                     final Map<String, String> missingReferenceDataMappingMap,
                                     final Map<String, List<String>> missingSessionsByOuCode) {
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

        final CourtRoom courtRoom = venueCourtRoomHelper.getCourtRoom(listingProfile, executionId, missingReferenceDataMappingMap);
        if (courtRoom == null) {
            logger.debug("Skipping court listing {} - could not determine court room", listingProfileId);
            return;
        }

        final List<CourtSchedule> courtSchedules = findCourtSchedule(courtRoom, sessionDate, session, panel);
        if (isNotEmpty(courtSchedules)) {
            final Set<UUID> courtScheduleIds = courtSchedules.stream()
                    .map(cs -> UUID.fromString(cs.getCourtScheduleId()))
                    .collect(toSet());
            courtScheduleMap.put(listingProfileId, courtScheduleIds);
            logger.debug("Mapped court listing profile {} to {} court schedule(s)",
                    listingProfileId, courtScheduleIds.size());
        } else {
            logger.debug("No court schedule found for listing profile {} with panel: {}, sessionDate: {}, session: {}, courtRoomId: {}",
                    listingProfileId, panel, sessionDate, session, courtRoom.getCourtroomId());
            recordMissingSession(missingSessionsByOuCode, courtRoom, sessionDate, session, panel, listingProfile);
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
                        && RotaUtils.matchesSession(session, cs.getCourtSession()))
                .toList();
    }


    private void logMissingCourtSessions(final Map<String, List<String>> missingSessionsByOuCode, final String executionId) {
        if (missingSessionsByOuCode == null || missingSessionsByOuCode.isEmpty() || !isNotEmpty(executionId)) {
            return;
        }

        missingSessionsByOuCode.forEach((ouCode, sessions) -> {
            final String sessionDetails = sessions.stream()
                    .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                    .distinct()
                    .collect(joining(format(DELIMITER)));
            if (isNotEmpty(sessionDetails)) {
                final String errorText = MISSING_COURT_SESSION.format(ouCode, sessionDetails);
                logger.warn(errorText);
                RotaUtils.logProcessingError(
                        rotaProcessLogService,
                        executionId,
                        MISSING_COURT_SESSION.code(),
                        errorText
                );
            }
        });
    }

    private void recordMissingSession(final Map<String, List<String>> missingSessionsByOuCode,
                                      final CourtRoom courtRoom,
                                      final LocalDate sessionDate,
                                      final String session,
                                      final String panel,
                                      final Map<String, String> listingProfile) {
        final String ouCode = defaultIfBlank(courtRoom.getOucode(), "UNKNOWN_OUCODE");
        final String courtHouseName = defaultIfBlank(courtRoom.getOucodeL3Name(), "UNKNOWN_COURTHOUSE");
        final String courtRoomName = defaultIfBlank(courtRoom.getCourtroomName(), "UNKNOWN_COURTROOM");
        final String businessType = defaultIfBlank(listingProfile.get(BUSINESS_TYPE), "UNKNOWN_BUSINESS_TYPE");
        final String sessionStr = defaultIfBlank(session, "UNKNOWN_SESSION");
        final String panelStr = defaultIfBlank(panel, "UNKNOWN_PANEL");
        
        final String sessionDetails = format("%s - %s - %s - %s - %s - %s",
                sessionDate,
                courtHouseName,
                courtRoomName,
                businessType,
                sessionStr,
                panelStr);
        
        missingSessionsByOuCode
                .computeIfAbsent(ouCode, key -> new ArrayList<>())
                .add(sessionDetails);
    }

}

