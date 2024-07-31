package uk.gov.moj.cpp.courtscheduler.converter;

public final class CourtSchedulerConverter {

    private CourtSchedulerConverter() {
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule convert(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        return new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder()
                .withListingProfileId(courtScheduleEntity.getListingProfileId())
                .withOuCode(courtScheduleEntity.getOuCode())
                .withCourtRoomNumber(courtScheduleEntity.getCourtRoomNumber())
                .withOperationalUnit(courtScheduleEntity.getOperationalUnit())
                .withCourtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .withAvailableDuration(courtScheduleEntity.getAvailableDuration())
                .withMaxDuration(courtScheduleEntity.getMaxDuration())
                .withAvailableSlots(courtScheduleEntity.getAvailableSlots())
                .withMaxSlots(courtScheduleEntity.getMaxSlots())
                .withBusinessType(courtScheduleEntity.getBusinessType())
                .withCourtHouseId(courtScheduleEntity.getCourtHouseId())
                .withCourtHouseName(courtScheduleEntity.getCourtHouseName())
                .withCourtRoomId(courtScheduleEntity.getCourtRoomId())
                .withCourtRoomName(courtScheduleEntity.getCourtRoomName())
                .withCourtSession(courtScheduleEntity.getCourtSession())
                .withSessionDate(courtScheduleEntity.getSessionDate())
                .withSlotBased(courtScheduleEntity.isSlotBased())
                .withActive(courtScheduleEntity.isActive())
                .withPanel(courtScheduleEntity.getPanel())
                .withCreatedOn(courtScheduleEntity.getCreatedOn())
                .withUpdatedOn(courtScheduleEntity.getUpdatedOn())
                .build();
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule convertToMi(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        return new uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule.CourtScheduleBuilder()
                .withListingProfileId(courtScheduleEntity.getListingProfileId())
                .withOuCode(courtScheduleEntity.getOuCode())
                .withCourtRoomNumber(courtScheduleEntity.getCourtRoomNumber())
                .withOperationalUnit(courtScheduleEntity.getOperationalUnit())
                .withCourtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .withAvailableDuration(courtScheduleEntity.getAvailableDuration())
                .withMaxDuration(courtScheduleEntity.getMaxDuration())
                .withAvailableSlots(courtScheduleEntity.getAvailableSlots())
                .withMaxSlots(courtScheduleEntity.getMaxSlots())
                .withBusinessType(courtScheduleEntity.getBusinessType())
                .withCourtHouseId(courtScheduleEntity.getCourtHouseId())
                .withCourtHouseName(courtScheduleEntity.getCourtHouseName())
                .withCourtRoomId(courtScheduleEntity.getCourtRoomId())
                .withCourtRoomName(courtScheduleEntity.getCourtRoomName())
                .withCourtSession(courtScheduleEntity.getCourtSession())
                .withSessionDate(courtScheduleEntity.getSessionDate())
                .withSlotBased(courtScheduleEntity.isSlotBased())
                .withActive(courtScheduleEntity.isActive())
                .withPanel(courtScheduleEntity.getPanel())
                .withCreatedOn(courtScheduleEntity.getCreatedOn())
                .withUpdatedOn(courtScheduleEntity.getUpdatedOn())
                .build();
    }
}
