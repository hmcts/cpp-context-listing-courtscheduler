package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.EMAIL_ADDRESS;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.LOCATION_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGISTRATE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGISTRATE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.VENUE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.VENUE_NAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.SCHEDULE;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileNamePrefix;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.JudiciaryBuilder;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.service.RotaReferenceDataValidationService;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
public class RotaFileProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileProcessor.class);
    private static final String SNAPSHOT_NAME_PART = "_snapshot_";
    private static final String DUMMY_NAME_PART = "dummysupport";
    private static final long NANOSECONDS_TO_MILLISECONDS = 1_000_000L;
    private static final String EMPTY_STRING = "";
    private static final String SNAPSHOT_FILE_PROCESSING_SKIPPED = "Snapshot file processing skipped";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AzureBlobClientService azureBlobClientService;
    private final RotaFileParser rotaFileParser;
    private final RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;
    private final RotaFileProcessHistoryService rotaFileProcessHistoryService;
    private final RotaReferenceDataValidationService referenceDataValidationService;
    private final SessionsService sessionsService;
    private final JudiciaryBuilder judiciaryBuilder;

    @Inject
    public RotaFileProcessor(final AzureBlobClientService azureBlobClientService,
                           final RotaFileParser rotaFileParser,
                           final RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository,
                           final RotaFileProcessHistoryService rotaFileProcessHistoryService,
                           final RotaReferenceDataValidationService referenceDataValidationService,
                           final SessionsService sessionsService,
                           final JudiciaryBuilder judiciaryBuilder) {
        this.azureBlobClientService = azureBlobClientService;
        this.rotaFileParser = rotaFileParser;
        this.rotaFileProcessHistoryRepository = rotaFileProcessHistoryRepository;
        this.rotaFileProcessHistoryService = rotaFileProcessHistoryService;
        this.referenceDataValidationService = referenceDataValidationService;
        this.sessionsService = sessionsService;
        this.judiciaryBuilder = judiciaryBuilder;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void downloadAndProcessForEachFile(final Requester requester, final BlobContent blobContent, final String blobName, final String leaseId) {
        logger.info("downloadAndProcessForEachFile called for blob with name: {}", blobName);
        final byte[] blobByteArray = blobContent.getBlobByteArray();
        try {
            processBlob(blobName, blobByteArray, requester);
            uploadAndCleanup(blobByteArray, blobName, leaseId);
        } catch (final RuntimeException ex) {
            logger.error("Error processing blob: {}", blobName, ex);
            azureBlobClientService.releaseLease(blobName, leaseId, true);
        }
    }

    private void processBlob(final String blobName, final byte[] blobByteArray, final Requester requester) {
        final long processStart = System.nanoTime();
        final ParseResult parseResult = parseFileContent(blobName, blobByteArray, requester);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = parseResult.records();
        final String executionId = parseResult.executionId();
        final long processEnd = System.nanoTime();
        
        logger.info("PRF: Processing and parsing completed for blob {} in {} ms", 
                blobName, convertNanosToMillis(processEnd - processStart));
        logger.info("rota file parsed successfully for blob with name: {} - parsed {} record types", 
                blobName, records.size());
        
        final Map<String, Judiciary> judiciaryMap = createJudiciaryMap(records, requester, executionId);
        logger.info("Created judiciary map with {} entries for blob: {}", judiciaryMap.size(), blobName);
        
        final Map<String, List<CourtSchedule>> courtScheduleMap = createCourtScheduleMap(records, requester, executionId);
        logger.info("Created court schedule map with {} entries for blob: {}", courtScheduleMap.size(), blobName);
        
        final List<CourtScheduleJudiciary> scheduleJudiciaryList = createScheduleJudiciaryList(records, requester, executionId);
        logger.info("Created schedule judiciary list with {} entries for blob: {}", scheduleJudiciaryList.size(), blobName);
    }

    private void uploadAndCleanup(final byte[] blobByteArray, final String blobName, final String leaseId) {
        final long uploadStart = System.nanoTime();
        final long fileLength = blobByteArray.length;
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(blobByteArray), fileLength, blobName, empty());
        final long uploadEnd = System.nanoTime();
        logger.info("PRF: Upload completed for blob {} in {} ms", blobName, convertNanosToMillis(uploadEnd - uploadStart));
        
        azureBlobClientService.releaseLease(blobName, leaseId, false);
        azureBlobClientService.deleteFile(blobName, empty());
        logger.info("Blob {} processed and cleaned up", blobName);
    }

    private long convertNanosToMillis(final long nanos) {
        return nanos / NANOSECONDS_TO_MILLISECONDS;
    }

    /**
     * Processes the rota file and parses it, returning the parsed records and execution ID.
     * Handles snapshot file validation, execution ID generation, and file parsing.
     *
     * @param fileName the name of the file being processed
     * @param content the byte content of the file
     * @param requester the requester for making service calls
     * @return ParseResult containing the parsed records and execution ID
     */
    public ParseResult parseFileContent(final String fileName, final byte[] content, final Requester requester) {
        final String executionId = processSnapshotFileIfNeeded(fileName, content);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = parseFile(fileName, content);
        return new ParseResult(records, executionId);
    }

    private String processSnapshotFileIfNeeded(final String fileName, final byte[] content) {
        if (!isSnapshotFile(fileName)) {
            return EMPTY_STRING;
        }
        
        if (isNewerSnapshotFileProcessed(fileName)) {
            logger.warn("Skipping snapshot file - newer version already processed: {}", fileName);
            throw new IllegalStateException(SNAPSHOT_FILE_PROCESSING_SKIPPED);
        }
        
        logger.info("DD-15703:processSnapshotRotaFile: before rotaFileProcessHistoryRepository.save");
        final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
        final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
        final String executionId = randomUUID().toString();
        rotaFileProcessHistoryService.save(fileNamePrefix, fileDateTime, content, executionId);
        logger.info("DD-15703:processSnapshotRotaFile: after rotaFileProcessHistoryRepository.save - executionId: {}", executionId);
        return executionId;
    }

    private boolean isSnapshotFile(final String fileName) {
        return fileName.contains(SNAPSHOT_NAME_PART);
    }

    private boolean isDummyFile(final String fileName) {
        return fileName.contains(DUMMY_NAME_PART);
    }

    private Map<RotaPayload, Map<String, Map<String, String>>> parseFile(final String fileName, final byte[] content) {
        final long parsingStartTime = System.nanoTime();
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(fileName, content);
        final long parsingEndTime = System.nanoTime();
        
        logger.info("PRF: Parsed file {} in {} ms", fileName, convertNanosToMillis(parsingEndTime - parsingStartTime));
        logger.info("File parsed successfully for file: {}", fileName);
        
        if (isDummyFile(fileName)) {
            logger.warn("Dummy support file detected: {}", fileName);
        }
        
        return records;
    }

    private boolean isNewerSnapshotFileProcessed(final String fileName) {
        final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
        if (isNull(fileDateTime)) {
            logger.warn("Invalid file date/time in fileName: {}", fileName);
            return true;
        }
        
        final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
        final List<RotaFileProcessHistory> newerFiles = rotaFileProcessHistoryRepository
                .findByFileNamePrefixAndFileDateGreaterThan(fileNamePrefix, Timestamp.from(fileDateTime.toInstant()));
        
        if (isNotEmpty(newerFiles)) {
            logger.warn("Newer snapshot file already processed for prefix: {}", fileNamePrefix);
            return true;
        }
        
        return false;
    }

    /**
     * Creates a map of magistrate/district judge IDs to their validated Judiciary objects.
     * The map contains entries for magistrates and district judges found in the parsed records,
     * where each entry's key is the magistrate/judge ID and the value is the Judiciary object
     * returned from validateAndFindJudiciaryByEmail.
     *
     * @param records the parsed rota file records
     * @param requester the requester for making reference data queries
     * @param executionId the execution ID for logging purposes
     * @return a map of magistrate/judge IDs to Judiciary objects
     */
    public Map<String, Judiciary> createJudiciaryMap(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                      final Requester requester,
                                                      final String executionId) {
        if (isEmptyRecords(records)) {
            logger.warn("No records provided to create judiciary map");
            return Collections.emptyMap();
        }
        
        final Map<String, Judiciary> judiciaryMap = new ConcurrentHashMap<>();
        final Map<String, Map<String, String>> magistrates = getRecordsByType(records, RotaPayload.MAGISTRATES);
        final Map<String, Map<String, String>> districtJudges = getRecordsByType(records, RotaPayload.DISTRICT_JUDGES);
        
        processJudiciaries(magistrates, MAGS_EMAIL, requester, executionId, judiciaryMap, "magistrate");
        processJudiciaries(districtJudges, JUDGE_EMAIL, requester, executionId, judiciaryMap, "district judge");
        
        logger.debug("Created judiciary map with {} entries ({} magistrates, {} district judges)",
                judiciaryMap.size(), magistrates.size(), districtJudges.size());
        
        return judiciaryMap;
    }

    private void processJudiciaries(final Map<String, Map<String, String>> judiciaries,
                                    final String emailFieldName,
                                    final Requester requester,
                                    final String executionId,
                                    final Map<String, Judiciary> judiciaryMap,
                                    final String judiciaryType) {
        judiciaries.forEach((judiciaryId, judiciaryData) -> {
            if (judiciaryData == null || judiciaryData.isEmpty()) {
                logger.debug("Skipping {} {} - no data available", judiciaryType, judiciaryId);
                return;
            }
            
            final String email = judiciaryData.get(emailFieldName);
            if (!isNotEmpty(email)) {
                logger.debug("Skipping {} {} - no email address found", judiciaryType, judiciaryId);
                return;
            }
            
            referenceDataValidationService.validateAndFindJudiciaryByEmail(requester, email, executionId)
                    .ifPresent(judiciary -> {
                        judiciaryMap.put(judiciaryId, judiciary);
                        logger.debug("Mapped {} {} to judiciary with ID: {}", judiciaryType, judiciaryId, judiciary.getId());
                    });
        });
    }

    /**
     * Creates a map of court listing profile IDs to lists of CourtSchedule objects.
     * Queries the repository using panel, sessionDate, session, and courtRoomId from each court listing.
     *
     * @param records the parsed rota file records
     * @param requester the requester for making reference data queries
     * @param executionId the execution ID for logging purposes
     * @return a map of court listing profile IDs to lists of CourtSchedule objects
     */
    public Map<String, List<CourtSchedule>> createCourtScheduleMap(final Map<RotaPayload, Map<String, Map<String, String>>> records,
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
        
        final Map<String, List<CourtSchedule>> courtScheduleMap = new ConcurrentHashMap<>();
        final Map<String, String> missingReferenceDataMappingMap = new ConcurrentHashMap<>();
        
        courtListings.forEach((listingProfileId, listingProfile) -> {
            try {
                processCourtListing(listingProfileId, listingProfile, requester, executionId, 
                        courtScheduleMap, missingReferenceDataMappingMap);
            } catch (final Exception ex) {
                logger.error("Error processing court listing profile {}: {}", listingProfileId, ex.getMessage(), ex);
            }
        });
        
        logger.debug("Created court schedule map with {} entries from {} court listings", 
                courtScheduleMap.size(), courtListings.size());
        
        return courtScheduleMap;
    }

    private void processCourtListing(final String listingProfileId,
                                     final Map<String, String> listingProfile,
                                     final Requester requester,
                                     final String executionId,
                                     final Map<String, List<CourtSchedule>> courtScheduleMap,
                                     final Map<String, String> missingReferenceDataMappingMap) {
        final String panel = listingProfile.get(PANEL);
        final String sessionDateStr = listingProfile.get(SESSION_DATE);
        final String session = listingProfile.get(SESSION);
        
        if (!isNotEmpty(panel) || !isNotEmpty(sessionDateStr) || !isNotEmpty(session)) {
            logger.debug("Skipping court listing {} - missing required fields (panel, sessionDate, or session)", listingProfileId);
            return;
        }
        
        final LocalDate sessionDate = parseSessionDate(sessionDateStr);
        if (sessionDate == null) {
            logger.warn("Skipping court listing {} - invalid session date: {}", listingProfileId, sessionDateStr);
            return;
        }
        
        final CourtRoom courtRoom = getCourtRoom(listingProfile, requester, executionId, missingReferenceDataMappingMap);
        if (courtRoom == null) {
            logger.debug("Skipping court listing {} - could not determine court room", listingProfileId);
            return;
        }
        
        final List<CourtSchedule> courtSchedules = findCourtSchedule(courtRoom, sessionDate, session, panel);
        if (!courtSchedules.isEmpty()) {
            courtScheduleMap.put(listingProfileId, courtSchedules);
            logger.debug("Mapped court listing profile {} to {} court schedule(s)", 
                    listingProfileId, courtSchedules.size());
        } else {
            logger.debug("No court schedule found for listing profile {} with panel: {}, sessionDate: {}, session: {}, courtRoomId: {}", 
                    listingProfileId, panel, sessionDate, session, courtRoom.getCourtroomId());
        }
    }

    private CourtRoom getCourtRoom(final Map<String, String> listingProfile,
                                   final Requester requester,
                                   final String executionId,
                                   final Map<String, String> missingReferenceDataMappingMap) {
        final String locationIdStr = listingProfile.get(LOCATION_ID);
        final String venueIdStr = listingProfile.get(VENUE_ID);
        final String venueName = listingProfile.get(VENUE_NAME);
        
        if (!isNotEmpty(locationIdStr) || !isNotEmpty(venueIdStr) || !isNotEmpty(venueName)) {
            logger.debug("Missing venue information for court listing");
            return null;
        }
        
        return parseAndValidateVenue(locationIdStr, venueIdStr, venueName, requester, executionId, missingReferenceDataMappingMap);
    }

    private CourtRoom parseAndValidateVenue(final String locationIdStr,
                                            final String venueIdStr,
                                            final String venueName,
                                            final Requester requester,
                                            final String executionId,
                                            final Map<String, String> missingReferenceDataMappingMap) {
        try {
            final Integer locationId = Integer.parseInt(locationIdStr);
            final Integer venueId = Integer.parseInt(venueIdStr);
            final Venue venue = new Venue(locationId, venueId, venueName);
            
            return referenceDataValidationService.validateAndFindVenue(venue, missingReferenceDataMappingMap, requester, executionId)
                    .orElse(null);
        } catch (final NumberFormatException ex) {
            logger.warn("Invalid locationId or venueId format: locationId={}, venueId={}", locationIdStr, venueIdStr);
            return null;
        }
    }

    private LocalDate parseSessionDate(final String sessionDateStr) {
        try {
            return LocalDate.parse(sessionDateStr, DATE_FORMATTER);
        } catch (final Exception ex) {
            logger.warn("Failed to parse session date: {}", sessionDateStr, ex);
            return null;
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
                        && session.equals(cs.getCourtSession()))
                .toList();
    }

    /**
     * Creates a list of CourtScheduleJudiciary objects containing listingProfileId and JudiciaryId mapping.
     * Processes schedules from the rota file, enriches them with judiciary information, and creates
     * CourtScheduleJudiciary objects for each valid schedule-judiciary mapping.
     *
     * @param records the parsed rota file records
     * @param requester the requester for making reference data queries
     * @param executionId the execution ID for logging purposes
     * @return a list of CourtScheduleJudiciary objects with listingProfileId and JudiciaryId mappings
     */
    public List<CourtScheduleJudiciary> createScheduleJudiciaryList(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                                     final Requester requester,
                                                                     final String executionId) {
        if (isEmptyRecords(records)) {
            logger.warn("No records provided to create schedule judiciary list");
            return Collections.emptyList();
        }
        
        final Collection<Map<String, String>> schedules = getRecordsByType(records, SCHEDULE).values();
        if (schedules.isEmpty()) {
            logger.debug("No schedules found in records");
            return Collections.emptyList();
        }
        
        final List<CourtScheduleJudiciary> scheduleJudiciaryList = new ArrayList<>();
        final Map<String, String> errors = new HashMap<>();
        final Map<String, Map<String, String>> judiciariesMap = getJudiciaryInfoMap(records);
        
        schedules.forEach(schedule -> {
            try {
                processSchedule(schedule, judiciariesMap, requester, executionId, scheduleJudiciaryList, errors);
            } catch (final Exception ex) {
                logger.error("Error processing schedule: {}", ex.getMessage(), ex);
            }
        });
        
        logger.debug("Created schedule judiciary list with {} entries from {} schedules", 
                scheduleJudiciaryList.size(), schedules.size());
        
        return scheduleJudiciaryList;
    }

    private void processSchedule(final Map<String, String> judiciarySchedule,
                                 final Map<String, Map<String, String>> judiciariesMap,
                                 final Requester requester,
                                 final String executionId,
                                 final List<CourtScheduleJudiciary> scheduleJudiciaryList,
                                 final Map<String, String> errors) {
        final String rotaJusticeId = judiciarySchedule.get(ROTA_JUDICIARY_ID);
        if (!isNotEmpty(rotaJusticeId)) {
            logger.debug("Skipping schedule - missing rota justice ID");
            return;
        }
        
        enrichScheduleWithJudiciaryInfo(judiciarySchedule, judiciariesMap, rotaJusticeId, requester, executionId, errors);
        
        final String courtListingProfileId = judiciarySchedule.get(COURT_LISTING_PROFILE_ID);
        final String judiciaryId = judiciarySchedule.get(JUDICIARY_ID);
        
        if (!isNotEmpty(courtListingProfileId) || !isNotEmpty(judiciaryId)) {
            logger.debug("Skipping schedule - missing court listing profile ID or judiciary ID");
            return;
        }
        
        buildAndAddScheduleJudiciary(judiciarySchedule, scheduleJudiciaryList, courtListingProfileId, judiciaryId);
    }

    private void enrichScheduleWithJudiciaryInfo(final Map<String, String> schedule,
                                                  final Map<String, Map<String, String>> judiciariesMap,
                                                  final String rotaJusticeId,
                                                  final Requester requester,
                                                  final String executionId,
                                                  final Map<String, String> errors) {
        schedule.putAll(getJudiciaryInfoFromRota(judiciariesMap, rotaJusticeId));
        enrichJudiciaryFromCppRefdata(schedule, errors, requester, executionId);
    }

    private void buildAndAddScheduleJudiciary(final Map<String, String> schedule,
                                              final List<CourtScheduleJudiciary> scheduleJudiciaryList,
                                              final String courtListingProfileId,
                                              final String judiciaryId) {
        final String courtScheduleId = randomUUID().toString();
        final CourtScheduleJudiciary courtScheduleJudiciary = judiciaryBuilder.build(schedule, courtScheduleId);
        
        if (isNotEmpty(courtScheduleJudiciary.getJudiciaryId())) {
            scheduleJudiciaryList.add(courtScheduleJudiciary);
            logger.debug("Created schedule judiciary mapping - listingProfileId: {}, judiciaryId: {}", 
                    courtListingProfileId, judiciaryId);
        }
    }

    private Map<String, Map<String, String>> getJudiciaryInfoMap(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> judiciaryInfoMap = new HashMap<>();
        judiciaryInfoMap.putAll(getRecordsByType(records, RotaPayload.DISTRICT_JUDGES));
        judiciaryInfoMap.putAll(getRecordsByType(records, RotaPayload.MAGISTRATES));
        return judiciaryInfoMap;
    }

    private Map<String, String> getJudiciaryInfoFromRota(final Map<String, Map<String, String>> judiciary, final String justiceId) {
        final Map<String, String> judiciaryDetails = new HashMap<>();
        final Map<String, String> judiciaryProps = judiciary.getOrDefault(justiceId, emptyMap());
        
        if (!judiciaryProps.isEmpty()) {
            judiciaryDetails.put(TITLE, getOrElse(judiciaryProps, MAGS_TITLE, JUDGE_TITLE));
            judiciaryDetails.put(FORENAMES, getOrElse(judiciaryProps, MAGISTRATE_FORENAMES, JUDGE_FORENAMES));
            judiciaryDetails.put(SURNAME, getOrElse(judiciaryProps, MAGISTRATE_SURNAME, JUDGE_SURNAME));
            judiciaryDetails.put(EMAIL_ADDRESS, getOrElse(judiciaryProps, MAGS_EMAIL, JUDGE_EMAIL));
        }
        
        return judiciaryDetails;
    }

    private String getOrElse(final Map<String, String> props, final String key, final String defaultKey) {
        final String value = props.get(key);
        if (!isBlank(value)) {
            return value;
        }
        return props.get(defaultKey);
    }

    private void enrichJudiciaryFromCppRefdata(final Map<String, String> schedule,
                                               final Map<String, String> errors,
                                               final Requester requester,
                                               final String executionId) {
        final String email = schedule.get(EMAIL_ADDRESS);
        if (!isNotEmpty(email)) {
            return;
        }
        
        referenceDataValidationService.validateAndFindJudiciaryByEmail(requester, email, executionId)
                .ifPresentOrElse(
                        judiciary -> populateScheduleWithJudiciaryData(schedule, judiciary),
                        () -> logJudiciaryNotFoundError(schedule, errors, email)
                );
    }

    private void populateScheduleWithJudiciaryData(final Map<String, String> schedule, final Judiciary judiciary) {
        schedule.put(JUDICIARY_ID, judiciary.getId());
        schedule.put(TITLE, judiciary.getTitlePrefix());
        schedule.put(FORENAMES, judiciary.getForenames());
        schedule.put(SURNAME, judiciary.getSurname());
        schedule.put(JUDICIARY_TYPE, judiciary.getJudiciaryType());
    }

    private void logJudiciaryNotFoundError(final Map<String, String> schedule,
                                           final Map<String, String> errors,
                                           final String email) {
        final String firstName = schedule.get(FORENAMES);
        final String lastName = schedule.get(SURNAME);
        if (isNotEmpty(firstName) && isNotEmpty(lastName)) {
            errors.put(email, format("Judiciary detail not found - Name %s %s, Email: %s", firstName, lastName, email));
        }
    }

    private boolean isEmptyRecords(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        return records == null || records.isEmpty();
    }

    private Map<String, Map<String, String>> getRecordsByType(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                               final RotaPayload payloadType) {
        return records.getOrDefault(payloadType, Collections.emptyMap());
    }

    public record ParseResult(Map<RotaPayload, Map<String, Map<String, String>>> records, String executionId) {
    }
}
