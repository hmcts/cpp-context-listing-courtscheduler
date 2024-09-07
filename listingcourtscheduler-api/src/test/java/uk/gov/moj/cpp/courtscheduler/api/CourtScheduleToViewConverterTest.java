package uk.gov.moj.cpp.courtscheduler.api;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import uk.gov.moj.cpp.courtscheduler.api.converter.CourtScheduleToViewConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSessionsView;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class CourtScheduleToViewConverterTest {
    @Test
    public void shouldConvert() {
        // given
        String courtRoomId1 = "courtRoomId3";
        String courtRoomId2 = "courtRoomId2";
        // and
        LocalDate sessionDate1 = LocalDate.now();
        LocalDate sessionDate2 = LocalDate.now();
        LocalDate sessionDate3 = LocalDate.now().plusDays(1);

        CourtSchedule courtSchedule1WithCourtRoom1 = random(CourtSchedule.class);
        courtSchedule1WithCourtRoom1.setCourtRoomId(courtRoomId1);
        courtSchedule1WithCourtRoom1.setCourtRoomName(courtRoomId1);
        courtSchedule1WithCourtRoom1.setSessionDate(sessionDate1);

        CourtSchedule courtSchedule2WithCourtRoom1 = random(CourtSchedule.class);
        courtSchedule2WithCourtRoom1.setCourtRoomId(courtRoomId2);
        courtSchedule2WithCourtRoom1.setCourtRoomName(courtRoomId2);
        courtSchedule2WithCourtRoom1.setSessionDate(sessionDate2);

        CourtSchedule courtSchedule1WithCourtRoom2 = random(CourtSchedule.class);
        courtSchedule1WithCourtRoom2.setCourtRoomId(courtRoomId2);
        courtSchedule1WithCourtRoom2.setCourtRoomName(courtRoomId2);
        courtSchedule1WithCourtRoom2.setSessionDate(sessionDate3);

        List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule1WithCourtRoom1, courtSchedule1WithCourtRoom2, courtSchedule2WithCourtRoom1));

        assertThat(courtSessionsViews.size(), is(2));
        assertThat(courtSessionsViews.get(0).getSessions().size(), is(2));
        assertThat(courtSessionsViews.get(0).getSessions().get(0).getSessionDate(), is(sessionDate2));
        assertThat(courtSessionsViews.get(0).getSessions().get(1).getSessionDate(), is(sessionDate3));
        assertThat(courtSessionsViews.get(1).getSessions().size(), is(1));
        assertThat(courtSessionsViews.get(1).getSessions().get(0).getCourtRoomId(), is(courtRoomId1));
        assertThat(courtSessionsViews.get(1).getSessions().get(0).getSessionDate(), is(sessionDate1));
    }
}