package uk.gov.moj.cpp.courtscheduler.common.service.mapper;

import static java.util.Objects.isNull;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

public class CourtScheduleMapper {

    // Private constructor to prevent instantiation
    private CourtScheduleMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static CourtSchedule toEntity(uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule domain) {
        if (isNull(domain)) {
            return null;
        }

        final CourtSchedule entity = new CourtSchedule();
        entity.setCourtScheduleId(domain.getCourtScheduleId());
        entity.setListingProfileId(domain.getListingProfileId());
        entity.setOuCode(domain.getOuCode());
        entity.setCourtRoomId(domain.getCourtRoomId());
        entity.setCourtRoomNumber(domain.getCourtRoomNumber());
        entity.setCourtHouseId(domain.getCourtHouseId());
        entity.setCourtHouseName(domain.getCourtHouseName());
        entity.setCourtRoomName(domain.getCourtRoomName());
        entity.setOperationalUnit(domain.getOperationalUnit());
        entity.setBusinessType(domain.getBusinessType());
        entity.setPanel(domain.getPanel());
        entity.setCourtSession(domain.getCourtSession());
        entity.setActive(domain.getActive());
        entity.setSlotBased(Boolean.TRUE.equals(domain.getSlotBased()));
        entity.setSessionDate(domain.getSessionDate());
        entity.setMaxSlots(domain.getMaxSlots());
        entity.setMaxDuration(domain.getMaxDuration());
        entity.setAvailableSlots(domain.getAvailableSlots());
        entity.setAvailableDuration(domain.getAvailableDuration());
        entity.setCreatedOn(toDate(domain.getCreatedOn()));
        entity.setSupportAdSplit(domain.getAllDaySplit());
        entity.setMaxAdMorningDuration(domain.getMaxDurationForMorning());
        entity.setMaxAdAfternoonDuration(domain.getMaxDurationForAfternoon());
        entity.setSessionStartTime(toDate(domain.getSessionStartTime()));
        entity.setSessionEndTime(toDate(domain.getSessionEndTime()));
        entity.setIsOverbookingAllowed(domain.getOverbookingAllowed());
        entity.setNationalBreakTime(toDate(domain.getNationalBreakTime()));
        entity.setIsDraft(domain.getDraft());
        entity.setJurisdiction(domain.getJurisdiction());
        return entity;
    }

    public static uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule toDomain(CourtSchedule entity) {
        if (isNull(entity)) {
            return null;
        }

        return new uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule()
                .courtScheduleId(entity.getCourtScheduleId())
                .listingProfileId(entity.getListingProfileId())
                .ouCode(entity.getOuCode())
                .courtRoomId(entity.getCourtRoomId())
                .courtRoomNumber(entity.getCourtRoomNumber())
                .courtHouseId(entity.getCourtHouseId())
                .courtHouseName(entity.getCourtHouseName())
                .courtRoomName(entity.getCourtRoomName())
                .operationalUnit(entity.getOperationalUnit())
                .businessType(entity.getBusinessType())
                .panel(entity.getPanel())
                .courtSession(entity.getCourtSession())
                .sessionDate(entity.getSessionDate())
                .slotBased(entity.isSlotBased())
                .maxSlots(entity.getMaxSlots())
                .maxDuration(entity.getMaxDuration())
                .availableSlots(entity.getAvailableSlots())
                .availableDuration(entity.getAvailableDuration())
                .active(entity.isActive())
                .createdOn(toOffsetDateTime(entity.getCreatedOn()))
                .nationalBreakTime(toOffsetDateTime(entity.getNationalBreakTime()))
                .draft(entity.getIsDraft())
                .jurisdiction(entity.getJurisdiction());
    }

    private static OffsetDateTime toOffsetDateTime(final Date date) {
        return date == null ? null : date.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static Date toDate(final OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : Date.from(offsetDateTime.toInstant());
    }
}
