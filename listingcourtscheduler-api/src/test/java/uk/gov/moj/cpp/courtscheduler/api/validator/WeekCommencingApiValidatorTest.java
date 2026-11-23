package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
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
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingUpdateRequest;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;

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

        final JsonObject result = validator.getWeekCommencingCreateValidation(request(), converted(COURT_ROOM_ID));

        assertThat(result.isEmpty(), is(true));
        verifyNoInteractions(sessionsService);
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
    }

    @Test
    void shouldAcceptARangeOfASingleMonday() {
        givenValidAndDurationBased();

        final JsonObject result = validator.getWeekCommencingCreateValidation(
                request().startDate("2027-01-04").endDate("2027-01-04"), converted(COURT_ROOM_ID));

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void duplicateValidationShouldRejectADuplicateWeekCommencingSession() {
        when(sessionsService.validateWeekCommencingIntegrity(COURT_CENTRE_ID, BUSINESS_TYPE,
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 2, 1)))
                .thenReturn(buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS));

        final JsonObject result = validator.getWeekCommencingDuplicateValidation(request());

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.DUPLICATE_SESSIONS));
        verifyNoInteractions(sessionsApiValidator, referenceDataCache);
    }

    @Test
    void duplicateValidationShouldPassWhenNoMondayHasASession() {
        when(sessionsService.validateWeekCommencingIntegrity(COURT_CENTRE_ID, BUSINESS_TYPE,
                LocalDate.of(2027, 1, 6), LocalDate.of(2027, 2, 1))).thenReturn(EMPTY_JSON_OBJECT);

        assertThat(validator.getWeekCommencingDuplicateValidation(request()).isEmpty(), is(true));
    }

    @Test
    void duplicateValidationShouldLeaveAnUnparseableDateToTheStandardCreateValidation() {
        final JsonObject result = validator.getWeekCommencingDuplicateValidation(request().startDate("2027-13-45"));

        assertThat(result.isEmpty(), is(true));
        verifyNoInteractions(sessionsService);
    }

    @Test
    void updateValidationShouldPassWhenSessionExistsAndIsWeekCommencing() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persisted = new CourtSchedule();
        persisted.setWeekCommencing(true);
        persisted.setBusinessType(BUSINESS_TYPE);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(false)));

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, BUSINESS_TYPE), persisted);

        assertThat(result.isEmpty(), is(true));
        verifyNoInteractions(sessionsService);
    }

    @Test
    void updateValidationShouldReturnErrorWhenSessionNotFound() {
        final String courtScheduleId = randomUUID().toString();

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, BUSINESS_TYPE), null);

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.SESSION_NOT_FOUND));
    }

    @Test
    void updateValidationShouldReturnErrorWhenSessionIsNotWeekCommencing() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persisted = new CourtSchedule();
        persisted.setWeekCommencing(false);

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, BUSINESS_TYPE), persisted);

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_SESSION_NOT_WC));
    }

    @Test
    void updateValidationShouldRejectSessionInThePast() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persisted = new CourtSchedule();
        persisted.setWeekCommencing(true);
        persisted.setSessionDate(LocalDate.of(2020, 1, 6));
        persisted.setBusinessType(BUSINESS_TYPE);

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, BUSINESS_TYPE), persisted);

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED));
        verifyNoInteractions(referenceDataCache, sessionsService);
    }

    @Test
    void updateValidationShouldAllowEditForCurrentWeekAfterMonday() {
        final String courtScheduleId = randomUUID().toString();
        final LocalDate thisMonday = LocalDate.now(TimezoneUtils.LONDON_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        final CourtSchedule persisted = new CourtSchedule();
        persisted.setWeekCommencing(true);
        persisted.setSessionDate(thisMonday);
        persisted.setBusinessType(BUSINESS_TYPE);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(false)));

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, BUSINESS_TYPE), persisted);

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void updateValidationShouldRejectSessionFromPreviousWeek() {
        final String courtScheduleId = randomUUID().toString();
        final LocalDate lastMonday = LocalDate.now(TimezoneUtils.LONDON_ZONE)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
        final CourtSchedule persisted = new CourtSchedule();
        persisted.setWeekCommencing(true);
        persisted.setSessionDate(lastMonday);
        persisted.setBusinessType(BUSINESS_TYPE);

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, BUSINESS_TYPE), persisted);

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED));
        verifyNoInteractions(referenceDataCache, sessionsService);
    }

    @Test
    void updateValidationShouldRejectSlotBasedBusinessType() {
        final String courtScheduleId = randomUUID().toString();
        final String slotBasedType = "DVLA";
        final CourtSchedule persisted = new CourtSchedule();
        persisted.setWeekCommencing(true);
        persisted.setBusinessType(BUSINESS_TYPE);
        when(referenceDataCache.getRotaBusinessTypeByCode(slotBasedType))
                .thenReturn(Optional.of(new BusinessType().typeCode(slotBasedType).slot(true)));

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, slotBasedType), persisted);

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED));
        verifyNoInteractions(sessionsService);
    }

    @Test
    void updateValidationShouldRejectDuplicateWhenChangingToExistingBusinessType() {
        final String courtScheduleId = randomUUID().toString();
        final String newBusinessType = "LGT";
        final LocalDate sessionDate = LocalDate.of(2027, 1, 4);
        final CourtSchedule persisted = new CourtSchedule();
        persisted.setWeekCommencing(true);
        persisted.setBusinessType(BUSINESS_TYPE);
        persisted.setCourtHouseId(COURT_CENTRE_ID);
        persisted.setSessionDate(sessionDate);
        when(referenceDataCache.getRotaBusinessTypeByCode(newBusinessType))
                .thenReturn(Optional.of(new BusinessType().typeCode(newBusinessType).slot(false)));
        when(sessionsService.validateWeekCommencingIntegrity(COURT_CENTRE_ID, newBusinessType, sessionDate, sessionDate))
                .thenReturn(buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS));

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, newBusinessType), persisted);

        assertThat(result.getString(ERROR_MESSAGE), is(ErrorMessages.DUPLICATE_SESSIONS));
    }

    @Test
    void updateValidationShouldSkipDuplicateCheckWhenBusinessTypeIsUnchanged() {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule persisted = new CourtSchedule();
        persisted.setWeekCommencing(true);
        persisted.setBusinessType(BUSINESS_TYPE);
        when(referenceDataCache.getRotaBusinessTypeByCode(BUSINESS_TYPE))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(false)));

        final JsonObject result = validator.getWeekCommencingUpdateValidation(updateRequest(courtScheduleId, BUSINESS_TYPE), persisted);

        assertThat(result.isEmpty(), is(true));
        verifyNoInteractions(sessionsService);
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
                .capacityInMinutes(1800);
    }

    private static WeekCommencingUpdateRequest updateRequest(final String courtScheduleId, final String businessType) {
        return new WeekCommencingUpdateRequest()
                .courtScheduleId(courtScheduleId)
                .businessType(businessType)
                .capacityInMinutes(1800);
    }
}
