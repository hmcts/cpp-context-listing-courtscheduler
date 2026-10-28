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

    public static List<CourtSessionsView> getCourtSessionsViews(final List<CourtSchedule> courtSchedules) {
        final Map<String, CourtSessionsView> courtSessionsViews = new HashMap<>();
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
                            : sessionTimeFormatter(courtSchedule.getSessionStartTime().toInstant()))
                    .sessionEndTime(courtSchedule.getSessionEndTime() == null ? null
                            : sessionTimeFormatter(courtSchedule.getSessionEndTime().toInstant()))
                    .isOverbookingAllowed(courtSchedule.getOverbookingAllowed())
                    .isDraft(courtSchedule.getDraft())
                    .jurisdiction(courtSchedule.getJurisdiction())
                    .judiciaries(courtSchedule.getJudiciaries());
            final CourtSessionsView courtSessionsView;
            if (courtSessionsViews.containsKey(courtRoomId)) {
                courtSessionsView = courtSessionsViews.get(courtRoomId);
            } else {
                courtSessionsView = new CourtSessionsView()
                        .courtRoomId(courtRoomId)
                        .courtRoomName(courtRoomName);
            }
            courtSessionsView.addSessionsItem(courtScheduleView);
            courtSessionsViews.put(courtRoomId, courtSessionsView);

        });
        // Draft sessions have their courtRoomId stripped (CourtScheduleRoomSanitiser), so the
        // grouping key can be null — they collapse into a single no-room bucket sorted first.
        return courtSessionsViews.values().stream()
                .sorted(Comparator.nullsFirst(Comparator.comparing(
                        CourtSessionsView::getCourtRoomName, Comparator.nullsFirst(Comparator.naturalOrder()))))
                .map(view -> {
            view.getSessions().sort(Comparator.comparing(CourtScheduleView::getSessionDate));
            return view;
        }).toList();
    }
}
