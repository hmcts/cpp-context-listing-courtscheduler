package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.exception.MoveHearingToPastDateNoSessionException;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Behavioural tests for {@code POST /hearings/{hearingId}} (courtscheduler.move-hearing-to-past-date).
 * The endpoint supports both jurisdictions, a mandatory room + start/end time-of-day, and a multi-day
 * [startDate, endDate] span returned as a {@code bookedSlots} array (one slot per sitting day). The
 * duration is derived from the submitted window (single-day) or fixed at a full court day (multi-day);
 * future dates are rejected. Business errors are bare {errorCode, message} 422 bodies.
 */
@ExtendWith(MockitoExtension.class)
class MoveHearingToPastDateApiTest {

    private static final int MULTI_DAY_DURATION_MINUTES = 360;

    @Mock
    private SlotsUpdateService slotsUpdateService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HearingSlotsApi api;

    @BeforeEach
    void setUp() {
        api = new HearingSlotsApi(slotsUpdateService, null, null, null, null, null, null, null, null, objectMapper);
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

    private static MoveHearingToPastDateResponse stubResponse(final String hearingId, final String sessionDate, final int durationInMinutes) {
        return new MoveHearingToPastDateResponse(
                hearingId, randomUUID().toString(), randomUUID().toString(), sessionDate,
                sessionDate + "T09:00:00.000Z", sessionDate + "T12:00:00.000Z", durationInMinutes,
                false, "NGAP", "MOVE_TO_PAST_DATE", false);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> bookedSlots(final ResponseEntity<Map<String, Object>> response) {
        return (List<Map<String, Object>>) response.getBody().get("bookedSlots");
    }

    @Test
    void shouldDelegateAndReturnBookedSlots_withSingleDayDurationFromWindow() {
        final String hearingId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();
        final MoveHearingToPastDateResponse stub = stubResponse(hearingId, "2026-05-01", 30);

        // single-day 09:00 -> 09:30 = 30-minute submitted window
        when(slotsUpdateService.moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(),
                eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-01")), any(), any(), eq("MAGISTRATES"), eq(30)))
                .thenReturn(List.of(stub));

        final ResponseEntity<Map<String, Object>> response = api.postMoveHearingToPastDate(hearingId,
                movePayload(courtCentreId, "MAGISTRATES", "2026-05-01T09:00:00.000Z", "2026-05-01T09:30:00.000Z"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, bookedSlots(response).size());
        final Map<String, Object> slot = bookedSlots(response).get(0);
        assertEquals(hearingId, slot.get("hearingId"));
        assertEquals(stub.courtScheduleId(), slot.get("courtScheduleId"));
        assertEquals("2026-05-01", slot.get("sessionDate"));
        assertEquals(30, slot.get("durationInMinutes"));
        assertEquals("MOVE_TO_PAST_DATE", slot.get("source"));
    }

    @Test
    void shouldReturnAllBookedSlots_forAMultiDayMove_withFullCourtDayDuration() {
        final String hearingId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        // multi-day 07-01 10:30 -> 07-02 17:00 fixes each sitting day at a full court day (360 min)
        when(slotsUpdateService.moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(),
                eq(LocalDate.parse("2026-07-01")), eq(LocalDate.parse("2026-07-02")), any(), any(), eq("MAGISTRATES"),
                eq(MULTI_DAY_DURATION_MINUTES)))
                .thenReturn(List.of(stubResponse(hearingId, "2026-07-01", MULTI_DAY_DURATION_MINUTES),
                        stubResponse(hearingId, "2026-07-02", MULTI_DAY_DURATION_MINUTES)));

        final ResponseEntity<Map<String, Object>> response = api.postMoveHearingToPastDate(hearingId,
                movePayload(courtCentreId, "MAGISTRATES", "2026-07-01T10:30:00.000Z", "2026-07-02T17:00:00.000Z"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, bookedSlots(response).size());
        assertEquals("2026-07-01", bookedSlots(response).get(0).get("sessionDate"));
        assertEquals("2026-07-02", bookedSlots(response).get(1).get("sessionDate"));
    }

    @Test
    void shouldComputeSingleDayDurationFromSubmittedWindow() {
        final String hearingId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        // 10:30 -> 10:50 = 20 minutes
        when(slotsUpdateService.moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(),
                eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-01")), any(), any(), eq("MAGISTRATES"), eq(20)))
                .thenReturn(List.of(stubResponse(hearingId, "2026-05-01", 20)));

        final ResponseEntity<Map<String, Object>> response = api.postMoveHearingToPastDate(hearingId,
                movePayload(courtCentreId, "MAGISTRATES", "2026-05-01T10:30:00.000Z", "2026-05-01T10:50:00.000Z"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(slotsUpdateService).moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(),
                eq(LocalDate.parse("2026-05-01")), eq(LocalDate.parse("2026-05-01")), any(), any(), eq("MAGISTRATES"), eq(20));
    }

    @Test
    void shouldSupportCrownJurisdiction() {
        final String hearingId = randomUUID().toString();
        final String courtCentreId = randomUUID().toString();

        when(slotsUpdateService.moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(), any(), any(), any(), any(), eq("CROWN"), anyInt()))
                .thenReturn(List.of(stubResponse(hearingId, "2026-05-01", 30)));

        final ResponseEntity<Map<String, Object>> response = api.postMoveHearingToPastDate(hearingId,
                movePayload(courtCentreId, "CROWN", "2026-05-01T09:00:00.000Z", "2026-05-01T09:30:00.000Z"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, bookedSlots(response).size());
        verify(slotsUpdateService).moveHearingToPastDate(eq(hearingId), eq(courtCentreId), any(), any(), any(), any(), any(), eq("CROWN"), anyInt());
    }

    @Test
    void shouldReturn422_whenMoveStartDateIsAfterToday() {
        final ResponseEntity<Map<String, Object>> response = api.postMoveHearingToPastDate(
                randomUUID().toString(),
                movePayload(randomUUID().toString(), "MAGISTRATES", "2999-01-01T09:00:00.000Z", "2999-01-01T09:30:00.000Z"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("FUTURE_DATE_NOT_ALLOWED", response.getBody().get("errorCode"));
        verify(slotsUpdateService, never()).moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldReturn422_whenMoveEndDateIsAfterToday() {
        final ResponseEntity<Map<String, Object>> response = api.postMoveHearingToPastDate(
                randomUUID().toString(),
                movePayload(randomUUID().toString(), "MAGISTRATES", "2026-05-01T09:00:00.000Z", "2999-01-01T09:30:00.000Z"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("FUTURE_DATE_NOT_ALLOWED", response.getBody().get("errorCode"));
        verify(slotsUpdateService, never()).moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldReturn422_andPropagateMessage_whenMoveFindsNoSession() {
        when(slotsUpdateService.moveHearingToPastDate(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .thenThrow(new MoveHearingToPastDateNoSessionException("no session"));

        final ResponseEntity<Map<String, Object>> response = api.postMoveHearingToPastDate(
                randomUUID().toString(),
                movePayload(randomUUID().toString(), "MAGISTRATES", "2026-05-01T09:00:00.000Z", "2026-05-01T09:30:00.000Z"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("NO_SESSION_FOUND", response.getBody().get("errorCode"));
        // team/pasthearings behaviour: the service's message is propagated as-is (the listing side
        // owns the fixed user-facing copy for NO_SESSION_FOUND)
        assertEquals("no session", response.getBody().get("message"));
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
            // startTime is a mandatory absolute-UTC instant; endTime is the multi-day upper bound
            assertTrue(schemaObject.getJsonObject("properties").containsKey("startTime"));
            assertTrue(schemaObject.getJsonObject("properties").containsKey("endTime"));
        }
    }
}
