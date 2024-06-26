package uk.gov.moj.cpp.courtscheduler.api;

import org.junit.jupiter.api.Test;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSessionsView;

import java.util.List;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CourtScheduleToViewConverterTest {
    @Test
    public void shouldConvert() {
        // given
        String courtRoomId1 = random(String.class);
        String courtRoomId2 = random(String.class);

        CourtSchedule courtSchedule1WithCourtRoom1 = random(CourtSchedule.class);
        courtSchedule1WithCourtRoom1.setCourtRoomId(courtRoomId1);

        CourtSchedule courtSchedule2WithCourtRoom1 = random(CourtSchedule.class);
        courtSchedule2WithCourtRoom1.setCourtRoomId(courtRoomId1);

        CourtSchedule courtSchedule1WithCourtRoom2 = random(CourtSchedule.class);
        courtSchedule1WithCourtRoom2.setCourtRoomId(courtRoomId2);

        List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule1WithCourtRoom1, courtSchedule1WithCourtRoom2, courtSchedule2WithCourtRoom1));

        assertThat(courtSessionsViews.size(), is(2));
    }
}