package uk.gov.moj.cpp.courtscheduler.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.ArrayList;
import java.util.List;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleServiceTest {

    @InjectMocks
    private CourtScheduleService courtScheduleService;
    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Test
    void shouldProcessProvisionalBookingRequestSuccessfully() {
        SessionsParam sessionsParam = new SessionsParam();
        sessionsParam.setSessions(List.of("1", "2"));
        List<CourtSchedule> courtSchedules = new ArrayList<>();

        when(courtScheduleRepository.deleteCourtSchedule(anyList())).thenReturn(courtSchedules);

        JsonObject response = courtScheduleService.deleteCourtScheduleSessions(sessionsParam);

        assertTrue(response.get("sessions").asJsonArray().isEmpty());
    }
}