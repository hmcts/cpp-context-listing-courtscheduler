package uk.gov.moj.cpp.courtscheduler.common.converter;

import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.sessionTimeFormatter;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleDeleteResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.SlotStartTime;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CourtScheduleToDeleteResponseConverter implements Converter<List<CourtSchedule>, List<CourtScheduleDeleteResponse>> {
    @Override
    public List<CourtScheduleDeleteResponse> convert(List<CourtSchedule> courtSchedules) {
        List<CourtScheduleDeleteResponse> courtScheduleDeleteResponses = new ArrayList<>();
        courtSchedules.forEach(courtSchedule -> {
            CourtScheduleDeleteResponse courtScheduleView = new CourtScheduleDeleteResponse()
                    .courtScheduleId(courtSchedule.getCourtScheduleId())
                    .active(courtSchedule.getActive())
                    .slotBased(courtSchedule.getSlotBased())
                    .availableDuration(courtSchedule.getAvailableDuration())
                    .availableSlots(courtSchedule.getAvailableSlots())
                    .businessType(courtSchedule.getBusinessType())
                    .businessDescription(courtSchedule.getBusinessDescription())
                    .courtHouseId(courtSchedule.getCourtHouseId())
                    .courtHouseName(courtSchedule.getCourtHouseName())
                    .courtRoomNumber(courtSchedule.getCourtRoomNumber())
                    .courtRoomId(courtSchedule.getCourtRoomId())
                    .courtRoomName(courtSchedule.getCourtRoomName())
                    .courtSession(courtSchedule.getCourtSession())
                    .listingProfileId(courtSchedule.getListingProfileId())
                    .maxDuration(courtSchedule.getMaxDuration())
                    .maxSlots(courtSchedule.getMaxSlots())
                    .operationalUnit(courtSchedule.getOperationalUnit())
                    .ouCode(courtSchedule.getOuCode())
                    .panel(courtSchedule.getPanel())
                    .sessionDate(courtSchedule.getSessionDate())
                    .totalBooked(courtSchedule.getTotalBooked());
            courtScheduleDeleteResponses.add(courtScheduleView);
        });
        return courtScheduleDeleteResponses;
    }

    public static List<CourtScheduleDeleteResponse> convert(List<CourtSchedule> courtSchedules, final List<AllocatedListingEachBooked> allocatedListingEachBooked) {
        List<CourtScheduleDeleteResponse> courtScheduleDeleteResponses = new ArrayList<>();
        courtSchedules.forEach(courtSchedule -> {
            final List<AllocatedListingEachBooked> allocatedListingEachBookedForThisSchedule = allocatedListingEachBooked.stream()
                    .filter(eachBooked -> eachBooked.getCourtScheduleId().equals(courtSchedule.getCourtScheduleId()))
                    .toList();
            final int totalBooked = allocatedListingEachBookedForThisSchedule.stream()
                    .mapToInt(AllocatedListingEachBooked::getDuration)
                    .sum();
            final List<SlotStartTime> slotStartTimes = courtSchedule.getSlotStartTimes().stream()
                    .map(slotStartTime -> new SlotStartTime()
                            .sessionStartTime(slotStartTime.getSessionStartTime())
                            .sessionEndTime(slotStartTime.getSessionEndTime())
                            .hearingStartTime(slotStartTime.getHearingStartTime())
                            .count(slotStartTime.getCount()))
                    .toList();
            CourtScheduleDeleteResponse courtScheduleView = new CourtScheduleDeleteResponse()
                    .courtScheduleId(courtSchedule.getCourtScheduleId())
                    .active(courtSchedule.getActive())
                    .slotBased(courtSchedule.getSlotBased())
                    .availableDuration(courtSchedule.getAvailableDuration())
                    .availableSlots(courtSchedule.getAvailableSlots())
                    .businessType(courtSchedule.getBusinessType())
                    .businessDescription(courtSchedule.getBusinessDescription())
                    .courtHouseId(courtSchedule.getCourtHouseId())
                    .courtHouseName(courtSchedule.getCourtHouseName())
                    .courtRoomNumber(courtSchedule.getCourtRoomNumber())
                    .courtRoomId(courtSchedule.getCourtRoomId())
                    .courtRoomName(courtSchedule.getCourtRoomName())
                    .courtSession(courtSchedule.getCourtSession())
                    .listingProfileId(courtSchedule.getListingProfileId())
                    .maxDuration(courtSchedule.getMaxDuration())
                    .maxSlots(courtSchedule.getMaxSlots())
                    .operationalUnit(courtSchedule.getOperationalUnit())
                    .ouCode(courtSchedule.getOuCode())
                    .panel(courtSchedule.getPanel())
                    .sessionDate(courtSchedule.getSessionDate())
                    .totalBooked(totalBooked)
                    .slotStartTimes(slotStartTimes)
                    .minHearingTime(courtSchedule.getMinHearingTime())
                    .maxHearingTime(courtSchedule.getMaxHearingTime())
                    .sessionStartTime(courtSchedule.getSessionStartTime() == null ? null
                            : sessionTimeFormatter(Date.from(courtSchedule.getSessionStartTime().toInstant())))
                    .sessionEndTime(courtSchedule.getSessionEndTime() == null ? null
                            : sessionTimeFormatter(Date.from(courtSchedule.getSessionEndTime().toInstant())))
                    .isOverbookingAllowed(courtSchedule.getOverbookingAllowed());
            courtScheduleDeleteResponses.add(courtScheduleView);
        });
        return courtScheduleDeleteResponses;
    }
}