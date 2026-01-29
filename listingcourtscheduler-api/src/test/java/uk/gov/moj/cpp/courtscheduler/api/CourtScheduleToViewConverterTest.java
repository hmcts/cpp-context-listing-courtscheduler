package uk.gov.moj.cpp.courtscheduler.api;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import uk.gov.moj.cpp.courtscheduler.api.converter.CourtScheduleToViewConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSessionsView;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleView;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class CourtScheduleToViewConverterTest {

    @Test
    public void shouldConvert() {
        // given
        String courtRoomId1 = "courtRoomId3";
        String courtRoomId2 = "courtRoomId2";
        Integer totalBooked1 = 10;
        Integer totalBooked2 = 20;
        // and
        LocalDate sessionDate1 = LocalDate.now();
        LocalDate sessionDate2 = LocalDate.now();
        LocalDate sessionDate3 = LocalDate.now().plusDays(1);

        CourtSchedule courtSchedule1WithCourtRoom1 = random(CourtSchedule.class);
        courtSchedule1WithCourtRoom1.setCourtRoomId(courtRoomId1);
        courtSchedule1WithCourtRoom1.setCourtRoomName(courtRoomId1);
        courtSchedule1WithCourtRoom1.setSessionDate(sessionDate1);
        courtSchedule1WithCourtRoom1.setTotalBooked(totalBooked1);

        CourtSchedule courtSchedule2WithCourtRoom1 = random(CourtSchedule.class);
        courtSchedule2WithCourtRoom1.setCourtRoomId(courtRoomId2);
        courtSchedule2WithCourtRoom1.setCourtRoomName(courtRoomId2);
        courtSchedule2WithCourtRoom1.setSessionDate(sessionDate2);

        CourtSchedule courtSchedule1WithCourtRoom2 = random(CourtSchedule.class);
        courtSchedule1WithCourtRoom2.setCourtRoomId(courtRoomId2);
        courtSchedule1WithCourtRoom2.setCourtRoomName(courtRoomId2);
        courtSchedule1WithCourtRoom2.setSessionDate(sessionDate3);
        courtSchedule1WithCourtRoom2.setTotalBooked(totalBooked2);

        List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule1WithCourtRoom1, courtSchedule1WithCourtRoom2, courtSchedule2WithCourtRoom1));

        assertThat(courtSessionsViews.size(), is(2));
        assertThat(courtSessionsViews.get(0).getSessions().size(), is(2));
        assertThat(courtSessionsViews.get(0).getSessions().get(0).getSessionDate(), is(sessionDate2));
        assertThat(courtSessionsViews.get(0).getSessions().get(1).getSessionDate(), is(sessionDate3));
        assertThat(courtSessionsViews.get(0).getSessions().get(1).getTotalBooked(), is(totalBooked2));
        assertThat(courtSessionsViews.get(1).getSessions().size(), is(1));
        assertThat(courtSessionsViews.get(1).getSessions().get(0).getCourtRoomId(), is(courtRoomId1));
        assertThat(courtSessionsViews.get(1).getSessions().get(0).getSessionDate(), is(sessionDate1));
        assertThat(courtSessionsViews.get(1).getSessions().get(0).getTotalBooked(), is(totalBooked1));
    }

    @Test
    public void shouldConvertJurisdictionType() {
        // given
        String jurisdictionType = "MAGISTRATES";
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setJurisdiction(jurisdictionType);

        // when
        List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule));

        // then
        assertThat(courtSessionsViews.size(), is(1));
        List<CourtScheduleView> sessions = courtSessionsViews.get(0).getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.get(0).getJurisdiction(), is(jurisdictionType));
    }

    @Test
    public void shouldConvertJurisdictionTypeWhenNull() {
        // given
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setJurisdiction(null);

        // when
        List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule));

        // then
        assertThat(courtSessionsViews.size(), is(1));
        List<CourtScheduleView> sessions = courtSessionsViews.get(0).getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.get(0).getJurisdiction(), is((String) null));
    }

    @Test
    public void shouldConvertJurisdictionTypeForCrown() {
        // given
        String jurisdictionType = "CROWN";
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setJurisdiction(jurisdictionType);

        // when
        List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule));

        // then
        assertThat(courtSessionsViews.size(), is(1));
        List<CourtScheduleView> sessions = courtSessionsViews.get(0).getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.get(0).getJurisdiction(), is(jurisdictionType));
    }
}