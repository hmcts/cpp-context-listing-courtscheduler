package uk.gov.moj.cpp.courtscheduler.api;

import uk.gov.moj.cpp.courtscheduler.api.domain.CourtScheduleView;
import uk.gov.moj.cpp.courtscheduler.api.domain.CourtSessionsView;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;

public class CourtScheduleToViewConverter {

    static CourtSessionsView convert(CourtSchedule courtSchedule) {
        return new CourtSessionsView.CourtSessionsViewBuilder()
                .withCourtRoomId(courtSchedule.getCourtRoomId())
                .withCourtRoomName(courtSchedule.getCourtRoomName())
                .withSession(
                        new CourtScheduleView.CourtScheduleViewBuilder()
                                .withCourtScheduleId(courtSchedule.getCourtScheduleId())
                                .withActive(courtSchedule.isActive())
                                .withAvailableDuration(courtSchedule.getAvailableDuration())
                                .withAvailableSlots(courtSchedule.getAvailableSlots())
                                .withBusinessType(courtSchedule.getBusinessType())
                                .withCourtHouseId(courtSchedule.getCourtHouseId())
                                .withCourtHouseName(courtSchedule.getCourtHouseName())
                                .withCourtRoomNumber(courtSchedule.getCourtRoomNumber())
                                .withCourtSession(courtSchedule.getCourtSession())
                                .withListingProfileId(courtSchedule.getListingProfileId())
                                .withMaxDuration(courtSchedule.getMaxDuration())
                                .withMaxSlots(courtSchedule.getMaxSlots())
                                .withOperationalUnit(courtSchedule.getOperationalUnit())
                                .withOuCode(courtSchedule.getOuCode())
                                .withPanel(courtSchedule.getPanel())
                                .withSessionDate(courtSchedule.getSessionDate())
                                .build()).build();
    }
}
