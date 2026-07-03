package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.JUDICIARY_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.EMAIL_ADDRESS;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGISTRATE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGISTRATE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.DISTRICT_JUDGES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.SCHEDULE;

// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.JudiciaryBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for all judiciary-related processing operations from rota file records.
 * Handles judiciary map creation, schedule enrichment, and judiciary court schedule mapping.
 */
@Service
public class RotaJudiciaryHelper {

    private static final Logger logger = LoggerFactory.getLogger(RotaJudiciaryHelper.class);

    @Inject
    private RotaReferenceDataService referenceDataValidationService;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    @Inject
    private JudiciaryBuilder judiciaryBuilder;

    // ============================================================================
    // JUDICIARY MAP CREATION
    // ============================================================================

    /**
     * Creates a map of magistrate/district judge IDs to their validated Judiciary UUIDs.
     *
     * @param records     the parsed rota file records
     * @param requester   the requester for making reference data queries
     * @param executionId the execution ID for logging purposes
     * @return a map of magistrate/judge IDs to Judiciary UUIDs
     */
    public Map<String, UUID> createJudiciaryMap(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                 final String executionId) {
        if (RotaUtils.isEmptyRecords(records)) {
            logger.warn("No records provided to create judiciary map");
            return Collections.emptyMap();
        }

        final Map<String, UUID> judiciaryMap = new ConcurrentHashMap<>();
        final Map<String, Map<String, String>> magistrates = RotaUtils.getRecordsByType(records, MAGISTRATES);
        final Map<String, Map<String, String>> districtJudges = RotaUtils.getRecordsByType(records, DISTRICT_JUDGES);

        processJudiciaries(magistrates, MAGS_EMAIL, executionId, judiciaryMap, "magistrate");
        processJudiciaries(districtJudges, JUDGE_EMAIL, executionId, judiciaryMap, "district judge");

        logger.info("Created judiciary map with {} entries ({} magistrates, {} district judges)",
                judiciaryMap.size(), magistrates.size(), districtJudges.size());

        return judiciaryMap;
    }

    // ============================================================================
    // JUDICIARY COURT SCHEDULE MAP CREATION
    // ============================================================================

    /**
     * Creates a map of judiciary IDs to lists of JudiciaryCourtScheduleData.
     *
     * @param records          the parsed rota file records
     * @param judiciaryMap     the map of judiciary IDs to Judiciary UUIDs
     * @param courtScheduleMap the map of court listing profile IDs to sets of CourtSchedule UUIDs
     * @param requester        the requester for making reference data queries
     * @param executionId      the execution ID for logging purposes
     * @return a map of judiciary IDs to lists of JudiciaryCourtScheduleData
     */
    public Map<String, List<JudiciaryCourtScheduleData>> createJudiciaryCourtScheduleMap(
            final Map<RotaPayload, Map<String, Map<String, String>>> records,
            final Map<String, UUID> judiciaryMap,
            final Map<String, Set<UUID>> courtScheduleMap,
            final String executionId) {
        final List<CourtScheduleJudiciary> scheduleJudiciaryList = createScheduleJudiciaryList(records, executionId);

        if (scheduleJudiciaryList == null || scheduleJudiciaryList.isEmpty()) {
            logger.debug("No schedule judiciary list created to create judiciary court schedule map");
            return Collections.emptyMap();
        }

        if (courtScheduleMap == null || courtScheduleMap.isEmpty()) {
            logger.debug("No court schedule map provided to create judiciary court schedule map");
            return Collections.emptyMap();
        }

        final Map<String, List<String>> judiciaryCourtListingProfileMap = 
                buildJudiciaryCourtListingProfileMap(scheduleJudiciaryList, judiciaryMap, courtScheduleMap);

        // Create map with composite key (judiciaryId + courtListingProfileId) using judiciaryCourtListingProfileMap
        final Map<String, JudiciaryCourtScheduleData> judiciaryCourtListingProfileScheduleMap = 
                createJudiciaryCourtListingProfileScheduleMap(judiciaryCourtListingProfileMap, 
                        scheduleJudiciaryList, courtScheduleMap);
        logger.debug("Created judiciary court listing profile schedule map with {} composite key entries",
                judiciaryCourtListingProfileScheduleMap.size());

        // Create final map with judiciaryId as key and List of JudiciaryCourtScheduleData as value
        final Map<String, List<JudiciaryCourtScheduleData>> judiciaryCourtScheduleMap = 
                createJudiciaryIdToScheduleDataListMap(judiciaryCourtListingProfileScheduleMap);

        logJudiciaryCourtScheduleMapSummary(judiciaryCourtScheduleMap, scheduleJudiciaryList.size());

        return judiciaryCourtScheduleMap;
    }

    /**
     * Builds a map of judiciary IDs to lists of court listing profile IDs from schedule judiciary list.
     *
     * @param scheduleJudiciaryList the list of court schedule judiciary objects
     * @param judiciaryMap          the map of judiciary IDs to Judiciary UUIDs
     * @param courtScheduleMap      the map of court listing profile IDs to sets of CourtSchedule UUIDs
     * @return a map of judiciary IDs to lists of court listing profile IDs
     */
    private Map<String, List<String>> buildJudiciaryCourtListingProfileMap(
            final List<CourtScheduleJudiciary> scheduleJudiciaryList,
            final Map<String, UUID> judiciaryMap,
            final Map<String, Set<UUID>> courtScheduleMap) {
        final Map<String, List<String>> judiciaryCourtListingProfileMap = new ConcurrentHashMap<>();

        scheduleJudiciaryList.forEach(schedule -> {
            try {
                if (shouldProcessSchedule(schedule, judiciaryMap, courtScheduleMap)) {
                    addCourtListingProfileToMap(judiciaryCourtListingProfileMap, schedule);
                }
            } catch (final Exception ex) {
                logger.error("Error processing schedule for judiciary court schedule map: {}", ex.getMessage(), ex);
            }
        });

        return judiciaryCourtListingProfileMap;
    }

    /**
     * Checks if a schedule should be processed based on validation criteria.
     *
     * @param schedule         the court schedule judiciary to validate
     * @param judiciaryMap     the map of judiciary IDs to Judiciary UUIDs
     * @param courtScheduleMap the map of court listing profile IDs to sets of CourtSchedule UUIDs
     * @return true if the schedule should be processed, false otherwise
     */
    private boolean shouldProcessSchedule(
            final CourtScheduleJudiciary schedule,
            final Map<String, UUID> judiciaryMap,
            final Map<String, Set<UUID>> courtScheduleMap) {
        final String judiciaryId = schedule.getJudiciaryId();
        final String courtListingProfileId = schedule.getCourtListingProfileId();

        if (!isNotEmpty(judiciaryId) || !isNotEmpty(courtListingProfileId)) {
            logger.debug("Skipping schedule - missing judiciaryId or courtListingProfileId");
            return false;
        }

        try {
            final UUID judiciaryUuid = UUID.fromString(judiciaryId);
            if (!judiciaryMap.containsValue(judiciaryUuid)) {
                logger.debug("Skipping schedule - judiciaryId {} not found in judiciaryMap", judiciaryId);
                return false;
            }
        } catch (final IllegalArgumentException ex) {
            logger.debug("Skipping schedule - invalid judiciaryId format: {}", judiciaryId, ex);
            return false;
        }

        final Set<UUID> scheduleIds = courtScheduleMap.get(courtListingProfileId);
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            logger.debug("Skipping schedule - no court schedule found for courtListingProfileId: {}", courtListingProfileId);
            return false;
        }

        return true;
    }

    /**
     * Adds a court listing profile ID to the judiciary court listing profile map.
     *
     * @param judiciaryCourtListingProfileMap the map to update
     * @param schedule                       the court schedule judiciary containing the data
     */
    private void addCourtListingProfileToMap(
            final Map<String, List<String>> judiciaryCourtListingProfileMap,
            final CourtScheduleJudiciary schedule) {
        final String judiciaryId = schedule.getJudiciaryId();
        final String courtListingProfileId = schedule.getCourtListingProfileId();

        judiciaryCourtListingProfileMap.compute(judiciaryId, (key, existingList) -> 
                RotaUtils.addToListIfNotPresent(existingList, courtListingProfileId));

        logger.debug("Mapped judiciaryId {} to court schedule(s) with listingProfileId: {}, position: {}, isBenchChairman: {}, isDeputy: {}",
                judiciaryId, courtListingProfileId,
                schedule.getPosition(), schedule.getBenchChairman(), schedule.getDeputy());
    }


    /**
     * Logs a summary of the created judiciary court schedule map.
     *
     * @param judiciaryCourtScheduleMap the created map
     * @param scheduleJudiciaryCount     the count of schedule judiciary entries processed
     */
    private void logJudiciaryCourtScheduleMapSummary(
            final Map<String, List<JudiciaryCourtScheduleData>> judiciaryCourtScheduleMap,
            final int scheduleJudiciaryCount) {
        final int totalSchedules = judiciaryCourtScheduleMap.values().stream()
                .flatMap(List::stream)
                .mapToInt(data -> data.courtScheduleIds().size())
                .sum();
        logger.info("Created judiciary court schedule map with {} entries and {} total court schedules from {} schedule judiciary entries",
                judiciaryCourtScheduleMap.size(), totalSchedules, scheduleJudiciaryCount);
    }

    /**
     * Creates a JudiciaryCourtScheduleData from a schedule and schedule IDs.
     *
     * @param schedule the court schedule judiciary
     * @param scheduleIds the set of schedule UUIDs
     * @return JudiciaryCourtScheduleData object
     */
    private JudiciaryCourtScheduleData createScheduleData(final CourtScheduleJudiciary schedule, final Set<UUID> scheduleIds) {
        return new JudiciaryCourtScheduleData(
                new ArrayList<>(scheduleIds),
                schedule.getRotaJudiciaryId(),
                schedule.getPosition(),
                schedule.getBenchChairman(),
                schedule.getDeputy()
        );
    }


    /**
     * Creates a map with judiciaryId as key and List of JudiciaryCourtScheduleData as value from the composite key map.
     * Optimized using groupingBy collector for better performance.
     *
     * @param judiciaryCourtListingProfileScheduleMap the map with composite key (judiciaryId|courtListingProfileId) and JudiciaryCourtScheduleData as value
     * @return a map with judiciaryId as key and List of JudiciaryCourtScheduleData as value
     */
    private Map<String, List<JudiciaryCourtScheduleData>> createJudiciaryIdToScheduleDataListMap(
            final Map<String, JudiciaryCourtScheduleData> judiciaryCourtListingProfileScheduleMap) {
        if (judiciaryCourtListingProfileScheduleMap == null || judiciaryCourtListingProfileScheduleMap.isEmpty()) {
            logger.debug("No judiciary court listing profile schedule map provided to create judiciary ID to schedule data list map");
            return new ConcurrentHashMap<>();
        }

        final Map<String, List<JudiciaryCourtScheduleData>> resultMap = judiciaryCourtListingProfileScheduleMap.entrySet().stream()
                .filter(entry -> {
                    final String compositeKey = entry.getKey();
                    if (RotaUtils.parseCompositeKey(compositeKey) == null) {
                        logger.debug("Invalid or empty composite key format: {}", compositeKey);
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.groupingBy(
                        entry -> RotaUtils.extractFirstPart(entry.getKey()),
                        ConcurrentHashMap::new,
                        Collectors.mapping(
                                Map.Entry::getValue,
                                Collectors.toList()
                        )
                ));

        logger.info("Created judiciary ID to schedule data list map with {} entries", resultMap.size());
        return resultMap;
    }

    /**
     * Creates a map with composite key (judiciaryId + courtListingProfileId) and JudiciaryCourtScheduleData as value.
     * Optimized by pre-building a lookup map for schedule judiciary objects.
     *
     * @param judiciaryCourtListingProfileMap the map of judiciary IDs to lists of court listing profile IDs
     * @param scheduleJudiciaryList the list of court schedule judiciary objects
     * @param courtScheduleMap the map of court listing profile IDs to sets of CourtSchedule UUIDs
     * @return a map with composite key (judiciaryId|courtListingProfileId) and JudiciaryCourtScheduleData as value
     */
    private Map<String, JudiciaryCourtScheduleData> createJudiciaryCourtListingProfileScheduleMap(
            final Map<String, List<String>> judiciaryCourtListingProfileMap,
            final List<CourtScheduleJudiciary> scheduleJudiciaryList,
            final Map<String, Set<UUID>> courtScheduleMap) {
        final Map<String, JudiciaryCourtScheduleData> resultMap = new ConcurrentHashMap<>();

        if (judiciaryCourtListingProfileMap == null || judiciaryCourtListingProfileMap.isEmpty()) {
            logger.debug("No judiciary court listing profile map provided to create composite key map");
            return resultMap;
        }

        // Pre-build lookup map for O(1) access instead of O(n) stream search
        final Map<String, CourtScheduleJudiciary> scheduleLookupMap = buildScheduleLookupMap(scheduleJudiciaryList);

        judiciaryCourtListingProfileMap.forEach((judiciaryId, courtListingProfileIds) -> {
            if (shouldProcessJudiciaryEntry(judiciaryId, courtListingProfileIds)) {
                processCourtListingProfileIds(judiciaryId, courtListingProfileIds, scheduleLookupMap, 
                        courtScheduleMap, resultMap);
            }
        });

        logger.info("Created judiciary court listing profile schedule map with {} entries", resultMap.size());
        return resultMap;
    }

    /**
     * Checks if a judiciary entry should be processed.
     *
     * @param judiciaryId            the judiciary ID to validate
     * @param courtListingProfileIds the list of court listing profile IDs
     * @return true if the entry should be processed, false otherwise
     */
    private boolean shouldProcessJudiciaryEntry(final String judiciaryId, final List<String> courtListingProfileIds) {
        return courtListingProfileIds != null 
                && !courtListingProfileIds.isEmpty() 
                && isNotEmpty(judiciaryId);
    }

    /**
     * Processes court listing profile IDs for a judiciary and adds entries to the result map.
     *
     * @param judiciaryId            the judiciary ID
     * @param courtListingProfileIds the list of court listing profile IDs to process
     * @param scheduleLookupMap      the lookup map for schedule judiciary objects
     * @param courtScheduleMap       the map of court listing profile IDs to sets of CourtSchedule UUIDs
     * @param resultMap              the result map to populate
     */
    private void processCourtListingProfileIds(
            final String judiciaryId,
            final List<String> courtListingProfileIds,
            final Map<String, CourtScheduleJudiciary> scheduleLookupMap,
            final Map<String, Set<UUID>> courtScheduleMap,
            final Map<String, JudiciaryCourtScheduleData> resultMap) {
        courtListingProfileIds.forEach(courtListingProfileId -> {
            try {
                processCourtListingProfileEntry(judiciaryId, courtListingProfileId, scheduleLookupMap, 
                        courtScheduleMap, resultMap);
            } catch (final Exception ex) {
                logger.error("Error creating composite key map entry for judiciaryId: {} and courtListingProfileId: {}: {}",
                        judiciaryId, courtListingProfileId, ex.getMessage(), ex);
            }
        });
    }

    /**
     * Processes a single court listing profile entry and adds it to the result map if valid.
     *
     * @param judiciaryId       the judiciary ID
     * @param courtListingProfileId the court listing profile ID
     * @param scheduleLookupMap  the lookup map for schedule judiciary objects
     * @param courtScheduleMap  the map of court listing profile IDs to sets of CourtSchedule UUIDs
     * @param resultMap         the result map to populate
     */
    private void processCourtListingProfileEntry(
            final String judiciaryId,
            final String courtListingProfileId,
            final Map<String, CourtScheduleJudiciary> scheduleLookupMap,
            final Map<String, Set<UUID>> courtScheduleMap,
            final Map<String, JudiciaryCourtScheduleData> resultMap) {
        if (!isNotEmpty(courtListingProfileId)) {
            logger.debug("Skipping - missing courtListingProfileId for judiciaryId: {}", judiciaryId);
            return;
        }

        final String lookupKey = RotaUtils.buildCompositeKey(judiciaryId, courtListingProfileId);
        final CourtScheduleJudiciary schedule = scheduleLookupMap.get(lookupKey);

        if (schedule == null) {
            logger.debug("No schedule found for judiciaryId: {} and courtListingProfileId: {}",
                    judiciaryId, courtListingProfileId);
            return;
        }

        final Set<UUID> scheduleIds = courtScheduleMap.get(courtListingProfileId);
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            logger.debug("No court schedule found for courtListingProfileId: {}", courtListingProfileId);
            return;
        }

        final JudiciaryCourtScheduleData scheduleData = createScheduleData(schedule, scheduleIds);
        resultMap.put(lookupKey, scheduleData);

        logger.debug("Created map entry with composite key {} for judiciaryId: {} and courtListingProfileId: {}",
                lookupKey, judiciaryId, courtListingProfileId);
    }

    /**
     * Builds a lookup map for schedule judiciary objects using composite key (judiciaryId|courtListingProfileId).
     *
     * @param scheduleJudiciaryList the list of court schedule judiciary objects
     * @return a map with composite key and CourtScheduleJudiciary as value
     */
    private Map<String, CourtScheduleJudiciary> buildScheduleLookupMap(
            final List<CourtScheduleJudiciary> scheduleJudiciaryList) {
        final Map<String, CourtScheduleJudiciary> lookupMap = new ConcurrentHashMap<>();
        
        if (scheduleJudiciaryList == null || scheduleJudiciaryList.isEmpty()) {
            return lookupMap;
        }

        scheduleJudiciaryList.forEach(schedule -> {
            final String judiciaryId = schedule.getJudiciaryId();
            final String courtListingProfileId = schedule.getCourtListingProfileId();
            
            if (isNotEmpty(judiciaryId) && isNotEmpty(courtListingProfileId)) {
                final String key = RotaUtils.buildCompositeKey(judiciaryId, courtListingProfileId);
                if (key != null) {
                    lookupMap.put(key, schedule);
                }
            }
        });

        return lookupMap;
    }

    // ============================================================================
    // JUDICIARY ENRICHMENT
    // ============================================================================

    /**
     * Gets a combined map of magistrates and district judges from records.
     *
     * @param records the parsed rota file records
     * @return a map of justice IDs to their data
     */
    public Map<String, Map<String, String>> getJudiciaryInfoMap(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> judiciaryInfoMap = new HashMap<>(
                RotaUtils.getRecordsByType(records, DISTRICT_JUDGES));
        judiciaryInfoMap.putAll(RotaUtils.getRecordsByType(records, MAGISTRATES));
        return judiciaryInfoMap;
    }

    /**
     * Enriches a schedule with judiciary information from both rota file and reference data.
     *
     * @param schedule       the schedule map to enrich
     * @param judiciariesMap the map of judiciary information from rota file
     * @param rotaJusticeId  the rota justice ID to look up
     * @param requester      the requester for making reference data queries
     * @param executionId    the execution ID for logging purposes
     * @param errors         map to store errors encountered during enrichment
     */
    public void enrichScheduleWithJudiciaryInfo(final Map<String, String> schedule,
                                                final Map<String, Map<String, String>> judiciariesMap,
                                                final String rotaJusticeId,
                                                final String executionId,
                                                final Map<String, String> errors) {
        schedule.putAll(getJudiciaryInfoFromRota(judiciariesMap, rotaJusticeId));
        enrichJudiciaryFromCppRefdata(schedule, errors, executionId);
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    private void processJudiciaries(final Map<String, Map<String, String>> judiciaries,
                                    final String emailFieldName,
                                    final String executionId,
                                    final Map<String, UUID> judiciaryMap,
                                    final String judiciaryType) {
        judiciaries.forEach((justiceId, judiciaryData) -> {
            if (judiciaryData == null || judiciaryData.isEmpty()) {
                logger.debug("Skipping {} {} - no data available", judiciaryType, justiceId);
                return;
            }

            final String email = judiciaryData.get(emailFieldName);
            if (!isNotEmpty(email)) {
                logger.debug("Skipping {} {} - no email address found", judiciaryType, justiceId);
                return;
            }

            referenceDataValidationService.validateAndFindJudiciaryByEmail(email, executionId)
                    .ifPresent(judiciary -> {
                        judiciaryMap.put(justiceId, UUID.fromString(judiciary.getId()));
                        logger.debug("Mapped {} {} to judiciary with ID: {}", judiciaryType, justiceId, judiciary.getId());
                    });
        });
    }

    private List<CourtScheduleJudiciary> createScheduleJudiciaryList(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                                     final String executionId) {
        if (RotaUtils.isEmptyRecords(records)) {
            logger.warn("No records provided to create schedule judiciary list");
            return Collections.emptyList();
        }

        final Collection<Map<String, String>> schedules = RotaUtils.getRecordsByType(records, SCHEDULE).values();
        if (schedules.isEmpty()) {
            logger.debug("No schedules found in records");
            return Collections.emptyList();
        }

        final List<CourtScheduleJudiciary> scheduleJudiciaryList = new ArrayList<>();
        final Map<String, String> errors = new HashMap<>();
        final Map<String, Map<String, String>> judiciariesMap = getJudiciaryInfoMap(records);

        schedules.forEach(schedule -> {
            try {
                processSchedule(schedule, judiciariesMap, executionId, scheduleJudiciaryList, errors);
            } catch (final Exception ex) {
                logger.error("Error processing schedule: {}", ex.getMessage(), ex);
            }
        });

        RotaUtils.logMissingDataError(rotaProcessLogService, errors.values(), executionId, JUDICIARY_NOT_FOUND);

        logger.info("Created schedule judiciary list with {} entries from {} schedules",
                scheduleJudiciaryList.size(), schedules.size());

        return scheduleJudiciaryList;
    }

    private void processSchedule(final Map<String, String> judiciarySchedule,
                                 final Map<String, Map<String, String>> judiciariesMap,
                                 final String executionId,
                                 final List<CourtScheduleJudiciary> scheduleJudiciaryList,
                                 final Map<String, String> errors) {
        final String rotaJusticeId = judiciarySchedule.get(ROTA_JUDICIARY_ID);
        if (!isNotEmpty(rotaJusticeId)) {
            logger.debug("Skipping schedule - missing rota justice ID");
            return;
        }

        enrichScheduleWithJudiciaryInfo(judiciarySchedule, judiciariesMap, rotaJusticeId, executionId, errors);

        final String courtListingProfileId = judiciarySchedule.get(COURT_LISTING_PROFILE_ID);
        final String judiciaryId = judiciarySchedule.get(JUDICIARY_ID);

        if (!isNotEmpty(courtListingProfileId) || !isNotEmpty(judiciaryId)) {
            logger.debug("Skipping schedule - missing court listing profile ID or judiciary ID");
            return;
        }

        buildAndAddScheduleJudiciary(judiciarySchedule, scheduleJudiciaryList, courtListingProfileId, judiciaryId);
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
                                               final String executionId) {
        final String email = schedule.get(EMAIL_ADDRESS);
        if (!isNotEmpty(email)) {
            return;
        }

        referenceDataValidationService.validateAndFindJudiciaryByEmail(email, executionId)
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

}

