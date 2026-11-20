package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.Boolean.TRUE;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.CROWN;
import static uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency.EVERY_WEEK;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.PanelTypes.ADULT;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingUpdateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class WeekCommencingSessionService {

    private static final String MONDAY = StringUtils.capitalize(DayOfWeek.MONDAY.name().toLowerCase(Locale.ROOT));
    private static final int EVERY_WEEK_REPEAT = 1;
    private static final DateTimeFormatter LONDON_HH_MM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(TimezoneUtils.LONDON_ZONE);

    private final ReferenceDataCache referenceDataCache;
    private final CourtScheduleRepository courtScheduleRepository;

    public WeekCommencingSessionService(final ReferenceDataCache referenceDataCache,
                                        final CourtScheduleRepository courtScheduleRepository) {
        this.referenceDataCache = referenceDataCache;
        this.courtScheduleRepository = courtScheduleRepository;
    }

    /**
     * Converts the request into an ordinary create payload: a CROWN draft all-day session on every
     * Monday of the range, in the court centre's default courtroom. The courtroom is null when the
     * court centre has none; WeekCommencingApiValidator rejects that.
     */
    public CreateSessionRequestParam enrichCreateSessionRequest(final WeekCommencingCreateRequest request) {
        final Session session = new Session()
                .courtCentreId(request.getCourtCentreId())
                .jurisdiction(CROWN.getJurisdiction())
                .courtRoomId(defaultCourtRoomId(request.getCourtCentreId()))
                .sessionType(ALL_DAY)
                .businessType(request.getBusinessType())
                .duration(request.getCapacityInMinutes())
                .allDaySplit(false)
                .maxDurationForMorning(0)
                .maxDurationForAfternoon(0)
                .panel(ADULT.name())
                .repeatDays(List.of(MONDAY))
                .isOverbookingAllowed(TRUE.equals(request.getIsOverbookingAllowed()))
                .isDraft(true)
                .isWeekCommencing(true);

        final RepeatPattern repeatPattern = new RepeatPattern()
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .repeatFor(EVERY_WEEK_REPEAT)
                .frequency(EVERY_WEEK.getRepeatFrequency());

        return new CreateSessionRequestParam()
                .sessions(List.of(session))
                .repeatPattern(repeatPattern);
    }

    /**
     * The first courtroom the reference data lists for the court centre, or null when it has none.
     * Reference data returns a centre's currently valid courtrooms sorted by venue name, then courtroom
     * name (CourtRoom#compareTo in cpp-context-reference-data), so this is normally "Courtroom 01" of
     * the first venue. The ou-courtrooms {@code courtroomId} is a platform-wide id, not a room number
     * within the centre, so it cannot be used to find "courtroom 1".
     */
    /* package */ String defaultCourtRoomId(final String courtCentreId) {
        return referenceDataCache.getCpCourtRoomsByCourtCentreId(courtCentreId).stream()
                .map(CourtRoom::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public CourtSchedule loadPersistedSession(final String courtScheduleId) {
        return courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId);
    }

    public UpdateCourtSchedule enrichUpdateSessionRequest(final WeekCommencingUpdateRequest request,
                                                          final CourtSchedule persisted) {
        return new UpdateCourtSchedule()
                .courtScheduleId(request.getCourtScheduleId())
                .courtRoomId(persisted.getCourtRoomId())
                .courtSession(persisted.getCourtSession())
                .businessType(request.getBusinessType())
                .panel(persisted.getPanel())
                .maxDuration(request.getCapacityInMinutes())
                .maxSlots(0)
                .allDaySplit(false)
                .maxDurationForMorning(0)
                .maxDurationForAfternoon(0)
                .sessionStartTime(persisted.getSessionStartTime() != null
                        ? LONDON_HH_MM.format(persisted.getSessionStartTime()) : null)
                .sessionEndTime(persisted.getSessionEndTime() != null
                        ? LONDON_HH_MM.format(persisted.getSessionEndTime()) : null)
                .isOverbookingAllowed(TRUE.equals(request.getIsOverbookingAllowed()))
                .jurisdiction(persisted.getJurisdiction())
                .isDraft(persisted.isDraft());
    }
}
