package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.REF_DATA_VENUE_NOT_FOUND;
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
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.resolveSessionTime;

// (removed) replaced by Spring CommonPlatformQueryClient
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

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CourtScheduleEnricher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleEnricher.class);

    @Inject
    private CourtSession courtSession;

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    public CourtSchedule build(final Map<String, String> listingProfile,
                               final LocalDate sessionDate,
                               final Map<String, String> missingReferenceDataMappingMap,
                               final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                               final String executionId) {
        final CourtSchedule.CourtScheduleBuilder builder = new CourtSchedule.CourtScheduleBuilder();
        final String businessTypeCode = listingProfile.get(BUSINESS_TYPE);
        final String courtSessionStr = listingProfile.get(SESSION);
        final Integer locationId = Integer.parseInt(listingProfile.get(LOCATION_ID));
        final String venueName = listingProfile.get(VENUE_NAME);
        final Integer venueId = Integer.parseInt(listingProfile.get(VENUE_ID));
        final Optional<CourtRoom> courtRoom = courtRoom(locationId, venueId, venueName, missingReferenceDataMappingMap);
        if (courtRoom.isPresent()) {
            final CourtRoom courtRoomDetail = courtRoom.get();
            populateCourtProperties(builder, courtRoomDetail);
            // Fetch session allocation up front so refdata-supplied start/end times
            // can be applied during populateListingProperties (with custom time precedence).
            final Optional<CourtRoomSessionAllocation> sessionAllocation =
                    lookupSessionAllocation(businessTypeCode, sessionDate, courtSessionStr, courtRoomDetail);

            populateListingProperties(builder, listingProfile, sessionDate, courtSessionStr, businessTypeCode, sessionAllocation);
            sessionAllocation.ifPresent(allocation -> {
                populateSessionAllocationProperties(builder, allocation);
                LOGGER.info(format(SESSION_ALLOCATION_MAX_SLOT_UPDATE_MSG, allocation.getOucode(), allocation.getCourtRoomId(), builder.getSessionDate(), allocation.getCourtSession(), allocation.getRotaBusinessTypeCode(), allocation.getMaxSlot(), allocation.getMaxDurationMins()));
            });

            final Optional<CourtSchedule> courtScheduleOptional = activeCourtSchedulesByOuCodesWithinRotaPeriod.stream()
                    .filter(activeCourtSchedule -> activeCourtSchedule.getCourtRoomId().equals(builder.getCourtRoomId())
                            && activeCourtSchedule.getSessionDate().equals(builder.getSessionDate())
                            && activeCourtSchedule.getBusinessType().equals(builder.getBusinessType())
                            && activeCourtSchedule.getCourtSession().equals(builder.getCourtSession()))
                    .findAny();
            if (courtScheduleOptional.isPresent() && isNotEmpty(courtScheduleOptional.get().getCourtScheduleId())) {
                final CourtSchedule courtSchedule = courtScheduleOptional.get();
                LOGGER.info("slot matched between file and db with ouCode: {} - courtScheduleId: {}", courtSchedule.getOuCode(), courtSchedule.getCourtScheduleId());
                builder.withCourtScheduleId(courtSchedule.getCourtScheduleId());
                builder.withCreatedOn(courtSchedule.getCreatedOn());
            }
        } else {
            final String venueDetails = format("%d - %s - %d", locationId, venueName, venueId);
            missingReferenceDataMappingMap.putIfAbsent(venueDetails, REF_DATA_VENUE_NOT_FOUND.code());
        }
        return builder.withActive(true).build();
    }

    private void populateListingProperties(final CourtSchedule.CourtScheduleBuilder builder,
                                           final Map<String, String> listingProfile,
                                           final LocalDate sessionDate,
                                           final String courtSessionStr,
                                           final String businessType,
                                           final Optional<CourtRoomSessionAllocation> sessionAllocation) {
        builder.withCourtScheduleId(randomUUID().toString())
                .withListingProfileId(listingProfile.get(ID))
                .withPanel(listingProfile.get(PANEL))
                .withBusinessType(businessType)
                .withSessionDate(sessionDate)
                .withCourtSession(courtSessionStr);

        // Precedence: refdata allocation time > hardcoded defaults.
        // (Rota file rows do not carry custom session times; custom times are only honoured
        // on the courtscheduler.create API path - see SessionsService.)
        final String refDataStartTime = sessionAllocation.map(CourtRoomSessionAllocation::getSessionStartTime).orElse(null);
        final String refDataEndTime = sessionAllocation.map(CourtRoomSessionAllocation::getSessionEndTime).orElse(null);

        if (AM_SESSION.equals(courtSessionStr)) {
            final String startTime = resolveSessionTime(null, refDataStartTime, DEFAULT_MORNING_START_TIME);
            final String endTime = resolveSessionTime(null, refDataEndTime, DEFAULT_MORNING_END_TIME);
            builder.withSessionStartTime(DateUtils.combineDateAndTime(sessionDate, startTime))
                    .withSessionEndTime(DateUtils.combineDateAndTime(sessionDate, endTime));
        } else if (PM_SESSION.equals(courtSessionStr)) {
            final String startTime = resolveSessionTime(null, refDataStartTime, DEFAULT_AFTERNOON_START_TIME);
            final String endTime = resolveSessionTime(null, refDataEndTime, DEFAULT_AFTERNOON_END_TIME);
            builder.withSessionStartTime(DateUtils.combineDateAndTime(sessionDate, startTime))
                    .withSessionEndTime(DateUtils.combineDateAndTime(sessionDate, endTime));
        }
        builder.withNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));

    }

    private Optional<CourtRoomSessionAllocation> lookupSessionAllocation(final String businessTypeCode,
                                                                        final LocalDate sessionDate,
                                                                        final String courtSessionStr,
                                                                        final CourtRoom courtRoomDetail) {
        final String listingSession = courtSession.getCourtSession(sessionDate, courtSessionStr);
        final Optional<CourtRoomSessionAllocation> sessionAllocation = referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(courtRoomDetail.getOucode(), courtRoomDetail.getCppCourtRoomId(), listingSession, businessTypeCode);
        LOGGER.debug("called referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType - with ouCode : {}, courtRoomNumber: {}, listingSession: {}, businessType: {} - with result : {}",
                courtRoomDetail.getOucode(), courtRoomDetail.getCppCourtRoomId(), listingSession, businessTypeCode, sessionAllocation);
        return sessionAllocation;
    }

    private void populateCourtProperties(final CourtSchedule.CourtScheduleBuilder builder, final CourtRoom courtRoomDetail) {
        builder.withOuCode(courtRoomDetail.getOucode())
                .withOperationalUnit(courtRoomDetail.getOucodeL2Code())
                .withCourtHouseName(courtRoomDetail.getOucodeL3Name())
                .withCourtHouseId(courtRoomDetail.getOucodeUUID())
                .withCourtRoomId(courtRoomDetail.getCourtroomId())
                .withCourtRoomNumber(courtRoomDetail.getCppCourtRoomId())
                .withCourtRoomName(courtRoomDetail.getCourtroomName())
                .withJurisdiction(MAGISTRATES.getJurisdiction());
    }

    private void populateSessionAllocationProperties(final CourtSchedule.CourtScheduleBuilder builder, final CourtRoomSessionAllocation allocation) {
        builder.withMaxSlots(allocation.getMaxSlot())
                .withAvailableSlots(allocation.getMaxSlot())
                .withMaxDuration(allocation.getMaxDurationMins())
                .withAvailableDuration(allocation.getMaxDurationMins());
    }

    private Optional<CourtRoom> courtRoom(final Integer locationId, final Integer venueId, final String venueName, final Map<String, String> exceptionMessages) {
        return referenceDataMapperService.findByVenue(new Venue(locationId, venueId, venueName), exceptionMessages);
    }
}
