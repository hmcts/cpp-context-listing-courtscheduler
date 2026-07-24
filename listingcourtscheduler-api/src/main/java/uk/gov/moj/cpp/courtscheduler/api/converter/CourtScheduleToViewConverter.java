package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.sessionTimeFormatter;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleView;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSessionsView;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
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
                    .withMinHearingTime(courtSchedule.getMinHearingTime())
                    .withMaxHearingTime(courtSchedule.getMaxHearingTime())
                    .withSessionStartTime(sessionTimeFormatter(courtSchedule.getSessionStartTime()))
                    .withSessionEndTime(sessionTimeFormatter(courtSchedule.getSessionEndTime()))
                    .withIsOverbookingAllowed(courtSchedule.isOverbookingAllowed())
                    .withIsDraft(courtSchedule.isDraft())
                    .withJurisdictionType(courtSchedule.getJurisdiction())
                    .withJudiciaries(courtSchedule.getJudiciaries())
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
        // Draft sessions have their courtRoomId stripped (CourtScheduleRoomSanitiser), so the
        // grouping key can be null — they collapse into a single no-room bucket sorted first.
        return courtSessionsViews.keySet().stream()
                .sorted(Comparator.nullsFirst(Comparator.naturalOrder()))
                .map(key -> {
            courtSessionsViews.get(key).getSessions().sort(Comparator.comparing(CourtScheduleView::getSessionDate));
            return courtSessionsViews.get(key);
        }).toList();
    }
}
