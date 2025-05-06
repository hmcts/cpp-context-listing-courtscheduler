package uk.gov.moj.cpp.courtscheduler.api.validator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import javax.json.JsonObject;
import java.util.List;

import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

public class HearingSlotsApiValidatorTest {
    @InjectMocks
    private HearingSlotsApiValidator validator;
    @Mock
    private CourtScheduleRepository courtScheduleRepository;


    @BeforeEach
    public void setUp() {
        courtScheduleRepository = mock(CourtScheduleRepository.class);
        validator = new HearingSlotsApiValidator();
        validator = Mockito.spy(validator);
        setField(validator, "courtScheduleRepository", courtScheduleRepository);
    }

    @Test
    public void shouldReturnEmptyJsonWhenValidCourtSchedule() {
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
    public void shouldReturnErrorWhenCourtScheduleNotFound() {
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
    public void shouldReturnErrorWhenDurationMissingAndNotSlotBased() {
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
}
