package uk.gov.moj.cpp.courtscheduler.common.converter;

import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.sessionTimeFormatter;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleDeleteResponseItem;

import java.util.ArrayList;
import java.util.List;

public class CourtScheduleToDeleteResponseConverter implements Converter<List<CourtSchedule>, List<CourtScheduleDeleteResponseItem>> {
    @Override
    public List<CourtScheduleDeleteResponseItem> convert(List<CourtSchedule> courtSchedules) {
        List<CourtScheduleDeleteResponseItem> courtScheduleDeleteResponses = new ArrayList<>();
        courtSchedules.forEach(courtSchedule -> {
            CourtScheduleDeleteResponseItem courtScheduleView = new CourtScheduleDeleteResponseItem()
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

    public static List<CourtScheduleDeleteResponseItem> convert(List<CourtSchedule> courtSchedules, final List<AllocatedListingEachBooked> allocatedListingEachBooked) {
        List<CourtScheduleDeleteResponseItem> courtScheduleDeleteResponses = new ArrayList<>();
        courtSchedules.forEach(courtSchedule -> {
            final List<AllocatedListingEachBooked> allocatedListingEachBookedForThisSchedule = allocatedListingEachBooked.stream()
                    .filter(eachBooked -> eachBooked.getCourtScheduleId().equals(courtSchedule.getCourtScheduleId()))
                    .toList();
            final int totalBooked = allocatedListingEachBookedForThisSchedule.stream()
                    .mapToInt(AllocatedListingEachBooked::getDuration)
                    .sum();
            CourtScheduleDeleteResponseItem courtScheduleView = new CourtScheduleDeleteResponseItem()
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
                    .slotStartTimes(courtSchedule.getSlotStartTimes())
                    .minHearingTime(courtSchedule.getMinHearingTime())
                    .maxHearingTime(courtSchedule.getMaxHearingTime())
                    .sessionStartTime(sessionTimeFormatter(java.util.Date.from(courtSchedule.getSessionStartTime().toInstant())))
                    .sessionEndTime(sessionTimeFormatter(java.util.Date.from(courtSchedule.getSessionEndTime().toInstant())))
                    .isOverbookingAllowed(courtSchedule.getOverbookingAllowed());
            courtScheduleDeleteResponses.add(courtScheduleView);
        });
        return courtScheduleDeleteResponses;
    }
}