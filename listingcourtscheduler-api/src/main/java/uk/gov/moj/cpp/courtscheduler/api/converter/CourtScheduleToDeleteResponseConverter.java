package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleDeleteResponse;

import java.util.ArrayList;
import java.util.List;

@Service
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
                    .sessionDate(courtSchedule.getSessionDate());
            courtScheduleDeleteResponses.add(courtScheduleView);

        });
        return courtScheduleDeleteResponses;
    }
}
