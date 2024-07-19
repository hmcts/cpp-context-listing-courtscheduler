package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleView;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSessionsView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CourtScheduleToViewConverter {

    public static List<CourtSessionsView> getCourtSessionsViews(List<CourtSchedule> courtSchedules) {
        Map<String, CourtSessionsView> courtSessionsViews = new HashMap<>();
        courtSchedules.forEach(courtSchedule -> {
            String courtRoomId = courtSchedule.getCourtRoomId();
            CourtScheduleView courtScheduleView = new CourtScheduleView.CourtScheduleViewBuilder()
                    .withCourtScheduleId(courtSchedule.getCourtScheduleId())
                    .withActive(courtSchedule.isActive())
                    .withHasHearingsBooked(courtSchedule.hasHearingsBooked())
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
                    .build();
            CourtSessionsView courtSessionsView;
            if (courtSessionsViews.containsKey(courtRoomId)) {
                courtSessionsView = courtSessionsViews.get(courtRoomId);
            } else {
                courtSessionsView = new CourtSessionsView(courtRoomId, courtSchedule.getCourtRoomName());
            }
            courtSessionsView.addSession(courtScheduleView);
            courtSessionsViews.put(courtRoomId, courtSessionsView);

        });
        return courtSessionsViews.values().stream().toList();
    }
}
