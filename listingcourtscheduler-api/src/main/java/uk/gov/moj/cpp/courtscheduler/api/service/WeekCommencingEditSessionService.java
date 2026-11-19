package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.Boolean.TRUE;

import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingUpdateRequest;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

@Service
public class WeekCommencingEditSessionService {

    private static final DateTimeFormatter UTC_HH_MM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    private final CourtScheduleRepository courtScheduleRepository;

    public WeekCommencingEditSessionService(final CourtScheduleRepository courtScheduleRepository) {
        this.courtScheduleRepository = courtScheduleRepository;
    }

    /**
     * Builds the full UpdateCourtSchedule from the simplified week commencing edit request,
     * filling in immutable fields (courtroom, session type, business type, panel, jurisdiction,
     * start/end times) from the persisted session. Called after pre-validation confirms the
     * session exists and is a week commencing session.
     */
    public UpdateCourtSchedule enrichUpdateSessionRequest(final WeekCommencingUpdateRequest request) {
        final CourtSchedule persisted = courtScheduleRepository.retrieveCourtScheduleWithListingById(
                request.getCourtScheduleId());

        return new UpdateCourtSchedule()
                .courtScheduleId(request.getCourtScheduleId())
                .courtRoomId(persisted.getCourtRoomId())
                .courtSession(persisted.getCourtSession())
                .businessType(persisted.getBusinessType())
                .panel(persisted.getPanel())
                .maxDuration(request.getCapacityInMinutes())
                .maxSlots(0)
                .allDaySplit(false)
                .maxDurationForMorning(0)
                .maxDurationForAfternoon(0)
                .sessionStartTime(persisted.getSessionStartTime() != null
                        ? UTC_HH_MM.format(persisted.getSessionStartTime()) : null)
                .sessionEndTime(persisted.getSessionEndTime() != null
                        ? UTC_HH_MM.format(persisted.getSessionEndTime()) : null)
                .isOverbookingAllowed(TRUE.equals(request.getIsOverbookingAllowed()))
                .jurisdiction(persisted.getJurisdiction())
                .isDraft(persisted.isDraft());
    }
}
