package uk.gov.moj.cpp.courtscheduler.common.converter;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleDeleteResponse;

import java.util.ArrayList;
import java.util.List;

public class CourtScheduleToDeleteResponseConverter implements Converter<List<CourtSchedule>, List<CourtScheduleDeleteResponse>> {
    @Override
    public List<CourtScheduleDeleteResponse> convert(List<CourtSchedule> courtSchedules) {
        List<CourtScheduleDeleteResponse> courtScheduleDeleteResponses = new ArrayList<>();
        courtSchedules.forEach(courtSchedule -> {
            CourtScheduleDeleteResponse courtScheduleView = new CourtScheduleDeleteResponse.CourtScheduleDeleteResponseBuilder()
                    .withCourtScheduleId(courtSchedule.getCourtScheduleId())
                    .withActive(courtSchedule.isActive())
                    .withSlotBased(courtSchedule.isSlotBased())
                    .withAvailableDuration(courtSchedule.getAvailableDuration())
                    .withAvailableSlots(courtSchedule.getAvailableSlots())
                    .withBusinessType(courtSchedule.getBusinessType())
                    .withBusinessDescription(courtSchedule.getBusinessDescription())
                    .withCourtHouseId(courtSchedule.getCourtHouseId())
                    .withCourtHouseName(courtSchedule.getCourtHouseName())
                    .withCourtRoomNumber(courtSchedule.getCourtRoomNumber())
                    .withCourtRoomId(courtSchedule.getCourtRoomId())
                    .withCourtRoomName(courtSchedule.getCourtRoomName())
                    .withCourtSession(courtSchedule.getCourtSession())
                    .withListingProfileId(courtSchedule.getListingProfileId())
                    .withMaxDuration(courtSchedule.getMaxDuration())
                    .withMaxSlots(courtSchedule.getMaxSlots())
                    .withOperationalUnit(courtSchedule.getOperationalUnit())
                    .withOuCode(courtSchedule.getOuCode())
                    .withPanel(courtSchedule.getPanel())
                    .withSessionDate(courtSchedule.getSessionDate())
                    .withTotalBooked(courtSchedule.getTotalBooked())
                    .build();
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
            CourtScheduleDeleteResponse courtScheduleView = new CourtScheduleDeleteResponse.CourtScheduleDeleteResponseBuilder()
                    .withCourtScheduleId(courtSchedule.getCourtScheduleId())
                    .withActive(courtSchedule.isActive())
                    .withSlotBased(courtSchedule.isSlotBased())
                    .withAvailableDuration(courtSchedule.getAvailableDuration())
                    .withAvailableSlots(courtSchedule.getAvailableSlots())
                    .withBusinessType(courtSchedule.getBusinessType())
                    .withBusinessDescription(courtSchedule.getBusinessDescription())
                    .withCourtHouseId(courtSchedule.getCourtHouseId())
                    .withCourtHouseName(courtSchedule.getCourtHouseName())
                    .withCourtRoomNumber(courtSchedule.getCourtRoomNumber())
                    .withCourtRoomId(courtSchedule.getCourtRoomId())
                    .withCourtRoomName(courtSchedule.getCourtRoomName())
                    .withCourtSession(courtSchedule.getCourtSession())
                    .withListingProfileId(courtSchedule.getListingProfileId())
                    .withMaxDuration(courtSchedule.getMaxDuration())
                    .withMaxSlots(courtSchedule.getMaxSlots())
                    .withOperationalUnit(courtSchedule.getOperationalUnit())
                    .withOuCode(courtSchedule.getOuCode())
                    .withPanel(courtSchedule.getPanel())
                    .withSessionDate(courtSchedule.getSessionDate())
                    .withTotalBooked(totalBooked)
                    .build();
            courtScheduleDeleteResponses.add(courtScheduleView);
        });
        return courtScheduleDeleteResponses;
    }
}