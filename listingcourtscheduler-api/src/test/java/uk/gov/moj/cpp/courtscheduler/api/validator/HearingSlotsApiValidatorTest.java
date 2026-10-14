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
import uk.gov.moj.cpp.courtscheduler.openapi.model.CrownSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MagsSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MoveHearingToPastDateRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RequestedCourtSchedule;
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
    private static final String ADULT_2 = "ADULT";
    private static final String CROWN_2 = "CROWN";
    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String TEST_SCHEDULE_ID = "test-schedule-id";

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
            final String courtSession,
            final String status) {
        return new HearingSlotRequestParam(
                ADULT_2,
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
        final RequestedCourtSchedule requestedSchedule = new RequestedCourtSchedule();
        requestedSchedule.setCourtScheduleId(TEST_SCHEDULE_ID);
        requestedSchedule.setDurationInMinutes(30);

        final CourtSchedule mockSchedule = mock(CourtSchedule.class);
        when(mockSchedule.isSlotBased()).thenReturn(false);

        when(courtScheduleRepository.findBy(TEST_SCHEDULE_ID)).thenReturn(mockSchedule);

        final HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setCourtScheduleIds(List.of(requestedSchedule));

        final JsonObject result = validator.listHearingSlotsValidation(List.of(hearingSlot));
        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnErrorWhenCourtScheduleNotFound() {
        final RequestedCourtSchedule requestedSchedule = new RequestedCourtSchedule();
        requestedSchedule.setCourtScheduleId(TEST_SCHEDULE_ID);

        when(courtScheduleRepository.findBy(TEST_SCHEDULE_ID)).thenReturn(null);

        final HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setCourtScheduleIds(List.of(requestedSchedule));

        final JsonObject result = validator.listHearingSlotsValidation(List.of(hearingSlot));
        final String errorMessage = result.getString(ERROR_MESSAGE);
        assertEquals("Requested CourSchedule not found. Id: test-schedule-id", errorMessage);
    }

    @Test
    void shouldReturnErrorWhenDurationMissingAndNotSlotBased() {
        final RequestedCourtSchedule requestedSchedule = new RequestedCourtSchedule();
        requestedSchedule.setCourtScheduleId(TEST_SCHEDULE_ID);

        final CourtSchedule mockSchedule = mock(CourtSchedule.class);
        when(mockSchedule.isSlotBased()).thenReturn(false);

        when(courtScheduleRepository.findBy(TEST_SCHEDULE_ID)).thenReturn(mockSchedule);

        final HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setCourtScheduleIds(List.of(requestedSchedule));

        final JsonObject result = validator.listHearingSlotsValidation(List.of(hearingSlot));
        final String errorMessage = result.getString(ERROR_MESSAGE);
        assertEquals("No duration supplied for requested CourtSchedule: test-schedule-id", errorMessage);
    }

    // --- getHearingSlotsValidation: mandatory and format errors ---

    @Test
    void shouldReturnErrorWhenPanelBlank() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(null, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null,  null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "panel" + CANNOT_BE_NULL, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenSessionStartDateBlank() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(ADULT_2, null, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null,null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "sessionStartDate" + CANNOT_BE_NULL, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenSessionEndDateBlank() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(ADULT_2, VALID_START_DATE, null, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "sessionEndDate" + CANNOT_BE_NULL, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenSessionStartDateInvalidFormat() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(ADULT_2, "not-a-date", VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null,null, null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("Start Date"));
        assertTrue(result.getString(ERROR_MESSAGE).contains("bad format"));
    }

    @Test
    void shouldReturnErrorWhenSessionEndDateInvalidFormat() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(ADULT_2, VALID_START_DATE, "invalid", null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("End Date"));
        assertTrue(result.getString(ERROR_MESSAGE).contains("bad format"));
    }

    @Test
    void shouldReturnErrorWhenExactHearingStartDateTimeInvalidFormat() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(ADULT_2, VALID_START_DATE, VALID_END_DATE, "not-iso-instant", "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("Exact Hearing Start DateTime"));
        assertTrue(result.getString(ERROR_MESSAGE).contains("bad format"));
    }

    @Test
    void shouldReturnErrorWhenBothOucodeL2CodeAndOuCodeBlank() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(ADULT_2, VALID_START_DATE, VALID_END_DATE, null, null, null, "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("Either"));
        assertTrue(result.getString(ERROR_MESSAGE).contains("oucodeL2Code"));
        assertTrue(result.getString(ERROR_MESSAGE).contains("ouCode"));
        assertTrue(result.getString(ERROR_MESSAGE).contains("should be entered"));
    }

    @Test
    void shouldReturnErrorWhenPageSizeBlank() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(ADULT_2, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", null, "1",
                null, null, null, null, null, null, false, null, null, null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "pageSize" + CANNOT_BE_NULL, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnErrorWhenPageNumberBlank() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(ADULT_2, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", null,
                null, null, null, null, null, null, false, null, null, null);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertEquals(MANDATORY_SEARCH_CRITERIA + "pageNumber" + CANNOT_BE_NULL, result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldReturnEmptyJsonWhenGetHearingSlotsRequestValid() {
        final JsonObject result = validator.getHearingSlotsValidation(validGetHearingSlotsRequest(null, null));

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnErrorWhenStartDateAfterEndDate() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(
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

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals("Start date must be on or before end date", result.getString(ERROR_MESSAGE));
    }

    @Test
    void shouldThrowBadRequestExceptionWhenHearingStartTimeIsInvalid() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(
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

        final ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> validator.getHearingSlotsValidation(request));

        assertTrue(thrown.getMessage().contains("invalid-date-format"));
    }

    // --- jurisdiction validation tests ---

    @Test
    void shouldPassValidationWhenJurisdictionIsCrown() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(
                ADULT_2, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, CROWN_2);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldPassValidationWhenJurisdictionIsMagistrates() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(
                ADULT_2, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, "MAGISTRATES");

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldReturnErrorWhenJurisdictionIsInvalid() {
        final HearingSlotRequestParam request = new HearingSlotRequestParam(
                ADULT_2, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, "INVALID");

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertFalse(result.isEmpty());
        assertTrue(result.getString(ERROR_MESSAGE).contains("Invalid jurisdiction value: INVALID"));
        assertTrue(result.getString(ERROR_MESSAGE).contains("Must be CROWN or MAGISTRATES"));
    }

    @Test
    void shouldPassValidationWhenJurisdictionIsBlankOrAbsent() {
        final HearingSlotRequestParam requestBlank = new HearingSlotRequestParam(
                ADULT_2, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        assertEquals(EMPTY_JSON_OBJECT, validator.getHearingSlotsValidation(requestBlank));

        final HearingSlotRequestParam requestNull = new HearingSlotRequestParam(
                ADULT_2, VALID_START_DATE, VALID_END_DATE, null, "L2", "OU", "10", "1",
                null, null, null, null, null, null, false, null, null, null);

        assertEquals(EMPTY_JSON_OBJECT, validator.getHearingSlotsValidation(requestNull));
    }

    // --- listHearingSlotsValidation: slot-based does not require duration ---

    @Test
    void shouldReturnEmptyJsonWhenSlotBasedScheduleWithoutDuration() {
        final RequestedCourtSchedule requestedSchedule = new RequestedCourtSchedule();
        requestedSchedule.setCourtScheduleId(TEST_SCHEDULE_ID);
        requestedSchedule.setDurationInMinutes(null);

        final CourtSchedule mockSchedule = mock(CourtSchedule.class);
        when(mockSchedule.isSlotBased()).thenReturn(true);
        when(courtScheduleRepository.findBy(TEST_SCHEDULE_ID)).thenReturn(mockSchedule);

        final HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setCourtScheduleIds(List.of(requestedSchedule));

        final JsonObject result = validator.listHearingSlotsValidation(List.of(hearingSlot));

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
        final HearingSlotRequestParam request = new HearingSlotRequestParam(
                ADULT_2, "2026-04-09", "2026-04-12", null, "L2", "OU", "10", "1",
                null, null, null, "AD", null, null, false, "5400", null, CROWN_2);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldPassWhenCrownMultidaySingleDayRange() {
        // Single day Mon 2026-04-06, duration=720 (2 days needed)
        // Hearing can start on Apr 6 and continue on Apr 7 — valid
        final HearingSlotRequestParam request = new HearingSlotRequestParam(
                ADULT_2, "2026-04-06", "2026-04-06", null, "L2", "OU", "10", "1",
                null, null, null, "AD", null, null, false, "720", null, CROWN_2);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    @Test
    void shouldPassWhenCrownMultidayEndDateIsWeekend() {
        // Thu 2026-04-02 to Fri 2026-04-03, duration=1080 (3 days needed)
        // Hearing starting Fri extends to Mon — valid
        final HearingSlotRequestParam request = new HearingSlotRequestParam(
                ADULT_2, "2026-04-02", "2026-04-03", null, "L2", "OU", "10", "1",
                null, null, null, "AD", null, null, false, "1080", null, CROWN_2);

        final JsonObject result = validator.getHearingSlotsValidation(request);

        assertEquals(EMPTY_JSON_OBJECT, result);
    }

    // ─── AC1/AC2/AC3: crownSearchAndBookValidation ───────────────────────────

    /* default */
    @org.junit.jupiter.api.Nested
    class CrownSearchAndBookValidation {

        @Test
        void should_passValidation_when_crownMinimalRequest_singleDay() {
            // AC1 — hearingId + courtCentreId + hearingDate + durationInMinutes present, single-day (<=360)
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .durationInMinutes(360);

            assertEquals(EMPTY_JSON_OBJECT, validator.crownSearchAndBookValidation(request));
        }

        @Test
        void should_passValidation_when_crownRequestCourtScheduleIdPresentForMultiDay() {
            // AC2 — courtScheduleId (anchor) is optional; when present for multi-day this is valid
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .courtScheduleId(UUID.randomUUID().toString())
                    .durationInMinutes(720);

            assertEquals(EMPTY_JSON_OBJECT, validator.crownSearchAndBookValidation(request));
        }

        @Test
        void should_passValidation_when_crownMultiDayNoAnchor() {
            // AC3 — no courtScheduleId is valid (court-centre search path)
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .durationInMinutes(1080);

            assertEquals(EMPTY_JSON_OBJECT, validator.crownSearchAndBookValidation(request));
        }

        @Test
        void should_returnError_when_crownHearingIdMissing() {
            // AC1 — hearingId is required
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .durationInMinutes(180);

            final JsonObject result = validator.crownSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).toLowerCase().contains("hearingid"));
        }

        @Test
        void should_returnError_when_crownHearingDateMissing() {
            // AC1 — hearingDate is required
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .durationInMinutes(180);

            final JsonObject result = validator.crownSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).toLowerCase().contains("hearingdate"));
        }

        @Test
        void should_returnError_when_crownCourtCentreIdMissing() {
            // courtCentreId is required — a null would otherwise bind into the centre SQL search
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .durationInMinutes(720);

            final JsonObject result = validator.crownSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).toLowerCase().contains("court"));
        }

        @Test
        void should_passValidation_when_crownMultiDayViaDateRange_noDuration() {
            // AC6 — date-range form: endDate present and > hearingDate, no durationInMinutes => valid multi-day
            final CrownSearchAndBookRequest request = new CrownSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .endDate(LocalDate.of(2026, 9, 3));

            assertEquals(EMPTY_JSON_OBJECT, validator.crownSearchAndBookValidation(request));
        }
    }

    // ─── AC4/AC5: magsSearchAndBookValidation ────────────────────────────────

    /* default */
    @org.junit.jupiter.api.Nested
    class MagsSearchAndBookValidation {

        @Test
        void should_passValidation_when_magsSingleDayIsPolice() {
            // AC4 — minimal valid MAGS request: isPolice present (true), single-day
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .durationInMinutes(180)
                    .isPolice(true);

            assertEquals(EMPTY_JSON_OBJECT, validator.magsSearchAndBookValidation(request));
        }

        @Test
        void should_returnError_when_magsCourtScheduleIdPresent() {
            // AC4 — MAGS action MUST NOT accept courtScheduleId (no anchor concept for MAGS)
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .durationInMinutes(180)
                    .isPolice(false)
                    .courtScheduleId(UUID.randomUUID().toString()); // NOT allowed

            final JsonObject result = validator.magsSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).toLowerCase().contains("courtscheduleid"));
        }

        @Test
        void should_returnError_when_magsCourtCentreIdMissing() {
            // courtCentreId is required — a null would otherwise bind into the sparse SQL search
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .durationInMinutes(720)
                    .isPolice(false);

            final JsonObject result = validator.magsSearchAndBookValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).toLowerCase().contains("court"));
        }

        @Test
        void should_passValidation_when_magsMultiDayDurationOver360() {
            // AC5 — multi-day MAGS via duration > 360
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .durationInMinutes(720)
                    .isPolice(false);

            assertEquals(EMPTY_JSON_OBJECT, validator.magsSearchAndBookValidation(request));
        }

        @Test
        void should_passValidation_when_magsMultiDayViaDateRange() {
            // AC5 — multi-day MAGS via date-range form
            final MagsSearchAndBookRequest request = new MagsSearchAndBookRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .hearingDate(LocalDate.of(2026, 9, 1))
                    .endDate(LocalDate.of(2026, 9, 5))
                    .isPolice(true);

            assertEquals(EMPTY_JSON_OBJECT, validator.magsSearchAndBookValidation(request));
        }
    }

    // ─── AC7: moveHearingToPastDateValidation ────────────────────────────────

    /* default */
    @org.junit.jupiter.api.Nested
    class MoveHearingToPastDateValidation {

        private static final String MAGS_START = "2025-03-03T10:00:00.000Z";

        @Test
        void should_passValidation_when_startDateIsInFuture() {
            // Past-only is enforced in CourtSchedulerApi before validation, not by this validator.
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .courtRoomId(UUID.randomUUID().toString())
                    .jurisdiction(CROWN_2)
                    .startDate(LocalDate.now().plusDays(1));

            assertEquals(EMPTY_JSON_OBJECT, validator.moveHearingToPastDateValidation(request));
        }

        @Test
        void should_passValidation_when_startDateIsToday() {
            // The validator itself does not apply the past-only rule (CourtSchedulerApi does, before validation).
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .courtRoomId(UUID.randomUUID().toString())
                    .jurisdiction(CROWN_2)
                    .startDate(LocalDate.now());

            assertEquals(EMPTY_JSON_OBJECT, validator.moveHearingToPastDateValidation(request));
        }

        @Test
        void should_passValidation_when_crownOptionalCourtScheduleIdPresent() {
            // AC7 — courtScheduleId is optional anchor for CROWN; its presence must not fail validation
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .courtRoomId(UUID.randomUUID().toString())
                    .jurisdiction(CROWN_2)
                    .startDate(LocalDate.of(2025, 3, 3))
                    .courtScheduleId(UUID.randomUUID().toString());

            assertEquals(EMPTY_JSON_OBJECT, validator.moveHearingToPastDateValidation(request));
        }

        @Test
        void should_passValidation_when_magsJurisdictionNoAnchor() {
            // AC7 — MAGS: courtScheduleId absent, jurisdiction=MAGISTRATES, complete window => valid
            assertEquals(EMPTY_JSON_OBJECT, validator.moveHearingToPastDateValidation(
                    magsMove(MAGS_START, "2025-03-03T11:00:00.000Z")));
        }

        @Test
        void should_returnError_when_magsStartTimeMissing() {
            // SPRDT-1447: the MAGISTRATES lookup needs the time-of-day, so startTime itself is required
            final JsonObject result = validator.moveHearingToPastDateValidation(magsMove(null, null));
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).contains("startTime"));
        }

        @Test
        void should_returnError_when_onlyStartTimeSupplied() {
            final JsonObject result = validator.moveHearingToPastDateValidation(magsMove(MAGS_START, null));
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).contains("must both be supplied"));
        }

        @Test
        void should_returnError_when_endTimeBeforeStartTime() {
            final JsonObject result = validator.moveHearingToPastDateValidation(
                    magsMove(MAGS_START, "2025-03-03T09:30:00.000Z"));
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).contains("endTime must not be before startTime"));
        }


        private MoveHearingToPastDateRequest magsMove(final String startTime, final String endTime) {
            return new MoveHearingToPastDateRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .courtRoomId(UUID.randomUUID().toString())
                    .jurisdiction("MAGISTRATES")
                    .startDate(LocalDate.of(2025, 3, 3))
                    .startTime(startTime)
                    .endTime(endTime)
                    .durationInMinutes(720);
        }

        @Test
        void should_returnError_when_jurisdictionMissing() {
            // AC7 — jurisdiction is required in move-hearing-to-past-date schema
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .courtRoomId(UUID.randomUUID().toString())
                    .startDate(LocalDate.of(2025, 3, 3))
                    .durationInMinutes(360);

            final JsonObject result = validator.moveHearingToPastDateValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).toLowerCase().contains("jurisdiction"));
        }

        @Test
        void should_returnError_when_courtRoomIdMissing() {
            // Main-contract alignment: courtRoomId is now mandatory (the caller names the room to search within).
            final MoveHearingToPastDateRequest request = new MoveHearingToPastDateRequest()
                    .hearingId(UUID.randomUUID().toString())
                    .courtCentreId(UUID.randomUUID().toString())
                    .jurisdiction(CROWN_2)
                    .startDate(LocalDate.of(2025, 3, 3));

            final JsonObject result = validator.moveHearingToPastDateValidation(request);
            assertFalse(result.isEmpty());
            assertTrue(result.getString(ERROR_MESSAGE).toLowerCase().contains("courtroomid"));
        }
    }

}
