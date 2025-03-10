package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleView;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSessionsView;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CourtScheduleToViewConverter {

    public static List<CourtSessionsView> getCourtSessionsViews(List<CourtSchedule> courtSchedules) {
        Map<String, CourtSessionsView> courtSessionsViews = new HashMap<>();
        courtSchedules.forEach(courtSchedule -> {
            final String courtRoomName = courtSchedule.getCourtRoomName();
            final String courtRoomId = courtSchedule.getCourtRoomId();
            final CourtScheduleView courtScheduleView = new CourtScheduleView.CourtScheduleViewBuilder()
                    .withCourtScheduleId(courtSchedule.getCourtScheduleId())
                    .withActive(courtSchedule.isActive())
                    .withTotalBooked(courtSchedule.getTotalBooked())
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
                    .withAllDaySplit(courtSchedule.isAllDaySplit())
                    .withMaxDurationForMorning(courtSchedule.getMaxDurationForMorning())
                    .withMaxDurationForAfternoon(courtSchedule.getMaxDurationForAfternoon())
                    .withTotalBookedForMorning(courtSchedule.getTotalBookedForMorning())
                    .withTotalBookedForAfternoon(courtSchedule.getTotalBookedForAfternoon())
                    .withAvailableDurationForMorning(courtSchedule.getAvailableDurationForMorning())
                    .withAvailableDurationForAfternoon(courtSchedule.getAvailableDurationForAfternoon())
                    .build();
            CourtSessionsView courtSessionsView;
            if (courtSessionsViews.containsKey(courtRoomName)) {
                courtSessionsView = courtSessionsViews.get(courtRoomName);
            } else {
                courtSessionsView = new CourtSessionsView(courtRoomId, courtSchedule.getCourtRoomName());
            }
            courtSessionsView.addSession(courtScheduleView);
            courtSessionsViews.put(courtRoomName, courtSessionsView);

        });
        return courtSessionsViews.keySet().stream().sorted().map(key -> {
            courtSessionsViews.get(key).getSessions().sort(Comparator.comparing(CourtScheduleView::getSessionDate));
            return courtSessionsViews.get(key);
        }).toList();
    }
}
