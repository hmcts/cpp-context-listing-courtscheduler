package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import org.springframework.stereotype.Service;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.DELIMITER;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Utility class for common rota processing operations.
 * Provides helper methods for records, collections, keys, validation, and error logging.
 */
public final class RotaUtils {

    private static final String KEY_SEPARATOR = "|";
    private static final String UNKNOWN_LOCATION = "UNKNOWN_LOCATION";
    private static final String UNKNOWN_VENUE = "UNKNOWN_VENUE";
    private static final String UNKNOWN_VENUE_ID = "UNKNOWN_VENUE_ID";

    private RotaUtils() {
        // Utility class - prevent instantiation
    }

    // ============================================================================
    // Record Operations
    // ============================================================================

    /**
     * Checks if the records map is null or empty.
     *
     * @param records the records map to check
     * @return true if records is null or empty, false otherwise
     */
    public static boolean isEmptyRecords(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        return records == null || records.isEmpty();
    }

    /**
     * Gets records of a specific type from the records map.
     *
     * @param records     the records map
     * @param payloadType the type of records to retrieve
     * @return a map of records of the specified type, or empty map if not found
     */
    public static Map<String, Map<String, String>> getRecordsByType(
            final Map<RotaPayload, Map<String, Map<String, String>>> records,
            final RotaPayload payloadType) {
        return records.getOrDefault(payloadType, Collections.emptyMap());
    }

    // ============================================================================
    // Collection Operations
    // ============================================================================

    /**
     * Adds an item to a list if it's not already present, or creates a new list if the existing one is null.
     *
     * @param existingList the existing list, or null
     * @param item         the item to add
     * @return the list with the item added
     */
    public static <T> List<T> addToListIfNotPresent(final List<T> existingList, final T item) {
        if (existingList == null) {
            final List<T> newList = new ArrayList<>();
            newList.add(item);
            return newList;
        }
        if (!existingList.contains(item)) {
            existingList.add(item);
        }
        return existingList;
    }

    // ============================================================================
    // Key Operations
    // ============================================================================

    /**
     * Builds a composite key from two parts using the separator.
     *
     * @param part1 the first part of the key
     * @param part2 the second part of the key
     * @return the composite key in format "part1|part2"
     */
    public static String buildCompositeKey(final String part1, final String part2) {
        if (!isNotEmpty(part1) || !isNotEmpty(part2)) {
            return null;
        }
        return part1 + KEY_SEPARATOR + part2;
    }

    /**
     * Parses a composite key into its parts.
     *
     * @param compositeKey the composite key to parse
     * @return an array with two elements [part1, part2], or null if invalid
     */
    public static String[] parseCompositeKey(final String compositeKey) {
        if (compositeKey == null || compositeKey.isEmpty()) {
            return null;
        }
        final String[] parts = compositeKey.split("\\" + KEY_SEPARATOR, 2);
        if (parts.length != 2 || !isNotEmpty(parts[0])) {
            return null;
        }
        return parts;
    }

    /**
     * Extracts the first part (judiciaryId) from a composite key.
     *
     * @param compositeKey the composite key
     * @return the first part, or null if invalid
     */
    public static String extractFirstPart(final String compositeKey) {
        final String[] parts = parseCompositeKey(compositeKey);
        return parts != null ? parts[0] : null;
    }

    // ============================================================================
    // Validation & Formatting
    // ============================================================================

    /**
     * Builds a formatted string representation of venue details.
     * Format: "locationId - venueName - venueId"
     *
     * @param venue the venue to format, can be null
     * @return a formatted string with venue details, or default values if venue is null
     */
    public static String buildVenueDetails(final Venue venue) {
        if (venue == null) {
            return format("%s - %s - %s", UNKNOWN_LOCATION, UNKNOWN_VENUE, UNKNOWN_VENUE_ID);
        }

        final String locationId = venue.getLocationId() != null 
                ? venue.getLocationId().toString() 
                : UNKNOWN_LOCATION;
        final String venueName = defaultIfBlank(venue.getVenueName(), UNKNOWN_VENUE);
        final String venueId = venue.getVenueId() != null 
                ? venue.getVenueId().toString() 
                : UNKNOWN_VENUE_ID;

        return format("%s - %s - %s", locationId, venueName, venueId);
    }

    /**
     * Checks if the session values match, considering that 'AD' (All Day) can match both 'AM' and 'PM' sessions.
     *
     * @param requestedSession      the session value from the rota file (AM, PM, or AD)
     * @param courtScheduleSession  the session value from the court schedule (AM, PM, or AD)
     * @return true if sessions match according to the matching rules, false otherwise
     */
    public static boolean matchesSession(final String requestedSession, final String courtScheduleSession) {
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
        return PM_SESSION.equals(requestedSession) && ALL_DAY.equals(courtScheduleSession);
    }

    // ============================================================================
    // Error Logging
    // ============================================================================

    /**
     * Logs a processing error to RotaProcessLogService.
     *
     * @param rotaProcessLogService the service to use for logging
     * @param executionId           the execution ID for logging purposes
     * @param errorCode             the error code
     * @param errorText             the error text
     */
    public static void logProcessingError(final RotaProcessLogService rotaProcessLogService,
                                         final String executionId,
                                         final String errorCode,
                                         final String errorText) {
        if (isNotEmpty(executionId) && isNotEmpty(errorText)) {
            rotaProcessLogService.saveRotaProcessLog(
                    rotaProcessLog()
                            .withExecutionId(executionId)
                            .withErrorCode(errorCode)
                            .withErrorText(errorText)
                            .build()
            );
        }
    }

    /**
     * Logs a collection of error messages using a MissingDataError template.
     *
     * @param rotaProcessLogService the service to use for logging
     * @param messages              the collection of error messages
     * @param executionId           the execution ID for logging purposes
     * @param missingDataError      the MissingDataError enum to use for formatting
     */
    public static void logMissingDataError(final RotaProcessLogService rotaProcessLogService,
                                          final Collection<String> messages,
                                          final String executionId,
                                          final MissingDataError missingDataError) {
        if (!isNotEmpty(executionId) || messages == null || messages.isEmpty()) {
            return;
        }

        final String formattedMessages = messages.stream()
                .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                .distinct()
                .collect(joining(format(DELIMITER)));

        if (isNotEmpty(formattedMessages)) {
            final String errorText = missingDataError.format(formattedMessages);
            logProcessingError(rotaProcessLogService, executionId, missingDataError.code(), errorText);
        }
    }

    /**
     * Logs missing reference data from a map of venue details to error codes.
     *
     * @param rotaProcessLogService        the service to use for logging
     * @param missingReferenceDataMap      the map of venue details to error codes
     * @param executionId                 the execution ID for logging purposes
     * @param missingDataError            the MissingDataError enum to use for formatting
     */
    public static void logMissingReferenceData(final RotaProcessLogService rotaProcessLogService,
                                               final Map<String, String> missingReferenceDataMap,
                                               final String executionId,
                                               final MissingDataError missingDataError) {
        if (missingReferenceDataMap == null || missingReferenceDataMap.isEmpty() || !isNotEmpty(executionId)) {
            return;
        }

        final String venueDetails = missingReferenceDataMap.entrySet()
                .stream()
                .filter(e -> missingDataError.code().equals(e.getValue()))
                .map(Map.Entry::getKey)
                .filter(org.apache.commons.lang3.StringUtils::isNotBlank)
                .distinct()
                .collect(joining(format(DELIMITER)));

        if (isNotEmpty(venueDetails)) {
            final String errorText = missingDataError.format(venueDetails);
            logProcessingError(rotaProcessLogService, executionId, missingDataError.code(), errorText);
        }
    }
}

