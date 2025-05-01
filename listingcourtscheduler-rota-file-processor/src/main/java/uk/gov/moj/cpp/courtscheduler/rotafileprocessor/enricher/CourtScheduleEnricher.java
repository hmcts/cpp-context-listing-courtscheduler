package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.utils.ProcessingDataInfoMessages.SESSION_ALLOCATION_MAX_SLOT_UPDATE_MSG;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.LOCATION_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.VENUE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.VENUE_NAME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_MORNING_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_MORNING_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.COURT_DETAIL_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingDataErrorMessages.COURT_ROOM_ERR_MSG;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public CourtSchedule build(final Map<String, String> listingProfile,
                               final LocalDate sessionDate,
                               final Map<String, String> missingReferenceDataMappingMap,
                               final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                               final Requester requester) {
        final CourtSchedule.CourtScheduleBuilder builder = new CourtSchedule.CourtScheduleBuilder();
        final String businessTypeCode = listingProfile.get(BUSINESS_TYPE);
        final String courtSessionStr = listingProfile.get(SESSION);
        final Integer locationId = Integer.parseInt(listingProfile.get(LOCATION_ID));
        final String venueName = listingProfile.get(VENUE_NAME);
        final Integer venueId = Integer.parseInt(listingProfile.get(VENUE_ID));
        final Optional<CourtRoom> courtRoom = courtRoom(locationId, venueId, venueName, missingReferenceDataMappingMap, requester);
        if (courtRoom.isPresent()) {
            final CourtRoom courtRoomDetail = courtRoom.get();
            populateCourtProperties(builder, courtRoomDetail);
            populateListingProperties(builder, listingProfile, sessionDate, courtSessionStr, businessTypeCode);
            populateSessionAllocation(builder, businessTypeCode, sessionDate, courtSessionStr, courtRoomDetail, requester);

            final Optional<CourtSchedule> courtScheduleOptional = activeCourtSchedulesByOuCodesWithinRotaPeriod.stream()
                    .filter(activeCourtSchedule -> activeCourtSchedule.getCourtRoomId().equals(builder.getCourtRoomId())
                    && activeCourtSchedule.getSessionDate().equals(builder.getSessionDate())
                    && activeCourtSchedule.getBusinessType().equals(builder.getBusinessType())
                    && activeCourtSchedule.getCourtSession().equals(builder.getCourtSession()))
                    .findAny();
            if (courtScheduleOptional.isPresent() && isNotEmpty(courtScheduleOptional.get().getCourtScheduleId())) {
                final CourtSchedule courtSchedule = courtScheduleOptional.get();
                logger.info("slot matched between file and db with ouCode: {} - courtScheduleId: {}", courtSchedule.getOuCode(), courtSchedule.getCourtScheduleId());
                builder.withCourtScheduleId(courtSchedule.getCourtScheduleId());
                builder.withCreatedOn(courtSchedule.getCreatedOn());
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

        if (AM_SESSION.equals(courtSessionStr)) {
            builder.withSessionStartTime(DateUtils.combineDateAndTime(sessionDate, DEFAULT_MORNING_START_TIME))
                    .withSessionEndTime(DateUtils.combineDateAndTime(sessionDate, DEFAULT_MORNING_END_TIME));
        } else if (PM_SESSION.equals(courtSessionStr)) {
            builder.withSessionStartTime(DateUtils.combineDateAndTime(sessionDate, DEFAULT_AFTERNOON_START_TIME))
                    .withSessionEndTime(DateUtils.combineDateAndTime(sessionDate, DEFAULT_AFTERNOON_END_TIME));
        }
            builder.withNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));

    }

    private void populateSessionAllocation(final CourtSchedule.CourtScheduleBuilder builder,
                                           final String businessTypeCode,
                                           final LocalDate sessionDate,
                                           final String courtSessionStr,
                                           final CourtRoom courtRoomDetail,
                                           final Requester requester) {
        final String listingSession = courtSession.getCourtSession(sessionDate, courtSessionStr);
        final Optional<CourtRoomSessionAllocation> sessionAllocation = referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(requester, courtRoomDetail.getOucode(), courtRoomDetail.getCppCourtRoomId(), listingSession, businessTypeCode);
        logger.debug("called referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType - with ouCode : {}, courtRoomNumber: {}, listingSession: {}, businessType: {} - with result : {}",
                courtRoomDetail.getOucode(), courtRoomDetail.getCppCourtRoomId(), listingSession, businessTypeCode, sessionAllocation);
        if (sessionAllocation.isPresent()) {
            final uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation allocation = sessionAllocation.get();
            populateSessionAllocationProperties(builder, allocation);
            logger.info(format(SESSION_ALLOCATION_MAX_SLOT_UPDATE_MSG, allocation.getOucode(), allocation.getCourtRoomId(), builder.getSessionDate(), allocation.getCourtSession(), allocation.getRotaBusinessTypeCode(), allocation.getMaxSlot(), allocation.getMaxDurationMins()));
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
