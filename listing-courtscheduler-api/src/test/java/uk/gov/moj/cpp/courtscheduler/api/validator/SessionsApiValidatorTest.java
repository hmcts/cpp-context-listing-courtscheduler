package uk.gov.moj.cpp.courtscheduler.api.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;

import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;

import java.time.LocalDate;

import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SessionsApiValidatorTest {

    private SessionsApiValidator sessionsApiValidator;

    @Mock
    private CreateSessionRequestParam createSessionRequestParam;

    @Mock
    private RepeatPattern repeatPattern;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        sessionsApiValidator = new SessionsApiValidator();
    }

    @Test
    public void shouldReturnErrorWhenPatternStartDateIsInPast() {
        LocalDate pastDate = LocalDate.now().minusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(pastDate);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(START_DATE_IS_INVALID + pastDate.toString(), result.getString("errorMessage"));
    }

    @Test
    public void shouldReturnErrorWhenFrequencyIsEveryWeekAndEndDateIsNull() {
        LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.EVERY_WEEK);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals("Invalid combination of parameters: For More Than once, you should supply a repeat-for and end date ", result.getString("errorMessage"));
    }


    @Test
    public void shouldReturnEmptyJsonObjectWhenValidationIsSuccessful() {
        LocalDate futureDate = LocalDate.now().plusDays(1);

        when(createSessionRequestParam.getRepeatPattern()).thenReturn(repeatPattern);
        when(repeatPattern.getStartDate()).thenReturn(futureDate);
        when(repeatPattern.getEndDate()).thenReturn(null);
        when(repeatPattern.getRepeatFor()).thenReturn(null);
        when(repeatPattern.getFrequency()).thenReturn(RepeatFrequency.ONCE);

        JsonObject result = sessionsApiValidator.getSessionsCreateValidation(createSessionRequestParam);

        assertEquals(0, result.size());
    }
}
