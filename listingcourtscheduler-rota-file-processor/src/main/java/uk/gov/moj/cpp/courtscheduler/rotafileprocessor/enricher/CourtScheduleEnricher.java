package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.LOCATION_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.VENUE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.VENUE_NAME;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.COURT_DETAIL_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.COURT_ROOM_ERR_MSG;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.SESSION_ALLOCATION_ERR_MSG;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.SESSION_ALLOCATION_NOT_FOUND;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class CourtScheduleEnricher {

    private static final Logger logger = LoggerFactory.getLogger(CourtScheduleEnricher.class);

    @Inject
    private CourtSession courtSession;

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private SessionsService sessionsService;

    private final Map<String, String> missingReferenceDataMappingMap = new ConcurrentHashMap<>();

    public CourtSchedule build(final Map<String, String> listingProfile, final LocalDate sessionDate, final Requester requester) {
        final CourtSchedule.CourtScheduleBuilder builder = new CourtSchedule.CourtScheduleBuilder();
        final String businessType = listingProfile.get(BUSINESS_TYPE);
        final String courtSessionStr = listingProfile.get(SESSION);
        final Integer locationId = Integer.parseInt(listingProfile.get(LOCATION_ID));
        final String venueName = listingProfile.get(VENUE_NAME);
        final Integer venueId = Integer.parseInt(listingProfile.get(VENUE_ID));
        final Optional<CourtRoom> courtRoom = courtRoom(locationId, venueId, venueName, missingReferenceDataMappingMap, requester);
        if (courtRoom.isPresent()) {
            final CourtRoom courtRoomDetail = courtRoom.get();
            populateCourtProperties(builder, courtRoomDetail);
            populateListingProperties(builder, listingProfile, sessionDate, courtSessionStr, businessType);
            populateSessionAllocation(builder, businessType, sessionDate, courtSessionStr, courtRoomDetail, requester);

            final CourtScheduleMatcherInfo courtScheduleMatcherInfo = sessionsService.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(builder.getCourtRoomId(), builder.getSessionDate(), builder.getBusinessType(), builder.getCourtSession());
            if (nonNull(courtScheduleMatcherInfo) && isNotEmpty(courtScheduleMatcherInfo.getCourtScheduleId())) {
                builder.withCourtScheduleId(courtScheduleMatcherInfo.getCourtScheduleId());
                builder.withCreatedOn(courtScheduleMatcherInfo.getCreatedOn());
            }
        } else {
            final String msgKey = format(COURT_ROOM_ERR_MSG, locationId, venueName, venueId);
            missingReferenceDataMappingMap.putIfAbsent(msgKey, COURT_DETAIL_NOT_FOUND);
        }
        return builder.withActive(true).build();
    }

    private void populateListingProperties(final CourtSchedule.CourtScheduleBuilder builder,
                                           final Map<String, String> listingProfile,
                                           final LocalDate sessionDate,
                                           final String courtSessionStr,
                                           final String businessType) {
        builder.withCourtScheduleId(randomUUID().toString())
                .withListingProfileId(listingProfile.get(ID))
                .withPanel(listingProfile.get(PANEL))
                .withBusinessType(businessType)
                .withSessionDate(sessionDate)
                .withCourtSession(courtSessionStr);
    }

    private void populateSessionAllocation(final CourtSchedule.CourtScheduleBuilder builder,
                                           final String businessType,
                                           final LocalDate sessionDate,
                                           final String courtSessionStr,
                                           final CourtRoom courtRoomDetail,
                                           final Requester requester) {
        final String listingSession = courtSession.getCourtSession(sessionDate, courtSessionStr);
        final Optional<CourtRoomSessionAllocation> sessionAllocation = referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(requester, courtRoomDetail.getOucode(), courtRoomDetail.getCppCourtRoomId(), listingSession, businessType);
        logger.debug("called referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType - with ouCode : {}, courtRoomNumber: {}, listingSession: {}, businessType: {} - with result : {}",
                courtRoomDetail.getOucode(), courtRoomDetail.getCppCourtRoomId(), listingSession, businessType, sessionAllocation);
        if (sessionAllocation.isPresent()) {
            final uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation allocation = sessionAllocation.get();
            populateSessionAllocationProperties(builder, allocation);
        } else {
            final String msgKey = format(SESSION_ALLOCATION_ERR_MSG,
                    courtRoomDetail.getOucode(),
                    courtRoomDetail.getCppCourtRoomId(),
                    businessType,
                    courtSession.getCourtSession(sessionDate, courtSessionStr));
            missingReferenceDataMappingMap.put(msgKey, SESSION_ALLOCATION_NOT_FOUND);
        }
    }

    private void populateCourtProperties(final CourtSchedule.CourtScheduleBuilder builder, final CourtRoom courtRoomDetail) {
        builder.withOuCode(courtRoomDetail.getOucode())
                .withOperationalUnit(courtRoomDetail.getOucodeL2Code())
                .withCourtHouseName(courtRoomDetail.getOucodeL3Name())
                .withCourtHouseId(courtRoomDetail.getOucodeUUID())
                .withCourtRoomId(courtRoomDetail.getCourtroomId())
                .withCourtRoomNumber(courtRoomDetail.getCppCourtRoomId())
                .withCourtRoomName(courtRoomDetail.getCourtroomName());
    }

    private void populateSessionAllocationProperties(final CourtSchedule.CourtScheduleBuilder builder, final CourtRoomSessionAllocation allocation) {
        builder.withMaxSlots(allocation.getMaxSlot())
                .withAvailableSlots(allocation.getMaxSlot())
                .withMaxDuration(allocation.getMaxDurationMins())
                .withAvailableDuration(allocation.getMaxDurationMins());
    }

    private Optional<CourtRoom> courtRoom(final Integer locationId, final Integer venueId, final String venueName, final Map<String, String> exceptionMessages, final Requester requester) {
        return referenceDataMapperService.findByVenue(new Venue(locationId, venueId, venueName), exceptionMessages, requester);
    }
}
