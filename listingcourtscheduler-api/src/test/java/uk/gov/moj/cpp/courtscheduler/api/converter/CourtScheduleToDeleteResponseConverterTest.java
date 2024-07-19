package uk.gov.moj.cpp.courtscheduler.api.converter;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleDeleteResponse;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleToDeleteResponseConverterTest {

    @InjectMocks
    private CourtScheduleToDeleteResponseConverter converter;

    @Test
    public void shouldConvertListCourtSchedule_ListCourtScheduleDeleteResponse() {
        List<CourtSchedule> courtScheduleList = List.of(random(CourtSchedule.class));

        List<CourtScheduleDeleteResponse> courtScheduleDeleteResponses = converter.convert(courtScheduleList);

        assertThat(courtScheduleDeleteResponses.size(), is(1));
        assertEquals(courtScheduleDeleteResponses.get(0).getCourtScheduleId(), courtScheduleList.get(0).getCourtScheduleId());
    }
}
