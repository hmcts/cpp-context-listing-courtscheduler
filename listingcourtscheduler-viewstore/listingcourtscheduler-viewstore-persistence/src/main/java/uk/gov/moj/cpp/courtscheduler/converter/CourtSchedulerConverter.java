package uk.gov.moj.cpp.courtscheduler.converter;

import static java.util.Comparator.comparing;
import static java.util.Objects.nonNull;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.BookingUtils.updateTotalBooked;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CourtSchedulerConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourtSchedulerConverter.class);
    public static final int DEFAULT_DURATION = 180;

    private CourtSchedulerConverter() {
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule convert(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        final Boolean isAllDaySplit = courtScheduleEntity.getSupportAdSplit();
        final CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = new CourtSchedule.CourtScheduleBuilder()
                .withListingProfileId(courtScheduleEntity.getListingProfileId())
                .withOuCode(courtScheduleEntity.getOuCode())
                .withCourtRoomNumber(courtScheduleEntity.getCourtRoomNumber())
                .withOperationalUnit(courtScheduleEntity.getOperationalUnit())
                .withCourtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .withMaxDuration(courtScheduleEntity.getMaxDuration())
                .withTotalBooked(courtScheduleEntity.getTotalBooked())
                .withAvailableDuration(courtScheduleEntity.getAvailableDuration())
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
                .withAllDaySplit(nonNull(isAllDaySplit) && isAllDaySplit)
                .withCreatedOn(courtScheduleEntity.getCreatedOn())
                .withUpdatedOn(courtScheduleEntity.getUpdatedOn())
                .withSessionStartTime(courtScheduleEntity.getSessionStartTime())
                .withSessionEndTime(courtScheduleEntity.getSessionEndTime())
                .withIsOverbookingAllowed(courtScheduleEntity.getIsOverbookingAllowed())
                .withIsDraft(courtScheduleEntity.getIsDraft())
                .withMinHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionStartTime()))
                .withMaxHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionEndTime()));
        if (nonNull(isAllDaySplit) && Boolean.TRUE.equals(isAllDaySplit)) {
            courtScheduleBuilder
                    .withMaxDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration())
                    .withMaxDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration())
                    .withTotalBookedForMorning(courtScheduleEntity.getTotalBookedMorning())
                    .withTotalBookedForAfternoon(courtScheduleEntity.getTotalBookedAfternoon())
                    .withAvailableDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration() - courtScheduleEntity.getTotalBookedMorning())
                    .withAvailableDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration() - courtScheduleEntity.getTotalBookedAfternoon());
        }

        return courtScheduleBuilder.build();
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule convertForOverbooking(
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        return new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .withIsOverbookingAllowed(Boolean.TRUE.equals(courtScheduleEntity.getIsOverbookingAllowed()))
                .withActive(courtScheduleEntity.isActive())
                .build();
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule convert(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity,
                                                                             final List<AllocatedListingEachBooked> allocatedListingEachBooked) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        final List<AllocatedListingEachBooked> allocatedListings = allocatedListingEachBooked.stream()
                .filter(eachBooked -> eachBooked.getCourtScheduleId().equals(courtScheduleEntity.getCourtScheduleId()))
                .toList();

        final Integer totalBooked = allocatedListings.stream().mapToInt(AllocatedListingEachBooked::getDuration).sum();
        final Boolean isAllDaySplit = courtScheduleEntity.getSupportAdSplit();
        final CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = new uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder()
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
                .withTotalBooked(totalBooked)
                .withAllDaySplit(nonNull(isAllDaySplit) && isAllDaySplit)
                .withMaxDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration())
                .withMaxDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration())
                .withCreatedOn(courtScheduleEntity.getCreatedOn())
                .withUpdatedOn(courtScheduleEntity.getUpdatedOn())
                .withSessionStartTime(courtScheduleEntity.getSessionStartTime())
                .withSessionEndTime(courtScheduleEntity.getSessionEndTime())
                .withIsOverbookingAllowed(courtScheduleEntity.getIsOverbookingAllowed())
                .withIsDraft(courtScheduleEntity.getIsDraft())
                .withMinHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionStartTime()))
                .withMaxHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionEndTime()))
                .withJurisdiction(courtScheduleEntity.getJurisdiction());

        final Date minHearingTime = allocatedListings.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .min(Date::compareTo)
                .orElse(null);
        final Date maxHearingTime = allocatedListings.stream()
                .max(comparing(AllocatedListingEachBooked::getHearingStartTime))
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .orElse(null);
        if (minHearingTime != null) {
            courtScheduleBuilder.withMinHearingTime(simpleDateFormat.format(minHearingTime));
        }
        if (maxHearingTime != null) {
            courtScheduleBuilder.withMaxHearingTime(simpleDateFormat.format(maxHearingTime));
        }

        if (Boolean.TRUE.equals(courtScheduleEntity.getSupportAdSplit()) && ALL_DAY.equals(courtScheduleEntity.getCourtSession())) {
            final AtomicInteger totalBookedForMorning = new AtomicInteger(0);
            final AtomicInteger totalBookedForAfternoon = new AtomicInteger(0);
            allocatedListings
                    .forEach(eachBooked -> {
                        if ((eachBooked.getHearingStartTime().after(courtScheduleEntity.getSessionStartTime()) || eachBooked.getHearingStartTime().equals(courtScheduleEntity.getSessionStartTime()))
                                && eachBooked.getHearingStartTime().before(combineDateAndTime(courtScheduleEntity.getSessionDate(), DEFAULT_AFTERNOON_START_TIME))
                        ) {
                            updateTotalBooked(eachBooked.getDuration(), totalBookedForMorning, totalBookedForAfternoon, DEFAULT_DURATION);
                        } else {
                            totalBookedForAfternoon.set(totalBookedForAfternoon.get() + eachBooked.getDuration());
                        }
                    });
            courtScheduleBuilder
                    .withTotalBookedForMorning(totalBookedForMorning.get())
                    .withTotalBookedForAfternoon(totalBookedForAfternoon.get())
                    .withAvailableDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration() - totalBookedForMorning.get())
                    .withAvailableDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration() - totalBookedForAfternoon.get());
        }
        return courtScheduleBuilder.build();
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
