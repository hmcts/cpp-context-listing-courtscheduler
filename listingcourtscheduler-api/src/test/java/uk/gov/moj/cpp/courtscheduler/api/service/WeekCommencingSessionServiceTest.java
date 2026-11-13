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
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeekCommencingSessionServiceTest {

    private static final String COURT_CENTRE_ID = randomUUID().toString();
    private static final String BUSINESS_TYPE = "TRL";
    private static final String ROOM_1 = randomUUID().toString();
    private static final String ROOM_2 = randomUUID().toString();
    private static final String ROOM_3 = randomUUID().toString();

    @Mock
    private ReferenceDataCache referenceDataCache;

    private WeekCommencingSessionService service;

    @BeforeEach
    void setUp() {
        service = new WeekCommencingSessionService(referenceDataCache);
    }

    @Test
    void shouldConvertToACrownDraftAllDayMondaySessionInTheFirstListedCourtRoom() {
        givenCourtRooms(room(ROOM_1, 1001), room(ROOM_2, 1), room(ROOM_3, 3));

        final CreateSessionRequestParam created = service.enrichCreateSessionRequest(request(true));

        assertThat(created.getSessions().size(), is(1));
        final Session session = created.getSessions().getFirst();
        assertThat(session.getCourtCentreId(), is(COURT_CENTRE_ID));
        assertThat(session.getCourtRoomId(), is(ROOM_1));
        assertThat(session.getJurisdiction(), is("CROWN"));
        assertThat(session.getSessionType(), is("AD"));
        assertThat(session.getBusinessType(), is(BUSINESS_TYPE));
        assertThat(session.getDuration(), is(1800));
        assertThat(session.getAllDaySplit(), is(false));
        assertThat(session.getPanel(), is("ADULT"));
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
}
