package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;

import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.BusinessType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeekCommencingApiValidatorTest {

    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String COURT_CENTRE_ID = randomUUID().toString();
    private static final String COURT_ROOM_ID = randomUUID().toString();
    private static final String BUSINESS_TYPE = "TRL";

    @Mock
    private SessionsApiValidator sessionsApiValidator;
    @Mock
    private ReferenceDataCache referenceDataCache;
    @Mock
    private SessionsService sessionsService;

    private WeekCommencingApiValidator validator;

    @BeforeEach
    void setUp() {
        validator = new WeekCommencingApiValidator(sessionsApiValidator, referenceDataCache, sessionsService);
    }

    @Test
    void shouldPassAValidRequest() {
        givenValidAndDurationBased();
        when(sessionsService.validateWeekCommencingIntegrity(COURT_CENTRE_ID, BUSINESS_TYPE,
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 2, 1))).thenReturn(EMPTY_JSON_OBJECT);

        final JsonObject result = validator.getWeekCommencingCreateValidation(request(), converted(COURT_ROOM_ID));

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void shouldRejectACourtCentreWithNoCourtRoomBeforeAnyOtherCheck() {
        final JsonObject result = validator.getWeekCommencingCreateValidation(request(), converted(null));

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_NO_COURTROOM + COURT_CENTRE_ID));
        verifyNoInteractions(sessionsApiValidator, sessionsService);
    }

    @Test
    void shouldReturnTheStandardCreateValidationErrorAndNotCheckDuplicates() {
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(buildErrorResponse("start date is in the past"));

        final JsonObject result = validator.getWeekCommencingCreateValidation(request(), converted(COURT_ROOM_ID));

        assertThat(result.getString(ERROR_MESSAGE), is("start date is in the past"));
        verifyNoInteractions(sessionsService);
    }

    @Test
    void shouldRejectASlotBasedBusinessType() {
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(true)));

        final JsonObject result = validator.getWeekCommencingCreateValidation(request(), converted(COURT_ROOM_ID));

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED));
        verifyNoInteractions(sessionsService);
    }

    @Test
    void shouldRejectADateRangeWithNoMonday() {
        givenValidAndDurationBased();
        // Tuesday 5 to Sunday 10 January 2027
        final WeekCommencingCreateRequest noMonday = request().startDate("2027-01-05").endDate("2027-01-10");

        final JsonObject result = validator.getWeekCommencingCreateValidation(noMonday, converted(COURT_ROOM_ID));

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_NO_MONDAY));
        verify(sessionsService, never()).validateWeekCommencingIntegrity(any(), any(), any(), any());
    }

    @Test
    void shouldAcceptARangeOfASingleMonday() {
        givenValidAndDurationBased();
        final LocalDate monday = LocalDate.of(2027, 1, 4);
        when(sessionsService.validateWeekCommencingIntegrity(COURT_CENTRE_ID, BUSINESS_TYPE, monday, monday))
                .thenReturn(EMPTY_JSON_OBJECT);

        final JsonObject result = validator.getWeekCommencingCreateValidation(
                request().startDate("2027-01-04").endDate("2027-01-04"), converted(COURT_ROOM_ID));

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void shouldRejectADuplicateWeekCommencingSession() {
        givenValidAndDurationBased();
        when(sessionsService.validateWeekCommencingIntegrity(any(), any(), any(), any()))
                .thenReturn(buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS));

        final JsonObject result = validator.getWeekCommencingCreateValidation(request(), converted(COURT_ROOM_ID));

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.DUPLICATE_SESSIONS));
    }

    private void givenValidAndDurationBased() {
        when(sessionsApiValidator.getSessionsCreateValidation(any())).thenReturn(EMPTY_JSON_OBJECT);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(false)));
    }

    private static CreateSessionRequestParam converted(final String courtRoomId) {
        return new CreateSessionRequestParam().sessions(List.of(new Session().courtRoomId(courtRoomId)));
    }

    private static WeekCommencingCreateRequest request() {
        return new WeekCommencingCreateRequest()
                .courtCentreId(COURT_CENTRE_ID)
                .businessType(BUSINESS_TYPE)
                .startDate("2027-01-06")
                .endDate("2027-02-01")
                .capacity(1800);
    }
}
