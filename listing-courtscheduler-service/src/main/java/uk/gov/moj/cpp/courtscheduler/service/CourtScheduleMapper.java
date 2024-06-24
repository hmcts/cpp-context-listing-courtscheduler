package uk.gov.moj.cpp.courtscheduler.service;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

public class CourtScheduleMapper {

    // Private constructor to prevent instantiation
    private CourtScheduleMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static CourtSchedule toEntity(uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule domain) {
        if (domain == null) {
            return null;
        }

        CourtSchedule entity = new CourtSchedule();
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
        entity.setActive(domain.isActive());
        entity.setSlotBased(domain.isSlotBased());
        entity.setSessionDate(domain.getSessionDate());
        entity.setMaxSlots(domain.getMaxSlots());
        entity.setMaxDuration(domain.getMaxDuration());
        entity.setAvailableSlots(domain.getAvailableSlots());
        entity.setAvailableDuration(domain.getAvailableDuration());
        return entity;
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule toDomain(CourtSchedule entity) {
        if (entity == null) {
            return null;
        }

        return uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(entity.getCourtScheduleId())
                .withListingProfileId(entity.getListingProfileId())
                .withOuCode(entity.getOuCode())
                .withCourtRoomId(entity.getCourtRoomId())
                .withCourtRoomNumber(entity.getCourtRoomNumber())
                .withCourtHouseId(entity.getCourtHouseId())
                .withCourtHouseName(entity.getCourtHouseName())
                .withCourtRoomName(entity.getCourtRoomName())
                .withOperationalUnit(entity.getOperationalUnit())
                .withBusinessType(entity.getBusinessType())
                .withPanel(entity.getPanel())
                .withCourtSession(entity.getCourtSession())
                .withSessionDate(entity.getSessionDate())
                .withSlotBased(entity.isSlotBased())
                .withMaxSlots(entity.getMaxSlots())
                .withMaxDuration(entity.getMaxDuration())
                .withAvailableSlots(entity.getAvailableSlots())
                .withAvailableDuration(entity.getAvailableDuration())
                .build();
    }
}
