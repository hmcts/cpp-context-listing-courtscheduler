package uk.gov.moj.cpp.courtscheduler.common.service.mapper;

import static java.util.Objects.isNull;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

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
        entity.setActive(Boolean.TRUE.equals(domain.getActive()));
        entity.setSlotBased(Boolean.TRUE.equals(domain.getSlotBased()));
        entity.setSessionDate(domain.getSessionDate());
        entity.setMaxSlots(domain.getMaxSlots());
        entity.setMaxDuration(domain.getMaxDuration());
        entity.setAvailableSlots(domain.getAvailableSlots());
        entity.setAvailableDuration(domain.getAvailableDuration());
        entity.setCreatedOn(domain.getCreatedOn() != null ? java.util.Date.from(domain.getCreatedOn().toInstant()) : null);
        entity.setSupportAdSplit(Boolean.TRUE.equals(domain.getAllDaySplit()));
        entity.setMaxAdMorningDuration(domain.getMaxDurationForMorning());
        entity.setMaxAdAfternoonDuration(domain.getMaxDurationForAfternoon());
        entity.setSessionStartTime(domain.getSessionStartTime() != null ? java.util.Date.from(domain.getSessionStartTime().toInstant()) : null);
        entity.setSessionEndTime(domain.getSessionEndTime() != null ? java.util.Date.from(domain.getSessionEndTime().toInstant()) : null);
        entity.setIsOverbookingAllowed(Boolean.TRUE.equals(domain.getOverbookingAllowed()));
        entity.setNationalBreakTime(domain.getNationalBreakTime() != null ? java.util.Date.from(domain.getNationalBreakTime().toInstant()) : null);
        entity.setIsDraft(Boolean.TRUE.equals(domain.getDraft()));
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
}
