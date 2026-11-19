package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingUpdateRequest;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeekCommencingEditSessionServiceTest {

    private static final String COURT_SCHEDULE_ID = randomUUID().toString();
    private static final String COURT_ROOM_ID = randomUUID().toString();
    private static final String BUSINESS_TYPE = "TRL";
    private static final int CAPACITY_IN_MINUTES = 1800;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    private WeekCommencingEditSessionService service;

    @BeforeEach
    void setUp() {
        service = new WeekCommencingEditSessionService(courtScheduleRepository);
    }

    @Test
    void shouldBuildUpdateCourtScheduleFromRequestAndPersistedSession() {
        final CourtSchedule persisted = persistedSession(COURT_ROOM_ID, BUSINESS_TYPE, "AD", "ADULT",
                "CROWN", true, null, null);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(COURT_SCHEDULE_ID)).thenReturn(persisted);

        final UpdateCourtSchedule result = service.enrichUpdateSessionRequest(
                new WeekCommencingUpdateRequest()
                        .courtScheduleId(COURT_SCHEDULE_ID)
                        .businessType(BUSINESS_TYPE)
                        .capacityInMinutes(CAPACITY_IN_MINUTES)
                        .isOverbookingAllowed(true));

        assertThat(result.getCourtScheduleId(), is(COURT_SCHEDULE_ID));
        assertThat(result.getCourtRoomId(), is(COURT_ROOM_ID));
        assertThat(result.getCourtSession(), is("AD"));
        assertThat(result.getBusinessType(), is(BUSINESS_TYPE));
        assertThat(result.getPanel(), is("ADULT"));
        assertThat(result.getMaxDuration(), is(CAPACITY_IN_MINUTES));
        assertThat(result.getMaxSlots(), is(0));
        assertThat(result.getAllDaySplit(), is(false));
        assertThat(result.getMaxDurationForMorning(), is(0));
        assertThat(result.getMaxDurationForAfternoon(), is(0));
        assertThat(result.getIsOverbookingAllowed(), is(true));
        assertThat(result.getJurisdiction(), is("CROWN"));
        assertThat(result.getIsDraft(), is(true));
    }

    @Test
    void businessTypeShouldComeFromRequestNotPersistedEntity() {
        final CourtSchedule persisted = persistedSession(COURT_ROOM_ID, "TRL", "AD", "ADULT",
                "CROWN", true, null, null);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(COURT_SCHEDULE_ID)).thenReturn(persisted);

        final UpdateCourtSchedule result = service.enrichUpdateSessionRequest(
                new WeekCommencingUpdateRequest()
                        .courtScheduleId(COURT_SCHEDULE_ID)
                        .businessType("FWT")
                        .capacityInMinutes(CAPACITY_IN_MINUTES)
                        .isOverbookingAllowed(false));

        assertThat(result.getBusinessType(), is("FWT"));
    }

    @Test
    void shouldFormatSessionStartAndEndTimesAsHhMmUtc() {
        final Instant startTime = Instant.parse("2027-01-04T10:00:00Z");
        final Instant endTime = Instant.parse("2027-01-04T17:00:00Z");
        final CourtSchedule persisted = persistedSession(COURT_ROOM_ID, BUSINESS_TYPE, "AD", "ADULT",
                "CROWN", true, startTime, endTime);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(COURT_SCHEDULE_ID)).thenReturn(persisted);

        final UpdateCourtSchedule result = service.enrichUpdateSessionRequest(
                new WeekCommencingUpdateRequest()
                        .courtScheduleId(COURT_SCHEDULE_ID)
                        .businessType(BUSINESS_TYPE)
                        .capacityInMinutes(CAPACITY_IN_MINUTES)
                        .isOverbookingAllowed(false));

        assertThat(result.getSessionStartTime(), is("10:00"));
        assertThat(result.getSessionEndTime(), is("17:00"));
    }

    @Test
    void shouldSetSessionTimesToNullWhenPersistedTimesAreNull() {
        final CourtSchedule persisted = persistedSession(COURT_ROOM_ID, BUSINESS_TYPE, "AD", "ADULT",
                "CROWN", true, null, null);
        when(courtScheduleRepository.retrieveCourtScheduleWithListingById(COURT_SCHEDULE_ID)).thenReturn(persisted);

        final UpdateCourtSchedule result = service.enrichUpdateSessionRequest(
                new WeekCommencingUpdateRequest()
                        .courtScheduleId(COURT_SCHEDULE_ID)
                        .businessType(BUSINESS_TYPE)
                        .capacityInMinutes(CAPACITY_IN_MINUTES)
                        .isOverbookingAllowed(false));

        assertThat(result.getSessionStartTime(), is(nullValue()));
        assertThat(result.getSessionEndTime(), is(nullValue()));
    }

    private static CourtSchedule persistedSession(final String courtRoomId, final String businessType,
                                                   final String courtSession, final String panel,
                                                   final String jurisdiction, final boolean isDraft,
                                                   final Instant sessionStartTime, final Instant sessionEndTime) {
        final CourtSchedule cs = new CourtSchedule();
        cs.setCourtRoomId(courtRoomId);
        cs.setBusinessType(businessType);
        cs.setCourtSession(courtSession);
        cs.setPanel(panel);
        cs.setJurisdiction(jurisdiction);
        cs.setIsDraft(isDraft);
        cs.setSessionStartTime(sessionStartTime);
        cs.setSessionEndTime(sessionEndTime);
        cs.setSessionDate(LocalDate.of(2027, 1, 4));
        return cs;
    }
}
