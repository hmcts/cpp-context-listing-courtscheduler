package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.DELIMITER;
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
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.justice.services.core.requester.Requester;
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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for all judiciary-related processing operations from rota file records.
 * Handles judiciary map creation, schedule enrichment, and judiciary court schedule mapping.
 */
@ApplicationScoped
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
                                                 final Requester requester,
                                                 final String executionId) {
        if (isEmptyRecords(records)) {
            logger.warn("No records provided to create judiciary map");
            return Collections.emptyMap();
        }

        final Map<String, UUID> judiciaryMap = new ConcurrentHashMap<>();
        final Set<String> missingJudiciaryEmails = ConcurrentHashMap.newKeySet();
        final Map<String, Map<String, String>> magistrates = getRecordsByType(records, MAGISTRATES);
        final Map<String, Map<String, String>> districtJudges = getRecordsByType(records, DISTRICT_JUDGES);

        processJudiciaries(magistrates, MAGS_EMAIL, requester, executionId, judiciaryMap, missingJudiciaryEmails, "magistrate");
        processJudiciaries(districtJudges, JUDGE_EMAIL, requester, executionId, judiciaryMap, missingJudiciaryEmails, "district judge");

        logMissingJudiciaries(missingJudiciaryEmails, executionId);

        logger.debug("Created judiciary map with {} entries ({} magistrates, {} district judges)",
                judiciaryMap.size(), magistrates.size(), districtJudges.size());

        return judiciaryMap;
    }

    // ============================================================================
    // JUDICIARY COURT SCHEDULE MAP CREATION
    // ============================================================================

    /**
     * Creates a map of judiciary IDs to lists of CourtSchedule UUIDs.
     *
     * @param records          the parsed rota file records
     * @param judiciaryMap     the map of judiciary IDs to Judiciary UUIDs
     * @param courtScheduleMap the map of court listing profile IDs to sets of CourtSchedule UUIDs
     * @param requester        the requester for making reference data queries
     * @param executionId      the execution ID for logging purposes
     * @return a map of judiciary IDs to lists of CourtSchedule UUIDs
     */
    public Map<String, List<UUID>> createJudiciaryCourtScheduleMap(
            final Map<RotaPayload, Map<String, Map<String, String>>> records,
            final Map<String, UUID> judiciaryMap,
            final Map<String, Set<UUID>> courtScheduleMap,
            final Requester requester,
            final String executionId) {
        final List<CourtScheduleJudiciary> scheduleJudiciaryList = createScheduleJudiciaryList(records, requester, executionId);

        if (scheduleJudiciaryList == null || scheduleJudiciaryList.isEmpty()) {
            logger.debug("No schedule judiciary list created to create judiciary court schedule map");
            return Collections.emptyMap();
        }

        if (courtScheduleMap == null || courtScheduleMap.isEmpty()) {
            logger.debug("No court schedule map provided to create judiciary court schedule map");
            return Collections.emptyMap();
        }

        final Map<String, List<UUID>> judiciaryCourtScheduleMap = new ConcurrentHashMap<>();

        scheduleJudiciaryList.forEach(schedule -> {
            try {
                final String judiciaryId = schedule.getJudiciaryId();
                final String courtListingProfileId = schedule.getCourtListingProfileId();

                if (!isNotEmpty(judiciaryId) || !isNotEmpty(courtListingProfileId)) {
                    logger.debug("Skipping schedule - missing judiciaryId or courtListingProfileId");
                    return;
                }

                if (!judiciaryMap.containsValue(UUID.fromString(judiciaryId))) {
                    logger.debug("Skipping schedule - judiciaryId {} not found in judiciaryMap", judiciaryId);
                    return;
                }

                final Set<UUID> scheduleIds = courtScheduleMap.get(courtListingProfileId);
                if (scheduleIds == null || scheduleIds.isEmpty()) {
                    logger.debug("Skipping schedule - no court schedule found for courtListingProfileId: {}",
                            courtListingProfileId);
                    return;
                }

                judiciaryCourtScheduleMap.computeIfAbsent(judiciaryId, k -> new ArrayList<>()).addAll(scheduleIds);

                logger.debug("Mapped judiciaryId {} to {} court schedule(s) with listingProfileId: {}",
                        judiciaryId, scheduleIds.size(), courtListingProfileId);
            } catch (final Exception ex) {
                logger.error("Error processing schedule for judiciary court schedule map: {}", ex.getMessage(), ex);
            }
        });

        final int totalSchedules = judiciaryCourtScheduleMap.values().stream()
                .mapToInt(List::size)
                .sum();
        logger.debug("Created judiciary court schedule map with {} entries and {} total court schedules from {} schedule judiciary entries",
                judiciaryCourtScheduleMap.size(), totalSchedules, scheduleJudiciaryList.size());

        return judiciaryCourtScheduleMap;
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
                getRecordsByType(records, DISTRICT_JUDGES));
        judiciaryInfoMap.putAll(getRecordsByType(records, MAGISTRATES));
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
                                                final Requester requester,
                                                final String executionId,
                                                final Map<String, String> errors) {
        schedule.putAll(getJudiciaryInfoFromRota(judiciariesMap, rotaJusticeId));
        enrichJudiciaryFromCppRefdata(schedule, errors, requester, executionId);
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    private void processJudiciaries(final Map<String, Map<String, String>> judiciaries,
                                    final String emailFieldName,
                                    final Requester requester,
                                    final String executionId,
                                    final Map<String, UUID> judiciaryMap,
                                    final Set<String> missingJudiciaryEmails,
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

            referenceDataValidationService.validateAndFindJudiciaryByEmail(requester, email, executionId)
                    .ifPresentOrElse(
                            judiciary -> {
                                judiciaryMap.put(justiceId, UUID.fromString(judiciary.getId()));
                                logger.debug("Mapped {} {} to judiciary with ID: {}", judiciaryType, justiceId, judiciary.getId());
                            },
                            () -> missingJudiciaryEmails.add(email)
                    );
        });
    }

    private void logMissingJudiciaries(final Set<String> missingJudiciaryEmails, final String executionId) {
        if (!missingJudiciaryEmails.isEmpty() && isNotEmpty(executionId)) {
            final String judiciaryMissingMessages = missingJudiciaryEmails.stream()
                    .filter(StringUtils::isNotEmpty)
                    .distinct()
                    .collect(joining(format(DELIMITER)));
            if (isNotEmpty(judiciaryMissingMessages)) {
                final String msg = JUDICIARY_NOT_FOUND.format(judiciaryMissingMessages);
                rotaProcessLogService.saveRotaProcessLog(
                        rotaProcessLog()
                                .withExecutionId(executionId)
                                .withErrorCode(JUDICIARY_NOT_FOUND.code())
                                .withErrorText(msg)
                                .build()
                );
            }
        }
    }

    private List<CourtScheduleJudiciary> createScheduleJudiciaryList(final Map<RotaPayload, Map<String, Map<String, String>>> records,
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
}

