package uk.gov.moj.cpp.courtscheduler.converter;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

public final class CourtSchedulerConverter {

    public final static DateFormat DATE_FORMAT = new SimpleDateFormat("MMM dd, yyyy, HH:mm:ss a");

    private CourtSchedulerConverter() {
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule convert(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        return new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder()
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
                .withCreatedOn(DATE_FORMAT.format(courtScheduleEntity.getCreatedOn()))
                .withUpdatedOn(DATE_FORMAT.format(courtScheduleEntity.getUpdatedOn()))
                .build();
    }
}
