package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.domain.BookedPastSession;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.exception.MoveHearingToPastDateNoSessionException;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MoveHearingToPastDateRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Behavioural tests for {@code POST /hearings/{hearingId}} with the
 * {@code courtscheduler.move-hearing-to-past-date} Content-Type - main's
 * {@code MoveHearingToPastDateApiTest}, ported onto {@link CourtSchedulerApi}'s Content-Type
 * dispatcher (SPRDT-1447). MAGISTRATES derives the dates, time-of-day and booked duration from the
 * submitted window and returns main's per-day items under {@code sessions}; CROWN keeps the team
 * (ccsph2) path. Only past dates are accepted. Business errors are bare {errorCode, message} 422s.
 */
@ExtendWith(MockitoExtension.class)
class MoveHearingToPastDateApiTest {

    private static final String MOVE_MT = "application/vnd.courtscheduler.move-hearing-to-past-date+json";
    private static final String MAGISTRATES = "MAGISTRATES";
    private static final String CROWN = "CROWN";
    private static final String SESSIONS_KEY = "sessions";
    private static final String ERROR_CODE = "errorCode";
    private static final String SESSION_DATE = "sessionDate";
    private static final String MAY_FIRST = "2026-05-01";
    private static final String MOVE_TO_PAST_DATE = "MOVE_TO_PAST_DATE";
    private static final String FUTURE_DATE_NOT_ALLOWED = "FUTURE_DATE_NOT_ALLOWED";
    private static final String HEARING_ID = "hearingId";
    private static final String NINE_AM = "09:00";
    private static final String MAY_FIRST_NINE_AM = "2026-05-01T09:00:00.000Z";
    private static final String MAY_FIRST_NINE_THIRTY = "2026-05-01T09:30:00.000Z";
    private static final int MULTI_DAY_DURATION_MINUTES = 360;
    private static final int UNPROCESSABLE = 422;

    @Mock
    private SlotsUpdateService slotsUpdateService;

    @Mock
    private HttpServletRequest request;

    private CourtSchedulerApi api;

    @BeforeEach
    void setUp() {
        api = new CourtSchedulerApi(new ObjectMapper(), request, null, null, null, null, null, null, null, null,
                slotsUpdateService, null, new HearingSlotsApiValidator(), null, null, null, null, null, null);
        lenient().when(request.getContentType()).thenReturn(MOVE_MT);
    }

    private ResponseEntity<Map<String, Object>> move(final String hearingId, final Map<String, Object> body) {
        return api.postSearchAndBookHearing(hearingId, body);
    }

    // startTime/endTime are absolute UTC instants; the API derives (startDate, endDate,
    // hearingStartTime, hearingEndTime, durationInMinutes) from them.
    private static Map<String, Object> movePayload(final String courtCentreId, final String jurisdiction,
                                                   final String startInstant, final String endInstant) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("courtCentreId", courtCentreId);
        body.put("courtRoomId", randomUUID().toString());
        body.put("jurisdiction", jurisdiction);
        body.put("startTime", startInstant);
        if (endInstant != null) {
            body.put("endTime", endInstant);
        }
        return body;
    }

    private static BookedPastSession stubSession(final String hearingId, final String sessionDate, final int durationInMinutes) {
        return new BookedPastSession(
                hearingId, randomUUID().toString(), randomUUID().toString(), sessionDate,
                sessionDate + "T09:00:00.000Z", sessionDate + "T12:00:00.000Z", durationInMinutes,
                false, "NGAP", MOVE_TO_PAST_DATE, false);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sessions(final ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get(SESSIONS_KEY);
    }

    private static String daysAgo(final int days, final String timeOfDay) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(days) + "T" + timeOfDay + ":00.000Z";
    }

    @Test
    void shouldDelegateAndReturnSessions_withSingleDayDurationFromWindow() {
        final String hearingId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();
        final BookedPastSession stub = stubSession(hearingId, MAY_FIRST, 30);

        // single-day 09:00 -> 09:30 = 30-minute submitted window
        when(slotsUpdateService.moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(),
                eq(LocalDate.parse(MAY_FIRST)), eq(LocalDate.parse(MAY_FIRST)), eq(NINE_AM), eq("09:30"), eq(MAGISTRATES), eq(30)))
                .thenReturn(List.of(stub));

        final ResponseEntity<Map<String, Object>> response = move(hearingId,
                movePayload(courtCentreId, MAGISTRATES, MAY_FIRST_NINE_AM, MAY_FIRST_NINE_THIRTY));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(hearingId, response.getBody().get(HEARING_ID));
        assertEquals(MOVE_TO_PAST_DATE, response.getBody().get("source"));
        assertEquals(1, sessions(response).size());
        final Map<String, Object> session = sessions(response).get(0);
        assertEquals(hearingId, session.get(HEARING_ID));
        assertEquals(stub.courtScheduleId(), session.get("courtScheduleId"));
        assertEquals(MAY_FIRST, session.get(SESSION_DATE));
        assertEquals(MAY_FIRST_NINE_AM, session.get("sessionStartTime"));
        assertEquals(30, session.get("durationInMinutes"));
        assertEquals(false, session.get("isDraft"));
        assertEquals(MOVE_TO_PAST_DATE, session.get("source"));
    }

    @Test
    void shouldReturnAllSessions_forAMultiDayMove_withFullCourtDayDuration() {
        final String hearingId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        // multi-day 07-01 10:30 -> 07-02 17:00 fixes each sitting day at a full court day (360 min)
        when(slotsUpdateService.moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(),
                eq(LocalDate.parse("2026-07-01")), eq(LocalDate.parse("2026-07-02")), any(), any(), eq(MAGISTRATES),
                eq(MULTI_DAY_DURATION_MINUTES)))
                .thenReturn(List.of(stubSession(hearingId, "2026-07-01", MULTI_DAY_DURATION_MINUTES),
                        stubSession(hearingId, "2026-07-02", MULTI_DAY_DURATION_MINUTES)));

        final ResponseEntity<Map<String, Object>> response = move(hearingId,
                movePayload(courtCentreId, MAGISTRATES, "2026-07-01T10:30:00.000Z", "2026-07-02T17:00:00.000Z"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, sessions(response).size());
        assertEquals("2026-07-01", sessions(response).get(0).get(SESSION_DATE));
        assertEquals("2026-07-02", sessions(response).get(1).get(SESSION_DATE));
    }

    @Test
    void shouldComputeSingleDayDurationFromSubmittedWindow() {
        final String hearingId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        // 10:30 -> 10:50 = 20 minutes
        when(slotsUpdateService.moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(),
                eq(LocalDate.parse(MAY_FIRST)), eq(LocalDate.parse(MAY_FIRST)), any(), any(), eq(MAGISTRATES), eq(20)))
                .thenReturn(List.of(stubSession(hearingId, MAY_FIRST, 20)));

        final ResponseEntity<Map<String, Object>> response = move(hearingId,
                movePayload(courtCentreId, MAGISTRATES, "2026-05-01T10:30:00.000Z", "2026-05-01T10:50:00.000Z"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(slotsUpdateService).moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(),
                eq(LocalDate.parse(MAY_FIRST)), eq(LocalDate.parse(MAY_FIRST)), eq("10:30"), eq("10:50"), eq(MAGISTRATES), eq(20));
    }

    @Test
    void shouldRouteCrownToTheTeamPath_notTheMagistratesPath() {
        final String hearingId = randomUUID().toString();
        when(slotsUpdateService.moveHearingToPastDate(any(MoveHearingToPastDateRequest.class)))
                .thenReturn(new MoveHearingToPastDateResponse(hearingId, MOVE_TO_PAST_DATE, Collections.emptyList()));

        final ResponseEntity<Map<String, Object>> response = move(hearingId,
                movePayload(randomUUID().toString(), CROWN, MAY_FIRST_NINE_AM, MAY_FIRST_NINE_THIRTY));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(slotsUpdateService).moveHearingToPastDate(any(MoveHearingToPastDateRequest.class));
        verify(slotsUpdateService, never()).moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldReturn422_whenMoveStartDateIsAfterToday() {
        final ResponseEntity<Map<String, Object>> response = move(randomUUID().toString(),
                movePayload(randomUUID().toString(), MAGISTRATES, "2999-01-01T09:00:00.000Z", "2999-01-01T09:30:00.000Z"));

        assertEquals(UNPROCESSABLE, response.getStatusCode().value());
        assertEquals(FUTURE_DATE_NOT_ALLOWED, response.getBody().get(ERROR_CODE));
        verify(slotsUpdateService, never()).moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldReturn422_whenMoveEndDateIsAfterToday() {
        final ResponseEntity<Map<String, Object>> response = move(randomUUID().toString(),
                movePayload(randomUUID().toString(), MAGISTRATES, MAY_FIRST_NINE_AM, "2999-01-01T09:30:00.000Z"));

        assertEquals(UNPROCESSABLE, response.getStatusCode().value());
        assertEquals(FUTURE_DATE_NOT_ALLOWED, response.getBody().get(ERROR_CODE));
        verify(slotsUpdateService, never()).moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    /** Past dates only: today is rejected for both jurisdictions (CROWN ruling of 2026-10-08). */
    @Test
    void shouldReturn422_whenMoveIsForToday() {
        final ResponseEntity<Map<String, Object>> magistrates = move(randomUUID().toString(),
                movePayload(randomUUID().toString(), MAGISTRATES, daysAgo(0, NINE_AM), daysAgo(0, "09:30")));
        final ResponseEntity<Map<String, Object>> crown = move(randomUUID().toString(),
                movePayload(randomUUID().toString(), CROWN, daysAgo(0, NINE_AM), daysAgo(0, "09:30")));

        assertEquals(UNPROCESSABLE, magistrates.getStatusCode().value());
        assertEquals(FUTURE_DATE_NOT_ALLOWED, magistrates.getBody().get(ERROR_CODE));
        assertEquals(UNPROCESSABLE, crown.getStatusCode().value());
        assertEquals(FUTURE_DATE_NOT_ALLOWED, crown.getBody().get(ERROR_CODE));
        verify(slotsUpdateService, never()).moveHearingToPastDate(any(MoveHearingToPastDateRequest.class));
    }

    @Test
    void shouldReturn422_andPropagateMessage_whenMoveFindsNoSession() {
        when(slotsUpdateService.moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new MoveHearingToPastDateNoSessionException("no session"));

        final ResponseEntity<Map<String, Object>> response = move(randomUUID().toString(),
                movePayload(randomUUID().toString(), MAGISTRATES, MAY_FIRST_NINE_AM, MAY_FIRST_NINE_THIRTY));

        assertEquals(UNPROCESSABLE, response.getStatusCode().value());
        assertEquals("NO_SESSION_FOUND", response.getBody().get(ERROR_CODE));
        // the service's message is propagated as-is (the listing side owns the fixed user-facing copy)
        assertEquals("no session", response.getBody().get("message"));
    }

    @Test
    void shouldReject_whenOnlyStartTimeIsSupplied() {
        final Map<String, Object> body = movePayload(randomUUID().toString(), MAGISTRATES, daysAgo(3, NINE_AM), null);

        assertThrows(ValidationException.class, () -> move(randomUUID().toString(), body));
        verify(slotsUpdateService, never()).moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldReject_whenEndTimeIsBeforeStartTime() {
        final Map<String, Object> body = movePayload(randomUUID().toString(), MAGISTRATES, daysAgo(3, "10:00"), daysAgo(3, NINE_AM));

        assertThrows(ValidationException.class, () -> move(randomUUID().toString(), body));
        verify(slotsUpdateService, never()).moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void moveHearingToPastDateSchema_shouldRequireMandatoryFields() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/request-schemas/courtscheduler.move-hearing-to-past-date.json");
             JsonReader reader = Json.createReader(in)) {
            final JsonObject schemaObject = reader.readObject();
            final List<String> requiredFields = schemaObject.getJsonArray("required")
                    .getValuesAs(JsonString.class).stream().map(JsonString::getString).toList();

            assertTrue(requiredFields.containsAll(List.of("courtCentreId", "courtRoomId", "jurisdiction", "startTime")));
            // hearingId rides in the URL path only — it must never appear in the request schema
            assertFalse(requiredFields.contains("hearingId"));
            assertFalse(schemaObject.getJsonObject("properties").containsKey("hearingId"));
            assertTrue(schemaObject.getJsonObject("properties").containsKey("startTime"));
            assertTrue(schemaObject.getJsonObject("properties").containsKey("endTime"));
        }
    }
}
