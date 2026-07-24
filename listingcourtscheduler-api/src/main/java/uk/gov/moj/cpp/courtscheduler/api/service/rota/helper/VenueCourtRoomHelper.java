package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.LOCATION_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.VENUE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.VENUE_NAME;

// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.Map;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for venue and court room operations.
 */
@Service
public class VenueCourtRoomHelper {

    private static final Logger logger = LoggerFactory.getLogger(VenueCourtRoomHelper.class);

    @Inject
    private RotaReferenceDataService referenceDataValidationService;

    /**
     * Extracts and validates court room information from a listing profile.
     *
     * @param listingProfile                 the listing profile containing venue information
     * @param requester                      the requester for making reference data queries
     * @param executionId                    the execution ID for logging purposes
     * @param missingReferenceDataMappingMap map to store missing reference data mappings
     * @return the CourtRoom if found, null otherwise
     */
    public CourtRoom getCourtRoom(final Map<String, String> listingProfile,
                                  final String executionId,
                                  final Map<String, String> missingReferenceDataMappingMap) {
        final String locationIdStr = listingProfile.get(LOCATION_ID);
        final String venueIdStr = listingProfile.get(VENUE_ID);
        final String venueName = listingProfile.get(VENUE_NAME);

        if (!isNotEmpty(locationIdStr) || !isNotEmpty(venueIdStr) || !isNotEmpty(venueName)) {
            logger.debug("Missing venue information for court listing");
            return null;
        }

        return parseAndValidateVenue(locationIdStr, venueIdStr, venueName, executionId, missingReferenceDataMappingMap);
    }

    /**
     * Parses and validates venue information to retrieve a CourtRoom.
     *
     * @param locationIdStr                  the location ID as a string
     * @param venueIdStr                     the venue ID as a string
     * @param venueName                      the venue name
     * @param requester                      the requester for making reference data queries
     * @param executionId                    the execution ID for logging purposes
     * @param missingReferenceDataMappingMap map to store missing reference data mappings
     * @return the CourtRoom if found, null otherwise
     */
    private CourtRoom parseAndValidateVenue(final String locationIdStr,
                                            final String venueIdStr,
                                            final String venueName,
                                            final String executionId,
                                            final Map<String, String> missingReferenceDataMappingMap) {
        try {
            final Integer locationId = Integer.parseInt(locationIdStr);
            final Integer venueId = Integer.parseInt(venueIdStr);
            final Venue venue = new Venue(locationId, venueId, venueName);

            final CourtRoom courtRoom = referenceDataValidationService.validateAndFindVenue(venue, missingReferenceDataMappingMap, executionId)
                    .orElse(null);
            if (courtRoom != null) {
                logger.info("Successfully validated venue and found court room - locationId: {}, venueId: {}, venueName: {}, courtRoomId: {}", 
                        locationId, venueId, venueName, courtRoom.getCourtroomId());
            }
            return courtRoom;
        } catch (final NumberFormatException ex) {
            logger.warn("Invalid locationId or venueId format: locationId={}, venueId={}", locationIdStr, venueIdStr);
            return null;
        }
    }
}

