package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.ROTA_PROCESSING_ERROR;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized service for all reference data validation checks in the rota file processor.
 * This service groups together all reference data checks including:
 * - Judiciary identification and validation (by email)
 * - Venue identification and validation (by location ID, venue ID, and venue name)
 */
@ApplicationScoped
public class RotaReferenceDataService {

    private static final Logger logger = LoggerFactory.getLogger(RotaReferenceDataService.class);

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    /**
     * Validates and retrieves judiciary information by email address.
     * Used to identify and validate judiciaries from the rota file.
     * If the judiciary is not found or an error occurs, it will be logged using RotaProcessLogService.
     *
     * @param requester   the requester for making reference data queries
     * @param email       the email address of the judiciary
     * @param executionId the execution ID for logging purposes (can be null)
     * @return Optional containing the Judiciary if found, empty otherwise
     */
    public Optional<Judiciary> validateAndFindJudiciaryByEmail(final Requester requester, final String email, final String executionId) {
        if (!isNotEmpty(email)) {
            logger.debug("Judiciary email is empty, returning empty Optional");
            return empty();
        }

        try {
            final Optional<Judiciary> judiciaryOptional = referenceDataMapperService.findByEmail(requester, email);

            if (judiciaryOptional.isPresent()) {
                logger.info("Judiciary validation successful for email: {} - found judiciary ID: {}", 
                        email, judiciaryOptional.get().getId());
                return judiciaryOptional;
            } else {
                // Judiciary not found - don't log here, let caller aggregate
                logger.warn("Judiciary not found for email: {}", email);
                return empty();
            }
        } catch (final Exception ex) {
            // Error occurred during validation - log error
            logger.error("Error occurred while validating judiciary for email: {}", email, ex);
            if (isNotEmpty(executionId)) {
                final String errorMessage = format("Error validating judiciary for email %s: %s", email, ex.getMessage());
                rotaProcessLogService.saveRotaProcessLog(
                        rotaProcessLog()
                                .withExecutionId(executionId)
                                .withErrorCode(ROTA_PROCESSING_ERROR.code())
                                .withErrorText(ROTA_PROCESSING_ERROR.template().formatted(errorMessage))
                                .build()
                );
            }
            return empty();
        }
    }

    /**
     * Validates and retrieves court room information by venue details.
     * Identifies venues using location ID, venue ID, and venue name.
     * If the venue is not found or an error occurs, it will be logged using RotaProcessLogService.
     *
     * @param venue             the venue containing locationId, venueId, and venueName
     * @param exceptionMessages map to store exception messages for missing venue mappings
     * @param requester         the requester for making reference data queries
     * @param executionId       the execution ID for logging purposes (can be null)
     * @return Optional containing the CourtRoom if found, empty otherwise
     */
    public Optional<CourtRoom> validateAndFindVenue(final Venue venue,
                                                    final Map<String, String> exceptionMessages,
                                                    final Requester requester,
                                                    final String executionId) {
        if (venue == null) {
            logger.warn("Venue is null, cannot validate venue");
            if (exceptionMessages != null) {
                final String venueDetails = buildVenueDetails(null);
                exceptionMessages.putIfAbsent(venueDetails, REF_DATA_VENUE_NOT_FOUND.code());
            } else if (isNotEmpty(executionId)) {
                final String errorMessage = REF_DATA_VENUE_NOT_FOUND.format(buildVenueDetails(null));
                rotaProcessLogService.saveRotaProcessLog(
                        rotaProcessLog()
                                .withExecutionId(executionId)
                                .withErrorCode(REF_DATA_VENUE_NOT_FOUND.code())
                                .withErrorText(errorMessage)
                                .build()
                );
            }
            return empty();
        }

        logger.debug("Validating venue - locationId: {}, venueId: {}, venueName: {}",
                venue.getLocationId(), venue.getVenueId(), venue.getVenueName());

        try {
            final Map<String, String> safeExceptionMessages = exceptionMessages != null ? exceptionMessages : new java.util.HashMap<>();
            final Optional<CourtRoom> courtRoomOptional = referenceDataMapperService.findByVenue(venue, safeExceptionMessages, requester);

            if (courtRoomOptional.isPresent()) {
                logger.info("Venue validated successfully - locationId: {}, venueId: {}, venueName: {} - found court room: {}", 
                        venue.getLocationId(), venue.getVenueId(), venue.getVenueName(), 
                        courtRoomOptional.get().getCourtroomId());
                return courtRoomOptional;
            } else {
                // Venue not found - populate map if provided, otherwise log directly
                logger.warn("Venue validation failed - locationId: {}, venueId: {}, venueName: {}",
                        venue.getLocationId(), venue.getVenueId(), venue.getVenueName());
                if (exceptionMessages != null) {
                    final String venueDetails = buildVenueDetails(venue);
                    exceptionMessages.putIfAbsent(venueDetails, REF_DATA_VENUE_NOT_FOUND.code());
                } else if (isNotEmpty(executionId)) {
                    final String errorMessage = REF_DATA_VENUE_NOT_FOUND.format(buildVenueDetails(venue));
                    rotaProcessLogService.saveRotaProcessLog(
                            rotaProcessLog()
                                    .withExecutionId(executionId)
                                    .withErrorCode(REF_DATA_VENUE_NOT_FOUND.code())
                                    .withErrorText(errorMessage)
                                    .build()
                    );
                }
                return empty();
            }
        } catch (final Exception ex) {
            // Error occurred during validation - log error
            logger.error("Error occurred while validating venue - locationId: {}, venueId: {}, venueName: {}",
                    venue.getLocationId(), venue.getVenueId(), venue.getVenueName(), ex);
            if (isNotEmpty(executionId)) {
                final String errorMessage = format("Error validating venue - locationId: %d, venueId: %d, venueName: %s - %s",
                        venue.getLocationId(), venue.getVenueId(), venue.getVenueName(), ex.getMessage());
                rotaProcessLogService.saveRotaProcessLog(
                        rotaProcessLog()
                                .withExecutionId(executionId)
                                .withErrorCode(ROTA_PROCESSING_ERROR.code())
                                .withErrorText(ROTA_PROCESSING_ERROR.template().formatted(errorMessage))
                                .build()
                );
            }
            return empty();
        }
    }

    private String buildVenueDetails(final Venue venue) {
        if (venue == null) {
            return "UNKNOWN_LOCATION - UNKNOWN_VENUE - UNKNOWN_VENUE_ID";
        }
        final String locationId = venue.getLocationId() != null ? venue.getLocationId().toString() : "UNKNOWN_LOCATION";
        final String venueName = defaultIfBlank(venue.getVenueName(), "UNKNOWN_VENUE");
        final String venueId = venue.getVenueId() != null ? venue.getVenueId().toString() : "UNKNOWN_VENUE_ID";
        return format("%s - %s - %s", locationId, venueName, venueId);
    }
}

