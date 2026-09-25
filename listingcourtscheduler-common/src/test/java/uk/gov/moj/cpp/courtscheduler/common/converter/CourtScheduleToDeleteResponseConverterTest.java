package uk.gov.moj.cpp.courtscheduler.common.converter;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.sessionTimeFormatter;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleDeleteResponseItem;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleToDeleteResponseConverterTest {

    @Test
    void shouldConvertListCourtSchedule_ListCourtScheduleDeleteResponse() {
        List<CourtSchedule> courtScheduleList = List.of(random(CourtSchedule.class));

        List<CourtScheduleDeleteResponseItem> courtScheduleDeleteResponses = CourtScheduleToDeleteResponseConverter.convert(courtScheduleList, List.of());

        assertThat(courtScheduleDeleteResponses.size(), is(1));
        assertEquals(courtScheduleDeleteResponses.get(0).getCourtScheduleId(), courtScheduleList.get(0).getCourtScheduleId());
        assertThat(courtScheduleDeleteResponses.get(0).getSessionStartTime(), is(sessionTimeFormatter(java.util.Date.from(courtScheduleList.get(0).getSessionStartTime().toInstant()))));
        assertThat(courtScheduleDeleteResponses.get(0).getSessionEndTime(), is(sessionTimeFormatter(java.util.Date.from(courtScheduleList.get(0).getSessionEndTime().toInstant()))));
        assertEquals(courtScheduleDeleteResponses.get(0).getIsOverbookingAllowed(), courtScheduleList.get(0).getOverbookingAllowed());
    }
}