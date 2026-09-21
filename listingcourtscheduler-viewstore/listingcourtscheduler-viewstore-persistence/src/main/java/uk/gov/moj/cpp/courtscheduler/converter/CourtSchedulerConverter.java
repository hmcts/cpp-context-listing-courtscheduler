package uk.gov.moj.cpp.courtscheduler.converter;

import static java.util.Comparator.comparing;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.BookingUtils.updateTotalBooked;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    private static int zeroIfNull(final Integer value) {
        return value == null ? 0 : value;
    }

    public static CourtSchedule convert(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        final Boolean isAllDaySplit = courtScheduleEntity.getSupportAdSplit();
        final CourtSchedule courtSchedule = new CourtSchedule()
                .listingProfileId(courtScheduleEntity.getListingProfileId())
                .ouCode(courtScheduleEntity.getOuCode())
                .courtRoomNumber(courtScheduleEntity.getCourtRoomNumber())
                .operationalUnit(courtScheduleEntity.getOperationalUnit())
                .courtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .maxDuration(courtScheduleEntity.getMaxDuration())
                .totalBooked(courtScheduleEntity.getTotalBooked())
                .availableDuration(courtScheduleEntity.getAvailableDuration())
                .availableSlots(courtScheduleEntity.getAvailableSlots())
                .maxSlots(courtScheduleEntity.getMaxSlots())
                .businessType(courtScheduleEntity.getBusinessType())
                .courtHouseId(courtScheduleEntity.getCourtHouseId())
                .courtHouseName(courtScheduleEntity.getCourtHouseName())
                .courtRoomId(courtScheduleEntity.getCourtRoomId())
                .courtRoomName(courtScheduleEntity.getCourtRoomName())
                .courtSession(courtScheduleEntity.getCourtSession())
                .sessionDate(courtScheduleEntity.getSessionDate())
                .slotBased(courtScheduleEntity.isSlotBased())
                .active(courtScheduleEntity.isActive())
                .panel(courtScheduleEntity.getPanel())
                .allDaySplit(Boolean.TRUE.equals(isAllDaySplit))
                .createdOn(toOffsetDateTime(courtScheduleEntity.getCreatedOn()))
                .updatedOn(toOffsetDateTime(courtScheduleEntity.getUpdatedOn()))
                .sessionStartTime(toOffsetDateTime(courtScheduleEntity.getSessionStartTime()))
                .sessionEndTime(toOffsetDateTime(courtScheduleEntity.getSessionEndTime()))
                .overbookingAllowed(Boolean.TRUE.equals(courtScheduleEntity.getIsOverbookingAllowed()))
                .draft(Boolean.TRUE.equals(courtScheduleEntity.getIsDraft()))
                .minHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionStartTime()))
                .maxHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionEndTime()));
        if (Boolean.TRUE.equals(isAllDaySplit)) {
            final int maxAdMorningDuration = zeroIfNull(courtScheduleEntity.getMaxAdMorningDuration());
            final int maxAdAfternoonDuration = zeroIfNull(courtScheduleEntity.getMaxAdAfternoonDuration());
            final int totalBookedMorning = zeroIfNull(courtScheduleEntity.getTotalBookedMorning());
            final int totalBookedAfternoon = zeroIfNull(courtScheduleEntity.getTotalBookedAfternoon());
            courtSchedule
                    .maxDurationForMorning(maxAdMorningDuration)
                    .maxDurationForAfternoon(maxAdAfternoonDuration)
                    .totalBookedForMorning(totalBookedMorning)
                    .totalBookedForAfternoon(totalBookedAfternoon)
                    .availableDurationForMorning(maxAdMorningDuration - totalBookedMorning)
                    .availableDurationForAfternoon(maxAdAfternoonDuration - totalBookedAfternoon);
        }

        return courtSchedule;
    }

    public static CourtSchedule convertForOverbooking(
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        return new CourtSchedule()
                .courtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .overbookingAllowed(Boolean.TRUE.equals(courtScheduleEntity.getIsOverbookingAllowed()))
                .active(courtScheduleEntity.isActive());
    }

    public static CourtSchedule convert(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity,
                                                                             final List<AllocatedListingEachBooked> allocatedListingEachBooked) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        final List<AllocatedListingEachBooked> allocatedListings = allocatedListingEachBooked.stream()
                .filter(eachBooked -> eachBooked.getCourtScheduleId().equals(courtScheduleEntity.getCourtScheduleId()))
                .toList();

        final Integer totalBooked = allocatedListings.stream().mapToInt(AllocatedListingEachBooked::getDuration).sum();
        final Boolean isAllDaySplit = courtScheduleEntity.getSupportAdSplit();
        final CourtSchedule courtSchedule = new CourtSchedule()
                .listingProfileId(courtScheduleEntity.getListingProfileId())
                .ouCode(courtScheduleEntity.getOuCode())
                .courtRoomNumber(courtScheduleEntity.getCourtRoomNumber())
                .operationalUnit(courtScheduleEntity.getOperationalUnit())
                .courtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .availableDuration(courtScheduleEntity.getAvailableDuration())
                .maxDuration(courtScheduleEntity.getMaxDuration())
                .availableSlots(courtScheduleEntity.getAvailableSlots())
                .maxSlots(courtScheduleEntity.getMaxSlots())
                .businessType(courtScheduleEntity.getBusinessType())
                .courtHouseId(courtScheduleEntity.getCourtHouseId())
                .courtHouseName(courtScheduleEntity.getCourtHouseName())
                .courtRoomId(courtScheduleEntity.getCourtRoomId())
                .courtRoomName(courtScheduleEntity.getCourtRoomName())
                .courtSession(courtScheduleEntity.getCourtSession())
                .sessionDate(courtScheduleEntity.getSessionDate())
                .slotBased(courtScheduleEntity.isSlotBased())
                .active(courtScheduleEntity.isActive())
                .panel(courtScheduleEntity.getPanel())
                .totalBooked(totalBooked)
                .allDaySplit(Boolean.TRUE.equals(isAllDaySplit))
                .maxDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration())
                .maxDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration())
                .createdOn(toOffsetDateTime(courtScheduleEntity.getCreatedOn()))
                .updatedOn(toOffsetDateTime(courtScheduleEntity.getUpdatedOn()))
                .sessionStartTime(toOffsetDateTime(courtScheduleEntity.getSessionStartTime()))
                .sessionEndTime(toOffsetDateTime(courtScheduleEntity.getSessionEndTime()))
                .overbookingAllowed(Boolean.TRUE.equals(courtScheduleEntity.getIsOverbookingAllowed()))
                .draft(Boolean.TRUE.equals(courtScheduleEntity.getIsDraft()))
                .minHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionStartTime()))
                .maxHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionEndTime()))
                .jurisdiction(courtScheduleEntity.getJurisdiction());

        final OffsetDateTime minHearingTime = allocatedListings.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
        final OffsetDateTime maxHearingTime = allocatedListings.stream()
                .max(comparing(AllocatedListingEachBooked::getHearingStartTime))
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .orElse(null);
        if (minHearingTime != null) {
            courtSchedule.minHearingTime(simpleDateFormat.format(Date.from(minHearingTime.toInstant())));
        }
        if (maxHearingTime != null) {
            courtSchedule.maxHearingTime(simpleDateFormat.format(Date.from(maxHearingTime.toInstant())));
        }

        if (Boolean.TRUE.equals(courtScheduleEntity.getSupportAdSplit()) && ALL_DAY.equals(courtScheduleEntity.getCourtSession())) {
            final AtomicInteger totalBookedForMorning = new AtomicInteger(0);
            final AtomicInteger totalBookedForAfternoon = new AtomicInteger(0);
            final OffsetDateTime sessionStartTime = toOffsetDateTime(courtScheduleEntity.getSessionStartTime());
            final OffsetDateTime afternoonStartTime = toOffsetDateTime(combineDateAndTime(courtScheduleEntity.getSessionDate(), DEFAULT_AFTERNOON_START_TIME));
            allocatedListings
                    .forEach(eachBooked -> {
                        if ((eachBooked.getHearingStartTime().isAfter(sessionStartTime) || eachBooked.getHearingStartTime().isEqual(sessionStartTime))
                                && eachBooked.getHearingStartTime().isBefore(afternoonStartTime)
                        ) {
                            updateTotalBooked(eachBooked.getDuration(), totalBookedForMorning, totalBookedForAfternoon, DEFAULT_DURATION);
                        } else {
                            totalBookedForAfternoon.set(totalBookedForAfternoon.get() + eachBooked.getDuration());
                        }
                    });
            courtSchedule
                    .totalBookedForMorning(totalBookedForMorning.get())
                    .totalBookedForAfternoon(totalBookedForAfternoon.get())
                    .availableDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration() - totalBookedForMorning.get())
                    .availableDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration() - totalBookedForAfternoon.get());
        }
        return courtSchedule;
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
