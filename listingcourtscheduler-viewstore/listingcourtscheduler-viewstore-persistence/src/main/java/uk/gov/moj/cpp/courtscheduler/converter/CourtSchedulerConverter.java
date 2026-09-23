package uk.gov.moj.cpp.courtscheduler.converter;

import static java.util.Comparator.comparing;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.BookingUtils.updateTotalBooked;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

    public static uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule convert(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        final Boolean isAllDaySplit = courtScheduleEntity.getSupportAdSplit();
        final CourtSchedule courtScheduleBuilder = new CourtSchedule()
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
                .overbookingAllowed(courtScheduleEntity.getIsOverbookingAllowed())
                .draft(courtScheduleEntity.getIsDraft())
                .minHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionStartTime()))
                .maxHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionEndTime()));
        if (Boolean.TRUE.equals(isAllDaySplit)) {
            courtScheduleBuilder
                    .maxDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration())
                    .maxDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration())
                    .totalBookedForMorning(courtScheduleEntity.getTotalBookedMorning())
                    .totalBookedForAfternoon(courtScheduleEntity.getTotalBookedAfternoon())
                    .availableDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration() - courtScheduleEntity.getTotalBookedMorning())
                    .availableDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration() - courtScheduleEntity.getTotalBookedAfternoon());
        }

        return courtScheduleBuilder;
    }

    public static uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule convertForOverbooking(
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity) {
        return new uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule()
                .courtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .overbookingAllowed(Boolean.TRUE.equals(courtScheduleEntity.getIsOverbookingAllowed()))
                .active(courtScheduleEntity.isActive());
    }

    public static uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule convert(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity,
                                                                             final List<AllocatedListingEachBooked> allocatedListingEachBooked) {
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm");
        final List<AllocatedListingEachBooked> allocatedListings = allocatedListingEachBooked.stream()
                .filter(eachBooked -> eachBooked.getCourtScheduleId().equals(courtScheduleEntity.getCourtScheduleId()))
                .toList();

        final Integer totalBooked = allocatedListings.stream().mapToInt(AllocatedListingEachBooked::getDuration).sum();
        final Boolean isAllDaySplit = courtScheduleEntity.getSupportAdSplit();
        final CourtSchedule courtScheduleBuilder = new uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule()
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
                .overbookingAllowed(courtScheduleEntity.getIsOverbookingAllowed())
                .draft(courtScheduleEntity.getIsDraft())
                .minHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionStartTime()))
                .maxHearingTime(simpleDateFormat.format(courtScheduleEntity.getSessionEndTime()))
                .jurisdiction(courtScheduleEntity.getJurisdiction());

        final Date minHearingTime = allocatedListings.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .map(CourtSchedulerConverter::toDate)
                .min(Date::compareTo)
                .orElse(null);
        final Date maxHearingTime = allocatedListings.stream()
                .max(comparing(AllocatedListingEachBooked::getHearingStartTime))
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .map(CourtSchedulerConverter::toDate)
                .orElse(null);
        if (minHearingTime != null) {
            courtScheduleBuilder.minHearingTime(simpleDateFormat.format(minHearingTime));
        }
        if (maxHearingTime != null) {
            courtScheduleBuilder.maxHearingTime(simpleDateFormat.format(maxHearingTime));
        }

        if (Boolean.TRUE.equals(courtScheduleEntity.getSupportAdSplit()) && ALL_DAY.equals(courtScheduleEntity.getCourtSession())) {
            final AtomicInteger totalBookedForMorning = new AtomicInteger(0);
            final AtomicInteger totalBookedForAfternoon = new AtomicInteger(0);
            allocatedListings
                    .forEach(eachBooked -> {
                        final Date hearingStartTime = toDate(eachBooked.getHearingStartTime());
                        if ((hearingStartTime.after(courtScheduleEntity.getSessionStartTime()) || hearingStartTime.equals(courtScheduleEntity.getSessionStartTime()))
                                && hearingStartTime.before(combineDateAndTime(courtScheduleEntity.getSessionDate(), DEFAULT_AFTERNOON_START_TIME))
                        ) {
                            updateTotalBooked(eachBooked.getDuration(), totalBookedForMorning, totalBookedForAfternoon, DEFAULT_DURATION);
                        } else {
                            totalBookedForAfternoon.set(totalBookedForAfternoon.get() + eachBooked.getDuration());
                        }
                    });
            courtScheduleBuilder
                    .totalBookedForMorning(totalBookedForMorning.get())
                    .totalBookedForAfternoon(totalBookedForAfternoon.get())
                    .availableDurationForMorning(courtScheduleEntity.getMaxAdMorningDuration() - totalBookedForMorning.get())
                    .availableDurationForAfternoon(courtScheduleEntity.getMaxAdAfternoonDuration() - totalBookedForAfternoon.get());
        }
        return courtScheduleBuilder;
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

    private static OffsetDateTime toOffsetDateTime(final Date date) {
        return date == null ? null : date.toInstant().atOffset(ZoneOffset.UTC);
    }

    private static Date toDate(final OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : Date.from(offsetDateTime.toInstant());
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
