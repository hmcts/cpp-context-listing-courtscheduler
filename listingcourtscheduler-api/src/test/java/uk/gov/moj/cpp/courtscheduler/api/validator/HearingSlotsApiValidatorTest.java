package uk.gov.moj.cpp.courtscheduler.api.validator;

import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.List;

import javax.json.JsonObject;

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
    void shouldThrowBadRequestExceptionWhenHearingStartTimeIsInvalid() {
        HearingSlotRequestParam request = new HearingSlotRequestParam(
                "YOUTH",
                "2025-07-28",
                "2025-07-29",
                "L2",
                "OU",
                "10",
                "1",
                "courtRoomId",
                "courtRoomNumber",
                "businessType",
                "courtSession",
                false,
                "invalid-date-format" //other than zoned date format
        );

        BadRequestException thrown = assertThrows(BadRequestException.class,
                () -> validator.getHearingSlotsValidation(request));

        assertTrue(thrown.getMessage().contains("invalid-date-format"));
    }
}

