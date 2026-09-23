package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

@Service
public class CourtScheduleToForecastCourtScheduleConverter {

    public CourtSchedule convertToProvisionalCourtSchedule(final CourtSchedule courtSchedule,
                                                           final LocalDate sessionDate,
                                                           final String courtScheduleId) {
        return copyOf(courtSchedule)
                .listingProfileId(null)
                .courtScheduleId(courtScheduleId)
                .sessionDate(sessionDate)
                .judiciaries(null);
    }

    /**
     * Manual field-by-field copy replacing the old hand-written {@code CourtScheduleBuilder.withCourtSchedule(...)}
     * copy-constructor, which no longer exists on the OpenAPI-generated {@link CourtSchedule} model.
     */
    private static CourtSchedule copyOf(final CourtSchedule source) {
        return new CourtSchedule()
                .courtScheduleId(source.getCourtScheduleId())
                .sessionDate(source.getSessionDate())
                .ouCode(source.getOuCode())
                .courtHouseName(source.getCourtHouseName())
                .courtHouseId(source.getCourtHouseId())
                .courtRoomId(source.getCourtRoomId())
                .courtRoomNumber(source.getCourtRoomNumber())
                .courtRoomName(source.getCourtRoomName())
                .businessType(source.getBusinessType())
                .courtSession(source.getCourtSession())
                .slotBased(source.getSlotBased())
                .maxSlots(source.getMaxSlots())
                .maxDuration(source.getMaxDuration())
                .listingProfileId(source.getListingProfileId())
                .operationalUnit(source.getOperationalUnit())
                .panel(source.getPanel())
                .availableDuration(source.getAvailableDuration())
                .availableSlots(source.getAvailableSlots())
                .judiciaries(source.getJudiciaries())
                .slotStartTimes(source.getSlotStartTimes())
                .active(source.getActive())
                .createdOn(source.getCreatedOn())
                .updatedOn(source.getUpdatedOn())
                .allDaySplit(source.getAllDaySplit())
                .maxDurationForMorning(source.getMaxDurationForMorning())
                .maxDurationForAfternoon(source.getMaxDurationForAfternoon())
                .totalBooked(source.getTotalBooked())
                .sessionStartTime(source.getSessionStartTime())
                .sessionEndTime(source.getSessionEndTime())
                .totalBookedForMorning(source.getTotalBookedForMorning())
                .totalBookedForAfternoon(source.getTotalBookedForAfternoon())
                .availableDurationForMorning(source.getAvailableDurationForMorning())
                .availableDurationForAfternoon(source.getAvailableDurationForAfternoon())
                .overbookingAllowed(source.getOverbookingAllowed())
                .nationalBreakTime(source.getNationalBreakTime())
                .draft(source.getDraft())
                .minHearingTime(source.getMinHearingTime())
                .maxHearingTime(source.getMaxHearingTime())
                .jurisdiction(source.getJurisdiction());
    }
}
