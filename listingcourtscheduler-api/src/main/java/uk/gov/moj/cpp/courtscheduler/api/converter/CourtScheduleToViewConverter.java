package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.sessionTimeFormatter;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleView;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSessionsView;

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
            final CourtScheduleView courtScheduleView = new CourtScheduleView()
                    .courtScheduleId(courtSchedule.getCourtScheduleId())
                    .active(courtSchedule.getActive())
                    .totalBooked(courtSchedule.getTotalBooked())
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
                    .allDaySplit(courtSchedule.getAllDaySplit())
                    .maxDurationForMorning(courtSchedule.getMaxDurationForMorning())
                    .maxDurationForAfternoon(courtSchedule.getMaxDurationForAfternoon())
                    .totalBookedForMorning(courtSchedule.getTotalBookedForMorning())
                    .totalBookedForAfternoon(courtSchedule.getTotalBookedForAfternoon())
                    .availableDurationForMorning(courtSchedule.getAvailableDurationForMorning())
                    .availableDurationForAfternoon(courtSchedule.getAvailableDurationForAfternoon())
                    .minHearingTime(courtSchedule.getMinHearingTime())
                    .maxHearingTime(courtSchedule.getMaxHearingTime())
                    .sessionStartTime(courtSchedule.getSessionStartTime() == null ? null
                            : sessionTimeFormatter(java.util.Date.from(courtSchedule.getSessionStartTime().toInstant())))
                    .sessionEndTime(courtSchedule.getSessionEndTime() == null ? null
                            : sessionTimeFormatter(java.util.Date.from(courtSchedule.getSessionEndTime().toInstant())))
                    .isOverbookingAllowed(courtSchedule.getOverbookingAllowed())
                    .isDraft(courtSchedule.getDraft())
                    .jurisdiction(courtSchedule.getJurisdiction())
                    .judiciaries(courtSchedule.getJudiciaries());
            CourtSessionsView courtSessionsView;
            if (courtSessionsViews.containsKey(courtRoomName)) {
                courtSessionsView = courtSessionsViews.get(courtRoomName);
            } else {
                courtSessionsView = new CourtSessionsView()
                        .courtRoomId(courtRoomId)
                        .courtRoomName(courtSchedule.getCourtRoomName());
            }
            courtSessionsView.addSessionsItem(courtScheduleView);
            courtSessionsViews.put(courtRoomName, courtSessionsView);

        });
        return courtSessionsViews.keySet().stream().sorted().map(key -> {
            courtSessionsViews.get(key).getSessions().sort(Comparator.comparing(CourtScheduleView::getSessionDate));
            return courtSessionsViews.get(key);
        }).toList();
    }
}
