package uk.gov.moj.cpp.courtscheduler.api.service;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;

import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.BusinessType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeekCommencingSessionServiceTest {

    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String COURT_CENTRE_ID = randomUUID().toString();
    private static final String BUSINESS_TYPE = "TRL";
    private static final String ROOM_1 = randomUUID().toString();
    private static final String ROOM_2 = randomUUID().toString();
    private static final String ROOM_3 = randomUUID().toString();

    @Mock
    private ReferenceDataCache referenceDataCache;
    @Mock
    private SessionsApiValidator sessionsApiValidator;
    @Mock
    private SessionsService sessionsService;
    @Captor
    private ArgumentCaptor<CreateSessionRequestParam> createCaptor;

    private WeekCommencingSessionService service;

    @BeforeEach
    void setUp() {
        service = new WeekCommencingSessionService(referenceDataCache, sessionsApiValidator, sessionsService);
    }

    @Test
    void shouldConvertToACrownDraftAllDayMondaySessionInTheFirstListedCourtRoomAndCreateIt() {
        givenCourtRooms(room(ROOM_1, 1001), room(ROOM_2, 1), room(ROOM_3, 3));
        givenValidAndNoDuplicate(false);

        service.create(request(true));

        verify(sessionsService).createWeekCommencing(createCaptor.capture(),
                eq(COURT_CENTRE_ID), eq(BUSINESS_TYPE), eq(LocalDate.of(2027, 1, 6)), eq(LocalDate.of(2027, 2, 1)));
        final CreateSessionRequestParam created = createCaptor.getValue();
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
        givenValidAndNoDuplicate(false);

        service.create(request(null));

        verify(sessionsService).createWeekCommencing(createCaptor.capture(), any(), any(), any(), any());
        assertThat(createCaptor.getValue().getSessions().getFirst().getIsOverbookingAllowed(), is(false));
    }

    @Test
    void shouldSkipACourtRoomWithoutAnIdAndIgnoreTheGlobalCourtRoomId() {
        givenCourtRooms(room(null, 7), room(ROOM_3, 3), room(ROOM_2, 1));

        assertThat(service.defaultCourtRoomId(COURT_CENTRE_ID), is(ROOM_3));
    }

    @Test
    void shouldRejectWhenTheCourtCentreHasNoCourtRoom() {
        givenCourtRooms();

        final ValidationException exception = assertThrows(ValidationException.class, () -> service.create(request(false)));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_NO_COURTROOM + COURT_CENTRE_ID));
        verify(sessionsService, never()).createWeekCommencing(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectWithTheStandardCreateValidationErrorAndNotCheckDuplicates() {
        givenCourtRooms(room(ROOM_1, 1));
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(buildErrorResponse("start date is in the past"));

        final ValidationException exception = assertThrows(ValidationException.class, () -> service.create(request(false)));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is("start date is in the past"));
        verify(sessionsService, never()).createWeekCommencing(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectSlotBasedBusinessTypes() {
        givenCourtRooms(room(ROOM_1, 1));
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(true)));

        final ValidationException exception = assertThrows(ValidationException.class, () -> service.create(request(false)));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED));
        verify(sessionsService, never()).createWeekCommencing(any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectADuplicateWeekCommencingSession() {
        givenCourtRooms(room(ROOM_1, 1));
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(false)));
        when(sessionsService.createWeekCommencing(any(), any(), any(), any(), any()))
                .thenReturn(buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS));

        final ValidationException exception = assertThrows(ValidationException.class, () -> service.create(request(false)));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is(ErrorMessages.DUPLICATE_SESSIONS));
    }

    @Test
    void shouldRejectADateRangeWithNoMonday() {
        givenCourtRooms(room(ROOM_1, 1));
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(false)));
        // Tuesday 5 to Sunday 10 January 2027
        final WeekCommencingCreateRequest noMonday = request(false).startDate("2027-01-05").endDate("2027-01-10");

        final ValidationException exception = assertThrows(ValidationException.class, () -> service.create(noMonday));

        assertThat(exception.getErrors().getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_NO_MONDAY));
        verify(sessionsService, never()).createWeekCommencing(any(), any(), any(), any(), any());
    }

    @Test
    void shouldAcceptARangeOfASingleMonday() {
        givenCourtRooms(room(ROOM_1, 1));
        givenValidAndNoDuplicate(false);

        service.create(request(false).startDate("2027-01-04").endDate("2027-01-04"));

        verify(sessionsService).createWeekCommencing(any(), any(), any(), eq(LocalDate.of(2027, 1, 4)), eq(LocalDate.of(2027, 1, 4)));
    }

    private void givenValidAndNoDuplicate(final boolean slotBased) {
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(slotBased)));
        when(sessionsService.createWeekCommencing(any(), any(), any(), any(), any())).thenReturn(EMPTY_JSON_OBJECT);
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
                .capacity(1800)
                .isOverbookingAllowed(isOverbookingAllowed);
    }
}
