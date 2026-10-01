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
    private static final String TITLE = "His Honour";
    private static final String FORENAMES = "Mark J";
    private static final String SURNAME = "Ainsworth";
    private static final String EMAIL_ADDRESS = "mark.ainsworth@ejudiciary.net";
    private static final String JUDICIARY_TYPE = "Recorder";
    private static final int SEQ_ID = 143_117;

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
        schedule.setJudiciaries(List.of(judiciary()
                .withJudiciaryId(judiciaryId)
                .withTitle(TITLE)
                .withForenames(FORENAMES)
                .withSurname(SURNAME)
                .withEmailAddress(EMAIL_ADDRESS)
                .withJudiciaryType(JUDICIARY_TYPE)
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .build()));

        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(schedule));

        assertThat(courtSessionsViews.size(), is(1));
        final List<CourtScheduleView> sessions = courtSessionsViews.getFirst().getSessions();
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getFirst().getJudiciaries().size(), is(1));
        final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary result = sessions.getFirst().getJudiciaries().getFirst();
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getTitle(), is(TITLE));
        assertThat(result.getForenames(), is(FORENAMES));
        assertThat(result.getSurname(), is(SURNAME));
        assertThat(result.getEmailAddress(), is(EMAIL_ADDRESS));
        assertThat(result.getJudiciaryType(), is(JUDICIARY_TYPE));
        assertThat(result.isBenchChairman(), is(true));
        assertThat(result.isDeputy(), is(false));
    }

    @Test
    void shouldPassThroughJudiciaryRefDataFields() {
        final CourtSchedule schedule = random(CourtSchedule.class);
        final String judiciaryId = "9f39f876-3ff6-32b5-926e-c588e36a87b8";
        final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary judiciary = judiciary()
                .withJudiciaryId(judiciaryId)
                .withTitle(TITLE)
                .withForenames(FORENAMES)
                .withSurname(SURNAME)
                .withEmailAddress(EMAIL_ADDRESS)
                .withJudiciaryType(JUDICIARY_TYPE)
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .build();
        judiciary.setSeqId(SEQ_ID);
        judiciary.setTitleJudicialPrefix("His Honour Judge");
        judiciary.setTitleJudicialPrefixWelsh("Ei Anrhydedd y Barnwr");
        judiciary.setPersonId("131172");
        judiciary.setRequestedName("HIS HONOUR JUDGE MARK AINSWORTH");
        schedule.setJudiciaries(List.of(judiciary));

        final List<CourtSessionsView> courtSessionsViews = CourtScheduleToViewConverter.getCourtSessionsViews(List.of(schedule));

        assertThat(courtSessionsViews.size(), is(1));
        final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary result =
                courtSessionsViews.getFirst().getSessions().getFirst().getJudiciaries().getFirst();
        assertThat(result.getJudiciaryId(), is(judiciaryId));
        assertThat(result.getTitle(), is(TITLE));
        assertThat(result.getForenames(), is(FORENAMES));
        assertThat(result.getSurname(), is(SURNAME));
        assertThat(result.getEmailAddress(), is(EMAIL_ADDRESS));
        assertThat(result.getJudiciaryType(), is(JUDICIARY_TYPE));
        assertThat(result.isBenchChairman(), is(true));
        assertThat(result.isDeputy(), is(false));
        assertThat(result.getSeqId(), is(SEQ_ID));
        assertThat(result.getTitleJudicialPrefix(), is("His Honour Judge"));
        assertThat(result.getTitleJudicialPrefixWelsh(), is("Ei Anrhydedd y Barnwr"));
        assertThat(result.getPersonId(), is("131172"));
        assertThat(result.getRequestedName(), is("HIS HONOUR JUDGE MARK AINSWORTH"));
    }
}