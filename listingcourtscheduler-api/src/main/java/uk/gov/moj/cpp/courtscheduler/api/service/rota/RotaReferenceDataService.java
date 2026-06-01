package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.ROTA_PROCESSING_ERROR;

// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.RotaUtils;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized service for all reference data validation checks in the rota file processor.
 * This service groups together all reference data checks including:
 * - Judiciary identification and validation (by email)
 * - Venue identification and validation (by location ID, venue ID, and venue name)
 */
@Service
@org.springframework.transaction.annotation.Transactional
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
    public Optional<Judiciary> validateAndFindJudiciaryByEmail(final String email, final String executionId) {
        if (!isNotBlank(email)) {
            logger.debug("Judiciary email is empty or blank, returning empty Optional");
            return empty();
        }

        try {
            final Optional<Judiciary> judiciaryOptional = referenceDataMapperService.findByEmail(email);

            if (judiciaryOptional.isPresent()) {
                logger.info("Judiciary validation successful for email: {} - found judiciary ID: {}", 
                        email, judiciaryOptional.get().getId());
                return judiciaryOptional;
            } else {
                logger.warn("Judiciary not found for email: {}", email);
                return empty();
            }
        } catch (final Exception ex) {
            // Error occurred during validation - log error
            logger.error("Error occurred while validating judiciary for email: {}", email, ex);
            if (isNotBlank(executionId)) {
                final String errorMessage = format("Error validating judiciary for email %s: %s", email, ex.getMessage());
                RotaUtils.logProcessingError(
                        rotaProcessLogService,
                        executionId,
                        ROTA_PROCESSING_ERROR.code(),
                        ROTA_PROCESSING_ERROR.template().formatted(errorMessage)
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
                                                    final String executionId) {
        if (venue == null) {
            logger.warn("Venue is null, cannot validate venue");
            if (exceptionMessages != null) {
                final String venueDetails = RotaUtils.buildVenueDetails(null);
                exceptionMessages.putIfAbsent(venueDetails, REF_DATA_VENUE_NOT_FOUND.code());
            } else if (isNotEmpty(executionId)) {
                final String errorMessage = REF_DATA_VENUE_NOT_FOUND.format(RotaUtils.buildVenueDetails(null));
                RotaUtils.logProcessingError(
                        rotaProcessLogService,
                        executionId,
                        REF_DATA_VENUE_NOT_FOUND.code(),
                        errorMessage
                );
            }
            return empty();
        }

        logger.debug("Validating venue - locationId: {}, venueId: {}, venueName: {}",
                venue.getLocationId(), venue.getVenueId(), venue.getVenueName());

        try {
            final Map<String, String> safeExceptionMessages = exceptionMessages != null ? exceptionMessages : new java.util.HashMap<>();
            final Optional<CourtRoom> courtRoomOptional = referenceDataMapperService.findByVenue(venue, safeExceptionMessages);

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
                    final String venueDetails = RotaUtils.buildVenueDetails(venue);
                    exceptionMessages.putIfAbsent(venueDetails, REF_DATA_VENUE_NOT_FOUND.code());
                } else if (isNotEmpty(executionId)) {
                    final String errorMessage = REF_DATA_VENUE_NOT_FOUND.format(RotaUtils.buildVenueDetails(venue));
                    RotaUtils.logProcessingError(
                            rotaProcessLogService,
                            executionId,
                            REF_DATA_VENUE_NOT_FOUND.code(),
                            errorMessage
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
                RotaUtils.logProcessingError(
                        rotaProcessLogService,
                        executionId,
                        ROTA_PROCESSING_ERROR.code(),
                        ROTA_PROCESSING_ERROR.template().formatted(errorMessage)
                );
            }
            return empty();
        }
    }
}

