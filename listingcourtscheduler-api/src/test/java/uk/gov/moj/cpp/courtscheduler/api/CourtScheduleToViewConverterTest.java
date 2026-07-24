package uk.gov.moj.cpp.courtscheduler.api;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary;

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

    @Test
    public void shouldGroupByCourtRoomIdNotCourtRoomName() {
        // Two schedules with the same courtRoomName but different courtRoomIds must NOT be merged
        String sharedCourtRoomName = "Room A";
        String courtRoomId1 = "room-id-unique-1";
        String courtRoomId2 = "room-id-unique-2";

        CourtSchedule schedule1 = random(CourtSchedule.class);
        schedule1.setCourtRoomId(courtRoomId1);
        schedule1.setCourtRoomName(sharedCourtRoomName);
        schedule1.setSessionDate(LocalDate.now());

        CourtSchedule schedule2 = random(CourtSchedule.class);
        schedule2.setCourtRoomId(courtRoomId2);
        schedule2.setCourtRoomName(sharedCourtRoomName);
        schedule2.setSessionDate(LocalDate.now().plusDays(1));

        List<CourtSessionsView> result = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(schedule1, schedule2));

        // Must have 2 courtRoom groups, not 1 (collision on name must not happen)
        assertThat(result.size(), is(2));
        assertThat(result.get(0).getSessions().size(), is(1));
        assertThat(result.get(1).getSessions().size(), is(1));
    }

    @Test
    void shouldConvertJudiciaries() {
        final CourtSchedule schedule = random(CourtSchedule.class);
        final String judiciaryId = "9f39f876-3ff6-32b5-926e-c588e36a87b8";
        schedule.setJudiciaries(List.of(judiciary()
                .withJudiciaryId(judiciaryId)
                .withTitle("His Honour")
                .withForenames("Mark J")
                .withSurname("Ainsworth")
                .withEmailAddress("mark.ainsworth@ejudiciary.net")
                .withJudiciaryType("Recorder")
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .build()));

        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(schedule));

        assertThat(courtSessionsViews.size(), is(1));
        final List<CourtScheduleView> sessions = courtSessionsViews.get(0).getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.get(0).getJudiciaries().size(), is(1));
        final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary result = sessions.get(0).getJudiciaries().get(0);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getTitle(), is("His Honour"));
        assertThat(result.getForenames(), is("Mark J"));
        assertThat(result.getSurname(), is("Ainsworth"));
        assertThat(result.getEmailAddress(), is("mark.ainsworth@ejudiciary.net"));
        assertThat(result.getJudiciaryType(), is("Recorder"));
        assertThat(result.getBenchChairman(), is(true));
        assertThat(result.getDeputy(), is(false));
    }

    @Test
    void shouldPassThroughJudiciaryRefDataFields() {
        final CourtSchedule schedule = random(CourtSchedule.class);
        final String judiciaryId = "9f39f876-3ff6-32b5-926e-c588e36a87b8";
        final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary judiciary = judiciary()
                .withJudiciaryId(judiciaryId)
                .withTitle("His Honour")
                .withForenames("Mark J")
                .withSurname("Ainsworth")
                .withEmailAddress("mark.ainsworth@ejudiciary.net")
                .withJudiciaryType("Recorder")
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .build();
        judiciary.setSeqId(143117);
        judiciary.setTitleJudicialPrefix("His Honour Judge");
        judiciary.setTitleJudicialPrefixWelsh("Ei Anrhydedd y Barnwr");
        judiciary.setPersonId("131172");
        judiciary.setRequestedName("HIS HONOUR JUDGE MARK AINSWORTH");
        schedule.setJudiciaries(List.of(judiciary));

        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(schedule));

        assertThat(courtSessionsViews.size(), is(1));
        final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary result =
                courtSessionsViews.get(0).getSessions().get(0).getJudiciaries().get(0);
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getTitle(), is("His Honour"));
        assertThat(result.getForenames(), is("Mark J"));
        assertThat(result.getSurname(), is("Ainsworth"));
        assertThat(result.getEmailAddress(), is("mark.ainsworth@ejudiciary.net"));
        assertThat(result.getJudiciaryType(), is("Recorder"));
        assertThat(result.getBenchChairman(), is(true));
        assertThat(result.getDeputy(), is(false));
        assertThat(result.getSeqId(), is(143117));
        assertThat(result.getTitleJudicialPrefix(), is("His Honour Judge"));
        assertThat(result.getTitleJudicialPrefixWelsh(), is("Ei Anrhydedd y Barnwr"));
        assertThat(result.getPersonId(), is("131172"));
        assertThat(result.getRequestedName(), is("HIS HONOUR JUDGE MARK AINSWORTH"));
    }
}