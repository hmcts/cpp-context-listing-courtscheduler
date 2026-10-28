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
    void shouldGroupByCourtRoomIdNotCourtRoomName() {
        // Two schedules with the same courtRoomName but different courtRoomIds must NOT be merged
        final String sharedCourtRoomName = "Room A";
        final String courtRoomId1 = "room-id-unique-1";
        final String courtRoomId2 = "room-id-unique-2";

        final CourtSchedule schedule1 = random(CourtSchedule.class);
        schedule1.setCourtRoomId(courtRoomId1);
        schedule1.setCourtRoomName(sharedCourtRoomName);
        schedule1.setSessionDate(LocalDate.now());

        final CourtSchedule schedule2 = random(CourtSchedule.class);
        schedule2.setCourtRoomId(courtRoomId2);
        schedule2.setCourtRoomName(sharedCourtRoomName);
        schedule2.setSessionDate(LocalDate.now().plusDays(1));

        final List<CourtSessionsView> result = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(schedule1, schedule2));

        // Must have 2 courtRoom groups, not 1 (collision on name must not happen)
        assertThat(result.size(), is(2));
        assertThat(result.get(0).getSessions().size(), is(1));
        assertThat(result.get(1).getSessions().size(), is(1));
    }

    @Test
    void shouldConvertJudiciaries() {
        final CourtSchedule schedule = random(CourtSchedule.class);
        final String judiciaryId = "9f39f876-3ff6-32b5-926e-c588e36a87b8";
        schedule.setJudiciaries(List.of(new CourtScheduleJudiciary()
                .id(judiciaryId)
                .titlePrefix("His Honour")
                .forenames("Mark J")
                .surname("Ainsworth")
                .emailAddress("mark.ainsworth@ejudiciary.net")
                .judiciaryType("Recorder")
                .isBenchChairman(true)
                .isDeputy(false)));

        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(schedule));

        assertThat(courtSessionsViews.size(), is(1));
        final List<CourtScheduleView> sessions = courtSessionsViews.getFirst().getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getFirst().getJudiciaries().size(), is(1));
        assertThat(sessions.getFirst().getJudiciaries().getFirst().getId(), is(judiciaryId));
        assertThat(sessions.getFirst().getJudiciaries().getFirst().getIsBenchChairman(), is(true));
        assertThat(sessions.getFirst().getJudiciaries().getFirst().getIsDeputy(), is(false));
    }
}