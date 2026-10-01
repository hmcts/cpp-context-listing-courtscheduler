package uk.gov.moj.cpp.courtscheduler.api;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import uk.gov.moj.cpp.courtscheduler.api.converter.CourtScheduleToViewConverter;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSessionsView;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleView;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class CourtScheduleToViewConverterTest {

    @Test
    void shouldConvert() {
        // given
        final String courtRoomId1 = "courtRoomId3";
        final String courtRoomId2 = "courtRoomId2";
        final Integer totalBooked1 = 10;
        final Integer totalBooked2 = 20;
        // and
        final LocalDate sessionDate1 = LocalDate.now();
        final LocalDate sessionDate2 = LocalDate.now();
        final LocalDate sessionDate3 = LocalDate.now().plusDays(1);

        final CourtSchedule courtSchedule1WithCourtRoom1 = random(CourtSchedule.class);
        courtSchedule1WithCourtRoom1.setCourtRoomId(courtRoomId1);
        courtSchedule1WithCourtRoom1.setCourtRoomName(courtRoomId1);
        courtSchedule1WithCourtRoom1.setSessionDate(sessionDate1);
        courtSchedule1WithCourtRoom1.setTotalBooked(totalBooked1);

        final CourtSchedule courtSchedule2WithCourtRoom1 = random(CourtSchedule.class);
        courtSchedule2WithCourtRoom1.setCourtRoomId(courtRoomId2);
        courtSchedule2WithCourtRoom1.setCourtRoomName(courtRoomId2);
        courtSchedule2WithCourtRoom1.setSessionDate(sessionDate2);

        final CourtSchedule courtSchedule1WithCourtRoom2 = random(CourtSchedule.class);
        courtSchedule1WithCourtRoom2.setCourtRoomId(courtRoomId2);
        courtSchedule1WithCourtRoom2.setCourtRoomName(courtRoomId2);
        courtSchedule1WithCourtRoom2.setSessionDate(sessionDate3);
        courtSchedule1WithCourtRoom2.setTotalBooked(totalBooked2);

        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule1WithCourtRoom1, courtSchedule1WithCourtRoom2, courtSchedule2WithCourtRoom1));

        assertThat(courtSessionsViews.size(), is(2));
        assertThat(courtSessionsViews.getFirst().getSessions().size(), is(2));
        assertThat(courtSessionsViews.getFirst().getSessions().getFirst().getSessionDate(), is(sessionDate2));
        assertThat(courtSessionsViews.getFirst().getSessions().get(1).getSessionDate(), is(sessionDate3));
        assertThat(courtSessionsViews.getFirst().getSessions().get(1).getTotalBooked(), is(totalBooked2));
        assertThat(courtSessionsViews.get(1).getSessions().size(), is(1));
        assertThat(courtSessionsViews.get(1).getSessions().getFirst().getCourtRoomId(), is(courtRoomId1));
        assertThat(courtSessionsViews.get(1).getSessions().getFirst().getSessionDate(), is(sessionDate1));
        assertThat(courtSessionsViews.get(1).getSessions().getFirst().getTotalBooked(), is(totalBooked1));
    }

    @Test
    void shouldConvertJurisdictionType() {
        // given
        final String jurisdictionType = "MAGISTRATES";
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setJurisdiction(jurisdictionType);

        // when
        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule));

        // then
        assertThat(courtSessionsViews.size(), is(1));
        final List<CourtScheduleView> sessions = courtSessionsViews.getFirst().getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getFirst().getJurisdiction(), is(jurisdictionType));
    }

    @Test
    void shouldConvertJurisdictionTypeWhenNull() {
        // given
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setJurisdiction(null);

        // when
        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule));

        // then
        assertThat(courtSessionsViews.size(), is(1));
        final List<CourtScheduleView> sessions = courtSessionsViews.getFirst().getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getFirst().getJurisdiction(), is((String) null));
    }

    @Test
    void shouldConvertJurisdictionTypeForCrown() {
        // given
        final String jurisdictionType = "CROWN";
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setJurisdiction(jurisdictionType);

        // when
        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(courtSchedule));

        // then
        assertThat(courtSessionsViews.size(), is(1));
        final List<CourtScheduleView> sessions = courtSessionsViews.getFirst().getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getFirst().getJurisdiction(), is(jurisdictionType));
    }

    @Test
    void shouldConvertJudiciaries() {
        final CourtSchedule schedule = random(CourtSchedule.class);
        final String judiciaryId = "9f39f876-3ff6-32b5-926e-c588e36a87b8";
        schedule.setJudiciaries(List.of(new CourtScheduleJudiciary()
                .judiciaryId(judiciaryId)
                .title("His Honour")
                .forenames("Mark J")
                .surname("Ainsworth")
                .emailAddress("mark.ainsworth@ejudiciary.net")
                .judiciaryType("Recorder")
                .benchChairman(true)
                .deputy(false)));

        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(schedule));

        assertThat(courtSessionsViews.size(), is(1));
        final List<CourtScheduleView> sessions = courtSessionsViews.getFirst().getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getFirst().getJudiciaries().size(), is(1));
        assertThat(sessions.getFirst().getJudiciaries().getFirst().getJudiciaryId(), is(judiciaryId));
        assertThat(sessions.getFirst().getJudiciaries().getFirst().getBenchChairman(), is(true));
        assertThat(sessions.getFirst().getJudiciaries().getFirst().getDeputy(), is(false));
    }
}