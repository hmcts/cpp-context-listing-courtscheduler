package uk.gov.moj.cpp.courtscheduler.converter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CourtSchedulerConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtSchedulerConverter.class);

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
                .withSessionDate(courtScheduleEntity.getSessionDate() == null ? null : getDate(courtScheduleEntity.getSessionDate()))
                .withSlotBased(courtScheduleEntity.isSlotBased())
                .withActive(courtScheduleEntity.isActive())
                .withPanel(courtScheduleEntity.getPanel())
                .withCreatedOn(courtScheduleEntity.getCreatedOn())
                .withUpdatedOn(courtScheduleEntity.getUpdatedOn())
                .build();
    }

    private static Date getDate(LocalDate localDate) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(localDate.toString());
        } catch (ParseException e) {
            LOGGER.error("Unable to parse date from, {}", localDate);
            return null;
        }
    }
}
