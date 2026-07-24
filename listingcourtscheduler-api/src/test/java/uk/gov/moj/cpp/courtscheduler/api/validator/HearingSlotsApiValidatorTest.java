package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;

import org.springframework.web.server.ResponseStatusException;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

class HearingSlotsApiValidatorTest {
    private static final String VALID_START_DATE = "2025-07-28";
    private static final String VALID_END_DATE = "2025-07-30";

    @InjectMocks
    private HearingSlotsApiValidator validator;
    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @BeforeEach
    void setUp() {
        courtScheduleRepository = mock(CourtScheduleRepository.class);
        validator = new HearingSlotsApiValidator();
        validator = Mockito.spy(validator);
        setField(validator, "courtScheduleRepository", courtScheduleRepository);
    }

    /**
     * Builds a valid get-hearing-slots request that passes all mandatory and optional validations.
     * Override specific parameters in tests to trigger validation errors.
     */
    private static HearingSlotRequestParam validGetHearingSlotsRequest(
            String courtSession,
            String status) {
        return new HearingSlotRequestParam(
                "ADULT",
                VALID_START_DATE,
                VALID_END_DATE,
                null,
                "L2",
                "OU",
                "10",
                "1",
                null,
                null,
                null,
                courtSession,
                null,
                null,
                false,
                null,
                status,
                null
        );
    }

    @Test
    void shouldReturnEmptyJsonWhenValidCourtSchedule() {
        RequestedCourtSchedule requestedSchedule = new RequestedCourtSchedule();
        requestedSchedule.setCourtScheduleId("test-schedule-id");
        requestedSchedule.setDurationInMinutes(30);

        CourtSchedule mockSchedule = mock(CourtSchedule.class);
        when(mockSchedule.isSlotBased()).thenReturn(false);

        when(courtScheduleRepository.findBy("test-schedule-id")).thenReturn(mockSchedule);

        HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setCourtScheduleIds(List.of(requestedSchedule));

        JsonObject result = validator.listHearingSlotsValidation(List.of(hearingSlot));
        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnErrorWhenCourtScheduleNotFound() {
        RequestedCourtSchedule requestedSchedule = new RequestedCourtSchedule();
        requestedSchedule.setCourtScheduleId("test-schedule-id");

        when(courtScheduleRepository.findBy("test-schedule-id")).thenReturn(null);

        HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setCourtScheduleIds(List.of(requestedSchedule));

        JsonObject result = validator.listHearingSlotsValidation(List.of(hearingSlot));
        String errorMessage = result.getString("errorMessage");
        assertEquals("Requested CourSchedule not found. Id: test-schedule-id", errorMessage);
    }

    @Test
    void shouldReturnErrorWhenDurationMissingAndNotSlotBased() {
        RequestedCourtSchedule requestedSchedule = new RequestedCourtSchedule();
        requestedSchedule.setCourtScheduleId("test-schedule-id");

        CourtSchedule mockSchedule = mock(CourtSchedule.class);
        when(mockSchedule.isSlotBased()).thenReturn(false);

        when(courtScheduleRepository.findBy("test-schedule-id")).thenReturn(mockSchedule);

        HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setCourtScheduleIds(List.of(requestedSchedule));

        JsonObject result = validator.listHearingSlotsValidation(List.of(hearingSlot));
        String errorMessage = result.getString("errorMessage");
        assertEquals("No duration supplied for requested CourtSchedule: test-schedule-id", errorMessage);
    }

    // --- getHearingSlotsValidation: mandatory and format errors ---

    @Test
    void shouldReturnErrorWhenPanelBlank() {
        HearingSlotRequestParam request = new HearingSlotRequestParam(null, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null,  null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "panel" + CANNOT_BE_NULL, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenSessionStartDateBlank() {
        HearingSlotRequestParam request = new HearingSlotRequestParam("ADULT", null, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null,null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "sessionStartDate" + CANNOT_BE_NULL, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenSessionEndDateBlank() {
        HearingSlotRequestParam request = new HearingSlotRequestParam("ADULT", VALID_START_DATE, null, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "sessionEndDate" + CANNOT_BE_NULL, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenSessionStartDateInvalidFormat() {
        HearingSlotRequestParam request = new HearingSlotRequestParam("ADULT", "not-a-date", VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null,null, null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("Start Date"));
        assertTrue(result.getString("errorMessage").contains("bad format"));
    }

    @Test
    void shouldReturnErrorWhenSessionEndDateInvalidFormat() {
        HearingSlotRequestParam request = new HearingSlotRequestParam("ADULT", VALID_START_DATE, "invalid", null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("End Date"));
        assertTrue(result.getString("errorMessage").contains("bad format"));
    }

    @Test
    void shouldReturnErrorWhenExactHearingStartDateTimeInvalidFormat() {
        HearingSlotRequestParam request = new HearingSlotRequestParam("ADULT", VALID_START_DATE, VALID_END_DATE, "not-iso-instant", "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("Exact Hearing Start DateTime"));
        assertTrue(result.getString("errorMessage").contains("bad format"));
    }

    @Test
    void shouldReturnErrorWhenBothOucodeL2CodeAndOuCodeBlank() {
        HearingSlotRequestParam request = new HearingSlotRequestParam("ADULT", VALID_START_DATE, VALID_END_DATE, null, null, null, "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("Either"));
        assertTrue(result.getString("errorMessage").contains("oucodeL2Code"));
        assertTrue(result.getString("errorMessage").contains("ouCode"));
        assertTrue(result.getString("errorMessage").contains("should be entered"));
    }

    @Test
    void shouldReturnErrorWhenPageSizeBlank() {
        HearingSlotRequestParam request = new HearingSlotRequestParam("ADULT", VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", null, "1",
                null, null, null, null, null, null, false, null, null, null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "pageSize" + CANNOT_BE_NULL, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenPageNumberBlank() {
        HearingSlotRequestParam request = new HearingSlotRequestParam("ADULT", VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", null,
                null, null, null, null, null, null, false, null, null, null);

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "pageNumber" + CANNOT_BE_NULL, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnEmptyJsonWhenGetHearingSlotsRequestValid() {
        JsonObject result = validator.getHearingSlotsValidation(validGetHearingSlotsRequest(null, null));

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnErrorWhenStartDateAfterEndDate() {
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "YOUTH",
                "2025-07-29",
                "2025-07-28",
                Instant.now().toString(),
                "L2",
                "OU",
                "10",
                "1",
                "courtRoomId",
                "courtRoomNumber",
                "businessType",
                "courtSession",
                false,
                null,
                false,
                null,
                null, // status
                null  // jurisdiction
        );

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals("Start date must be on or before end date", result.getString("errorMessage"));
    }

    @Test
    void shouldThrowBadRequestExceptionWhenHearingStartTimeIsInvalid() {
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "YOUTH",
                "2025-07-28",
                "2025-07-29",
                Instant.now().toString(),
                "L2",
                "OU",
                "10",
                "1",
                "courtRoomId",
                "courtRoomNumber",
                "businessType",
                "courtSession",
                false,
                "invalid-date-format", //other than zoned date format
                false,
                null,
                null,
                null
        );

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> validator.getHearingSlotsValidation(request));

        assertTrue(thrown.getMessage().contains("invalid-date-format"));
    }

    // --- jurisdiction validation tests ---

    @Test
    void shouldPassValidationWhenJurisdictionIsCrown() {
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "ADULT", VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, "CROWN");

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldPassValidationWhenJurisdictionIsMagistrates() {
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "ADULT", VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, "MAGISTRATES");

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnErrorWhenJurisdictionIsInvalid() {
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "ADULT", VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, "INVALID");

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("Invalid jurisdiction value: INVALID"));
        assertTrue(result.getString("errorMessage").contains("Must be CROWN or MAGISTRATES"));
    }

    @Test
    void shouldPassValidationWhenJurisdictionIsBlankOrAbsent() {
        HearingSlotRequestParam requestBlank = new HearingSlotRequestParam(
                "ADULT", VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        assertEquals(EMPTY_JSON_OBJECT, validator.getHearingSlotsValidation(requestBlank));

        HearingSlotRequestParam requestNull = new HearingSlotRequestParam(
                "ADULT", VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        assertEquals(EMPTY_JSON_OBJECT, validator.getHearingSlotsValidation(requestNull));
    }

    // --- searchAndBookRequestValidation ---

    @Test
    void shouldReturnErrorWhenSearchAndBookHearingIdBlank() {
        HearingSlotSearchRequest request = new HearingSlotSearchRequest(null, "courtCentreId", "2025-05-13", null, null, null, 30, null);

        JsonObject result = validator.searchAndBookRequestValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "hearingId" + CANNOT_BE_NULL, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenSearchAndBookCourtCentreIdBlank() {
        HearingSlotSearchRequest request = new HearingSlotSearchRequest("hearing-1", null, "2025-05-13", null, null, null, 30, null);

        JsonObject result = validator.searchAndBookRequestValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("courtCentreId"));
        assertTrue(result.getString("errorMessage").contains("should be entered"));
    }

    @Test
    void shouldReturnErrorWhenSearchAndBookHearingSessionDateBlank() {
        HearingSlotSearchRequest request = new HearingSlotSearchRequest("hearing-1", "courtCentreId", null, null, null, null, 30, null);

        JsonObject result = validator.searchAndBookRequestValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "hearingSessionDate" + CANNOT_BE_NULL, result.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenSearchAndBookHearingSessionDateInvalidFormat() {
        HearingSlotSearchRequest request = new HearingSlotSearchRequest("hearing-1", "courtCentreId", "not-a-date", null, null, null, 30, null);

        JsonObject result = validator.searchAndBookRequestValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString("errorMessage").contains("Start Date"));
        assertTrue(result.getString("errorMessage").contains("bad format"));
    }

    @Test
    void shouldReturnEmptyJsonWhenSearchAndBookRequestValid() {
        HearingSlotSearchRequest request = new HearingSlotSearchRequest("hearing-1", "courtCentreId", "2025-05-13", null, null, null, 30, null);

        JsonObject result = validator.searchAndBookRequestValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    // --- listHearingSlotsValidation: slot-based does not require duration ---

    @Test
    void shouldReturnEmptyJsonWhenSlotBasedScheduleWithoutDuration() {
        RequestedCourtSchedule requestedSchedule = new RequestedCourtSchedule();
        requestedSchedule.setCourtScheduleId("test-schedule-id");
        requestedSchedule.setDurationInMinutes(null);

        CourtSchedule mockSchedule = mock(CourtSchedule.class);
        when(mockSchedule.isSlotBased()).thenReturn(true);
        when(courtScheduleRepository.findBy("test-schedule-id")).thenReturn(mockSchedule);

        HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setCourtScheduleIds(List.of(requestedSchedule));

        JsonObject result = validator.listHearingSlotsValidation(List.of(hearingSlot));

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    // --- Multiday CROWN: no date-range restriction ---
    // The date range defines where the first day of a multiday hearing can START.
    // Subsequent business days extend beyond the range, so the validator must not
    // reject requests where daysNeeded > days in range.

    @Test
    void shouldPassWhenCrownMultidayDurationExceedsDateRange() {
        // Thu 2026-04-09 to Sun 2026-04-12 = narrow 4-day range, duration=5400 (15 days needed)
        // The search extends beyond endDate, so this must NOT be rejected
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "ADULT", "2026-04-09", "2026-04-12", null, "L2", "OU", "10", "1",
                null, null, null, "AD", null, null, false, "5400", null, "CROWN");

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldPassWhenCrownMultidaySingleDayRange() {
        // Single day Mon 2026-04-06, duration=720 (2 days needed)
        // Hearing can start on Apr 6 and continue on Apr 7 — valid
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "ADULT", "2026-04-06", "2026-04-06", null, "L2", "OU", "10", "1",
                null, null, null, "AD", null, null, false, "720", null, "CROWN");

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldPassWhenCrownMultidayEndDateIsWeekend() {
        // Thu 2026-04-02 to Fri 2026-04-03, duration=1080 (3 days needed)
        // Hearing starting Fri extends to Mon — valid
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "ADULT", "2026-04-02", "2026-04-03", null, "L2", "OU", "10", "1",
                null, null, null, "AD", null, null, false, "1080", null, "CROWN");

        JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    // ─── AC1/AC2/AC3: crownSearchAndBookValidation ───────────────────────────

    @org.junit.jupiter.api.Nested
    class CrownSearchAndBookValidation {

        @Test
        void should_passValidation_when_crownMinimalRequest_singleDay() {
            // AC1 — hearingId + courtCentreId + hearingDate + durationInMinutes present, single-day (<=360)
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(360);

            assertEquals(EMPTY_JSON_OBJECT, validator.crownSearchAndBookValidation(request));
        }

        @Test
        void should_passValidation_when_crownRequestCourtScheduleIdPresentForMultiDay() {
            // AC2 — courtScheduleId (anchor) is optional; when present for multi-day this is valid
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setCourtScheduleId(UUID.randomUUID().toString())
                    .setDurationInMinutes(720);

            assertEquals(EMPTY_JSON_OBJECT, validator.crownSearchAndBookValidation(request));
        }

        @Test
        void should_passValidation_when_crownMultiDayNoAnchor() {
            // AC3 — no courtScheduleId is valid (court-centre search path)
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(1080);

            assertEquals(EMPTY_JSON_OBJECT, validator.crownSearchAndBookValidation(request));
        }

        @Test
        void should_returnError_when_crownHearingIdMissing() {
            // AC1 — hearingId is required
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(180);

            final JsonObject result = validator.crownSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("hearingid"));
        }

        @Test
        void should_returnError_when_crownHearingDateMissing() {
            // AC1 — hearingDate is required
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setDurationInMinutes(180);

            final JsonObject result = validator.crownSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("hearingdate"));
        }

        @Test
        void should_returnError_when_crownCourtCentreIdMissing() {
            // courtCentreId is required — a null would otherwise bind into the centre SQL search
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(720);

            final JsonObject result = validator.crownSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("court"));
        }

        @Test
        void should_passValidation_when_crownMultiDayViaDateRange_noDuration() {
            // AC6 — date-range form: endDate present and > hearingDate, no durationInMinutes => valid multi-day
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setEndDate(LocalDate.of(2026, 9, 3));

            assertEquals(EMPTY_JSON_OBJECT, validator.crownSearchAndBookValidation(request));
        }
    }

    // ─── AC4/AC5: magsSearchAndBookValidation ────────────────────────────────

    @org.junit.jupiter.api.Nested
    class MagsSearchAndBookValidation {

        @Test
        void should_passValidation_when_magsSingleDayIsPolice() {
            // AC4 — minimal valid MAGS request: isPolice present (true), single-day
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(180)
                    .setIsPolice(true);

            assertEquals(EMPTY_JSON_OBJECT, validator.magsSearchAndBookValidation(request));
        }

        @Test
        void should_returnError_when_magsCourtScheduleIdPresent() {
            // AC4 — MAGS action MUST NOT accept courtScheduleId (no anchor concept for MAGS)
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(180)
                    .setIsPolice(false)
                    .setCourtScheduleId(UUID.randomUUID().toString()); // NOT allowed

            final JsonObject result = validator.magsSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("courtscheduleid"));
        }

        @Test
        void should_returnError_when_magsCourtCentreIdMissing() {
            // courtCentreId is required — a null would otherwise bind into the sparse SQL search
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(720)
                    .setIsPolice(false);

            final JsonObject result = validator.magsSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("court"));
        }

        @Test
        void should_passValidation_when_magsMultiDayDurationOver360() {
            // AC5 — multi-day MAGS via duration > 360
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setDurationInMinutes(720)
                    .setIsPolice(false);

            assertEquals(EMPTY_JSON_OBJECT, validator.magsSearchAndBookValidation(request));
        }

        @Test
        void should_passValidation_when_magsMultiDayViaDateRange() {
            // AC5 — multi-day MAGS via date-range form
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setHearingDate(LocalDate.of(2026, 9, 1))
                    .setEndDate(LocalDate.of(2026, 9, 5))
                    .setIsPolice(true);

            assertEquals(EMPTY_JSON_OBJECT, validator.magsSearchAndBookValidation(request));
        }
    }

    // ─── AC7: moveHearingToPastDateValidation ────────────────────────────────

    @org.junit.jupiter.api.Nested
    class MoveHearingToPastDateValidation {

        @Test
        void should_passValidation_when_startDateIsInFuture() {
            // Past-only is owned by the caller (listing), not this validator — a future startDate must NOT be rejected.
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setJurisdiction("CROWN")
                    .setStartDate(LocalDate.now().plusDays(1));

            assertEquals(EMPTY_JSON_OBJECT, validator.moveHearingToPastDateValidation(request));
        }

        @Test
        void should_passValidation_when_startDateIsToday() {
            // AC7 — today is allowed (not future)
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setJurisdiction("CROWN")
                    .setStartDate(LocalDate.now());

            assertEquals(EMPTY_JSON_OBJECT, validator.moveHearingToPastDateValidation(request));
        }

        @Test
        void should_passValidation_when_crownOptionalCourtScheduleIdPresent() {
            // AC7 — courtScheduleId is optional anchor for CROWN; its presence must not fail validation
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setJurisdiction("CROWN")
                    .setStartDate(LocalDate.of(2025, 3, 3))
                    .setCourtScheduleId(UUID.randomUUID().toString());

            assertEquals(EMPTY_JSON_OBJECT, validator.moveHearingToPastDateValidation(request));
        }

        @Test
        void should_passValidation_when_magsJurisdictionNoAnchor() {
            // AC7 — MAGS: courtScheduleId absent, jurisdiction=MAGISTRATES => valid
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setJurisdiction("MAGISTRATES")
                    .setStartDate(LocalDate.of(2025, 3, 3))
                    .setDurationInMinutes(720);

            assertEquals(EMPTY_JSON_OBJECT, validator.moveHearingToPastDateValidation(request));
        }

        @Test
        void should_returnError_when_jurisdictionMissing() {
            // AC7 — jurisdiction is required in move-hearing-to-past-date schema
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .setHearingId(UUID.randomUUID().toString())
                    .setCourtCentreId(UUID.randomUUID().toString())
                    .setStartDate(LocalDate.of(2025, 3, 3))
                    .setDurationInMinutes(360);

            final JsonObject result = validator.moveHearingToPastDateValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("jurisdiction"));
        }
    }

    // ─── multiDay search-and-book validation ─────────────────────────────────

    @org.junit.jupiter.api.Nested
    class GetMultiDaySearchAndBookValidation {

        @Test
        void shouldReturnEmptyForMinimumAcceptedDuration() {
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "cs-1", "hid-1", 720);

            assertEquals(EMPTY_JSON_OBJECT, result);
        }

        @Test
        void shouldReturnEmptyFor1080Minutes() {
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "cs-1", "hid-1", 1080);

            assertEquals(EMPTY_JSON_OBJECT, result);
        }

        @Test
        void shouldRejectDurationOneBelowMinimum() {
            // 719 min is 1 min under the 720 floor — classic off-by-one target.
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "cs-1", "hid-1", 719);

            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").contains("720"),
                    "error message should name the 720-min minimum");
            assertTrue(result.getString("errorMessage").contains("719"),
                    "error message should echo the rejected value");
        }

        @Test
        void shouldRejectDurationEqualToOneFullDay() {
            // 360 min is exactly one day — not a multi-day booking.
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "cs-1", "hid-1", 360);

            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").contains("720"));
        }

        @Test
        void shouldRejectZeroDuration() {
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "cs-1", "hid-1", 0);

            assertFalse(result.isEmpty());
        }

        @Test
        void shouldRejectNegativeDuration() {
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "cs-1", "hid-1", -60);

            assertFalse(result.isEmpty());
        }

        @Test
        void shouldRejectNullCourtScheduleId() {
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    null, "hid-1", 1080);

            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("courtscheduleid"));
        }

        @Test
        void shouldRejectBlankCourtScheduleId() {
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "   ", "hid-1", 1080);

            assertFalse(result.isEmpty());
        }

        @Test
        void shouldRejectNullHearingId() {
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "cs-1", null, 1080);

            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("hearingid"));
        }

        @Test
        void shouldRejectBlankHearingId() {
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    "cs-1", "", 1080);

            assertFalse(result.isEmpty());
        }

        @Test
        void shouldPrioritiseCourtScheduleIdCheckWhenBothMissing() {
            // courtScheduleId is the anchor — callers need to hear about that first.
            final JsonObject result = validator.getMultiDaySearchAndBookValidation(
                    null, null, 60);

            assertFalse(result.isEmpty());
            assertTrue(result.getString("errorMessage").toLowerCase().contains("courtscheduleid"));
        }
    }
}
