package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingUpdateRequest;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeekCommencingSessionServiceTest {

    private static final String COURT_CENTRE_ID = randomUUID().toString();
    private static final String COURT_SCHEDULE_ID = randomUUID().toString();
    private static final String COURT_ROOM_ID = randomUUID().toString();
    private static final String BUSINESS_TYPE = "TRL";
    private static final String ADULT = "ADULT";
    private static final String CROWN = "CROWN";
    private static final int CAPACITY_IN_MINUTES = 1800;
    private static final String ROOM_1 = randomUUID().toString();
    private static final String ROOM_2 = randomUUID().toString();
    private static final String ROOM_3 = randomUUID().toString();

    @Mock
    private ReferenceDataCache referenceDataCache;
    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    private WeekCommencingSessionService service;

    @BeforeEach
    void setUp() {
        service = new WeekCommencingSessionService(referenceDataCache, courtScheduleRepository);
    }

    @Test
    void shouldConvertToACrownDraftAllDayMondaySessionInTheFirstListedCourtRoom() {
        givenCourtRooms(room(ROOM_1, 1001), room(ROOM_2, 1), room(ROOM_3, 3));

        final CreateSessionRequestParam created = service.enrichCreateSessionRequest(request(true));

        assertThat(created.getSessions().size(), is(1));
        final Session session = created.getSessions().getFirst();
        assertThat(session.getCourtCentreId(), is(COURT_CENTRE_ID));
        assertThat(session.getCourtRoomId(), is(ROOM_1));
        assertThat(session.getJurisdiction(), is(CROWN));
        assertThat(session.getSessionType(), is("AD"));
        assertThat(session.getBusinessType(), is(BUSINESS_TYPE));
        assertThat(session.getDuration(), is(1800));
        assertThat(session.getAllDaySplit(), is(false));
        assertThat(session.getPanel(), is(ADULT));
        assertThat(session.getRepeatDays(), contains("Monday"));
        assertThat(session.getIsDraft(), is(true));
        assertThat(session.getIsWeekCommencing(), is(true));
        assertThat(session.getIsOverbookingAllowed(), is(true));
        assertThat(created.getRepeatPattern().getStartDate(), is("2027-01-06"));
        assertThat(created.getRepeatPattern().getEndDate(), is("2027-02-01"));
        assertThat(created.getRepeatPattern().getRepeatFor(), is(1));
        assertThat(created.getRepeatPattern().getFrequency(), is("EVERY_WEEK"));
    }

    @Test
    void shouldDefaultOverbookingToFalseWhenNotSupplied() {
        givenCourtRooms(room(ROOM_1, 1));

        final CreateSessionRequestParam created = service.enrichCreateSessionRequest(request(null));

        assertThat(created.getSessions().getFirst().getIsOverbookingAllowed(), is(false));
    }

    @Test
    void shouldLeaveTheCourtRoomEmptyWhenTheCourtCentreHasNone() {
        givenCourtRooms();

        final CreateSessionRequestParam created = service.enrichCreateSessionRequest(request(false));

        assertThat(created.getSessions().getFirst().getCourtRoomId(), is(nullValue()));
    }

    @Test
    void shouldSkipACourtRoomWithoutAnIdAndIgnoreTheGlobalCourtRoomId() {
        givenCourtRooms(room(null, 7), room(ROOM_3, 3), room(ROOM_2, 1));

        assertThat(service.defaultCourtRoomId(COURT_CENTRE_ID), is(ROOM_3));
    }

    private void givenCourtRooms(final CourtRoom... courtRooms) {
        when(referenceDataCache.getCpCourtRoomsByCourtCentreId(COURT_CENTRE_ID)).thenReturn(List.of(courtRooms));
    }

    private static CourtRoom room(final String id, final int number) {
        return new CourtRoom().id(id).cppCourtRoomId(number).oucodeUUID(COURT_CENTRE_ID);
    }

    private static WeekCommencingCreateRequest request(final Boolean isOverbookingAllowed) {
        return new WeekCommencingCreateRequest()
                .courtCentreId(COURT_CENTRE_ID)
                .businessType(BUSINESS_TYPE)
                .startDate("2027-01-06")
                .endDate("2027-02-01")
                .capacityInMinutes(1800)
                .isOverbookingAllowed(isOverbookingAllowed);
    }

    @Test
    void shouldBuildUpdateCourtScheduleFromRequestAndPersistedSession() {
        final CourtSchedule persisted = persistedSession(COURT_ROOM_ID, BUSINESS_TYPE, "AD", ADULT,
                CROWN, true, null, null);
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
        assertThat(result.getPanel(), is(ADULT));
        assertThat(result.getMaxDuration(), is(CAPACITY_IN_MINUTES));
        assertThat(result.getMaxSlots(), is(0));
        assertThat(result.getAllDaySplit(), is(false));
        assertThat(result.getMaxDurationForMorning(), is(0));
        assertThat(result.getMaxDurationForAfternoon(), is(0));
        assertThat(result.getIsOverbookingAllowed(), is(true));
        assertThat(result.getJurisdiction(), is(CROWN));
        assertThat(result.getIsDraft(), is(true));
    }

    @Test
    void businessTypeShouldComeFromRequestNotPersistedEntity() {
        final CourtSchedule persisted = persistedSession(COURT_ROOM_ID, "TRL", "AD", ADULT,
                CROWN, true, null, null);
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
        final CourtSchedule persisted = persistedSession(COURT_ROOM_ID, BUSINESS_TYPE, "AD", ADULT,
                CROWN, true, startTime, endTime);
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
        final CourtSchedule persisted = persistedSession(COURT_ROOM_ID, BUSINESS_TYPE, "AD", ADULT,
                CROWN, true, null, null);
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
