package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import org.springframework.web.server.ResponseStatusException;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.util.List;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

class HearingSlotsApiValidatorTest {
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
                null
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
                null
        );

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> validator.getHearingSlotsValidation(request));

        assertTrue(thrown.getMessage().contains("invalid-date-format"));
    }
}

