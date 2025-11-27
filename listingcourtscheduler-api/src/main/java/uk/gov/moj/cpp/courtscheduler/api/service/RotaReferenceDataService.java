package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.JUDICIARY_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.ROTA_PROCESSING_ERROR;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized service for all reference data validation checks in the rota file processor.
 * This service groups together all reference data checks including:
 * - Judiciary identification and validation (by email)
 * - Venue identification and validation (by location ID, venue ID, and venue name)
 * - Court room mappings (location ID to OU code resolution)
 * - Business type mappings (business type validation)
 * - Session allocation validation (OU code, room ID, session, and business type)
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
     * @param requester the requester for making reference data queries
     * @param email the email address of the judiciary
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
                logger.debug("Judiciary validation for email {} - found: {}", email, true);
                return judiciaryOptional;
            } else {
                // Judiciary not found - log error
                logger.warn("Judiciary not found for email: {}", email);
                if (isNotEmpty(executionId)) {
                    final String errorMessage = JUDICIARY_NOT_FOUND.format(email);
                    rotaProcessLogService.saveRotaProcessLog(
                            rotaProcessLog()
                                    .withExecutionId(executionId)
                                    .withErrorCode(JUDICIARY_NOT_FOUND.code())
                                    .withErrorText(errorMessage)
                                    .build()
                    );
                }
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
     * @param venue the venue containing locationId, venueId, and venueName
     * @param exceptionMessages map to store exception messages for missing venue mappings
     * @param requester the requester for making reference data queries
     * @param executionId the execution ID for logging purposes (can be null)
     * @return Optional containing the CourtRoom if found, empty otherwise
     */
    public Optional<CourtRoom> validateAndFindVenue(final Venue venue,
                                                     final Map<String, String> exceptionMessages,
                                                     final Requester requester,
                                                     final String executionId) {
        if (venue == null) {
            logger.warn("Venue is null, cannot validate venue");
            if (isNotEmpty(executionId)) {
                rotaProcessLogService.saveRotaProcessLog(
                        rotaProcessLog()
                                .withExecutionId(executionId)
                                .withErrorCode(REF_DATA_VENUE_NOT_FOUND.code())
                                .withErrorText("Venue is null, cannot validate venue")
                                .build()
                );
            }
            return empty();
        }

        logger.debug("Validating venue - locationId: {}, venueId: {}, venueName: {}",
                venue.getLocationId(), venue.getVenueId(), venue.getVenueName());

        try {
            final Optional<CourtRoom> courtRoomOptional = referenceDataMapperService.findByVenue(venue, exceptionMessages, requester);

            if (courtRoomOptional.isPresent()) {
                logger.debug("Venue validated successfully - found court room: {}", courtRoomOptional.get().getCourtroomId());
                return courtRoomOptional;
            } else {
                // Venue not found - log error
                logger.warn("Venue validation failed - locationId: {}, venueId: {}, venueName: {}",
                        venue.getLocationId(), venue.getVenueId(), venue.getVenueName());
                if (isNotEmpty(executionId)) {
                    final String errorMessage = REF_DATA_VENUE_NOT_FOUND.format(venue.getLocationId(), venue.getVenueName(), venue.getVenueId());
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

    /**
     * Retrieves all court room mappings for location ID to OU code resolution.
     * Used to map rota location IDs to organizational unit codes.
     *
     * @param requester the requester for making reference data queries
     * @return Map of court room UUIDs to CourtRoom objects
     */
    public Map<UUID, CourtRoom> getCourtRoomMappings(final Requester requester) {
        logger.debug("Retrieving court room mappings");
        final Map<UUID, CourtRoom> courtRoomsMap = referenceDataMapperService.getCourtRoomsMap(requester);
        logger.debug("Retrieved {} court room mappings", courtRoomsMap.size());
        return courtRoomsMap;
    }

    /**
     * Retrieves business type mappings for validation.
     * Used to validate business types from the rota file.
     *
     * @param requester the requester for making reference data queries
     * @return Map of business type codes to BusinessType objects
     */
    public Map<String, BusinessType> getBusinessTypeMappings(final Requester requester) {
        logger.debug("Retrieving business type mappings");
        final Map<String, BusinessType> businessTypesMap = referenceDataMapperService.getBusinessTypeMap(requester);
        logger.debug("Retrieved {} business type mappings", businessTypesMap.size());
        return businessTypesMap;
    }

    /**
     * Validates and retrieves session allocation information.
     * Checks if a session allocation exists for the given OU code, room ID, session, and business type.
     *
     * @param requester the requester for making reference data queries
     * @param ouCode the organizational unit code
     * @param roomId the court room ID
     * @param listingSession the listing session (e.g., "AM", "PM", "ALL_DAY")
     * @param businessType the business type code
     * @return Optional containing the CourtRoomSessionAllocation if found, empty otherwise
     */
    public Optional<CourtRoomSessionAllocation> validateAndFindSessionAllocation(final Requester requester,
                                                                                  final String ouCode,
                                                                                  final Integer roomId,
                                                                                  final String listingSession,
                                                                                  final String businessType) {
        if (ouCode == null || roomId == null || listingSession == null || businessType == null) {
            logger.warn("Session allocation validation skipped - missing required parameters: ouCode={}, roomId={}, listingSession={}, businessType={}",
                    ouCode, roomId, listingSession, businessType);
            return empty();
        }

        logger.debug("Validating session allocation - ouCode: {}, roomId: {}, listingSession: {}, businessType: {}",
                ouCode, roomId, listingSession, businessType);

        final Optional<CourtRoomSessionAllocation> sessionAllocation =
                referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(
                        requester, ouCode, roomId, listingSession, businessType);

        if (sessionAllocation.isPresent()) {
            logger.debug("Session allocation validated successfully - found allocation for ouCode: {}, roomId: {}",
                    ouCode, roomId);
        } else {
            logger.debug("Session allocation not found - ouCode: {}, roomId: {}, listingSession: {}, businessType: {}",
                    ouCode, roomId, listingSession, businessType);
        }

        return sessionAllocation;
    }
}

