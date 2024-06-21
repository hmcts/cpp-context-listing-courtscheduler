package uk.gov.moj.cpp.courtscheduler.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.moj.cpp.courtscheduler.domain.*;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourtScheduleServiceTest {
    @Mock
    private CourtScheduleRepository courtScheduleRepository;


    @InjectMocks
    private CourtScheduleService courtScheduleService;

    @Test
    public void shouldGetCourtSchedulesBetweenLastUpdatedOn() {
        // given
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParam();
        CourtSchedule courtSchedule = new CourtSchedule();
        given(courtScheduleRepository.findBy(courtScheduleRequestParam)).willReturn(List.of(courtSchedule));

        List<CourtSchedule> courtSchedules = courtScheduleService.getCourtSchedules(courtScheduleRequestParam);

        assertThat(courtSchedules.contains(courtSchedule), is(true));
    }

    @Test
    void shouldProcessProvisionalBookingRequestSuccessfully() {
        SessionsParam sessionsParam = new SessionsParam();
        sessionsParam.setSessions(List.of("1", "2"));
        List<CourtSchedule> courtSchedules = new ArrayList<>();

        when(courtScheduleRepository.deleteCourtSchedule(anyList())).thenReturn(courtSchedules);

        JsonObject response = courtScheduleService.deleteCourtScheduleSessions(sessionsParam);

        assertTrue(response.get("sessions").asJsonArray().isEmpty());
    }

    @Test
    public void shouldUpdateCourtSchedule() {
        // given
        UpdateCourtSchedule updateCourtSchedule = new UpdateCourtSchedule();
        given(courtScheduleRepository.update(updateCourtSchedule)).willReturn(Result.SUCCESS());

        Result result = courtScheduleService.update(updateCourtSchedule);

        assertThat(result.isSuccess(), is(true));
    }

    private CourtScheduleRequestParam courtScheduleRequestParam() {
        String courtCentreId = "courtCentreId";
        String courtRoomId = "courtRoomId";
        String businessType = "businessType";
        String sessionStartDate = "2024-12-01";
        String sessionEndDate = "2024-12-03";
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId,
                businessType, sessionStartDate, sessionEndDate, pageSize, pageNumber);
    }

}

