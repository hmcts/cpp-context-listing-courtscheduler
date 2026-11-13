package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.openapi.model.BusinessType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.OrganisationUnit;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionsServiceWeekCommencingTest {

    private static final String BUSINESS_TYPE = "TRL";
    private static final String FIRST_MONDAY = "2027-01-04";
    private static final String COURT_ROOM_ID = randomUUID().toString();
    private static final String COURT_CENTRE_ID = randomUUID().toString();

    @Mock
    private CourtScheduleRepository courtScheduleRepository;
    @Mock
    private ReferenceDataCache referenceDataCache;
    @InjectMocks
    private SessionsService sessionsService;
    @Captor
    private ArgumentCaptor<List<CourtSchedule>> savedCaptor;

    @Test
    void shouldPersistOneDraftAllDayWeekCommencingRowPerMondayInTheRange() {
        givenDurationBasedBusinessTypeAndCrownCourtRoom();
        when(referenceDataCache.getOrganisationUnit(COURT_CENTRE_ID))
                .thenReturn(Optional.of(new OrganisationUnit().defaultStartTime("10:30:00")));

        // Wednesday 6 Jan 2027 to Monday 1 Feb 2027 -> Mondays 11, 18, 25 Jan and 1 Feb (end inclusive)
        sessionsService.create(weekCommencingRequest(true, "2027-01-06", "2027-02-01"));

        verify(courtScheduleRepository).saveCourtSchedules(savedCaptor.capture());
        final List<CourtSchedule> saved = savedCaptor.getValue();
        assertThat(saved.stream().map(CourtSchedule::getSessionDate).toList(), contains(
                LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 18), LocalDate.of(2027, 1, 25), LocalDate.of(2027, 2, 1)));
        assertThat(saved.stream().map(CourtSchedule::isWeekCommencing).toList(), everyItem(is(true)));
        assertThat(saved.stream().map(CourtSchedule::isDraft).toList(), everyItem(is(true)));
        assertThat(saved.stream().map(CourtSchedule::getCourtSession).toList(), everyItem(is("AD")));
        assertThat(saved.stream().map(CourtSchedule::getJurisdiction).toList(), everyItem(is("CROWN")));
        assertThat(saved.stream().map(CourtSchedule::getCourtRoomId).toList(), everyItem(is(COURT_ROOM_ID)));
        assertThat(saved.stream().map(CourtSchedule::getMaxDuration).toList(), everyItem(is(1800)));
        assertThat(saved.stream().map(CourtSchedule::getAvailableDuration).toList(), everyItem(is(1800)));
        // court centre default start time from reference data (GMT in January)
        assertThat(saved.getFirst().getSessionStartTime(), is(Instant.parse("2027-01-11T10:30:00Z")));
    }

    @Test
    void shouldPersistOrdinarySessionsAsNotWeekCommencing() {
        givenDurationBasedBusinessTypeAndCrownCourtRoom();

        sessionsService.create(weekCommencingRequest(null, FIRST_MONDAY, FIRST_MONDAY));

        verify(courtScheduleRepository).saveCourtSchedules(savedCaptor.capture());
        assertThat(savedCaptor.getValue().stream().map(CourtSchedule::isWeekCommencing).toList(), contains(false));
    }

    @Test
    void shouldReportDuplicateWhenAMondayAlreadyHasADraftWeekCommencingSession() {
        final LocalDate start = LocalDate.of(2027, 1, 4);
        final LocalDate end = LocalDate.of(2027, 1, 25);
        final CourtSchedule existing = new CourtSchedule();
        existing.setSessionDate(LocalDate.of(2027, 1, 18));
        when(courtScheduleRepository.findActiveWeekCommencingSessions(COURT_CENTRE_ID, BUSINESS_TYPE, start, end))
                .thenReturn(List.of(existing));

        final JsonObject result = sessionsService.validateWeekCommencingIntegrity(COURT_CENTRE_ID, BUSINESS_TYPE, start, end);

        assertThat(result.getString("errorMessage"), is(ErrorMessages.DUPLICATE_SESSIONS));
    }

    @Test
    void shouldPassWhenNoMondayHasAWeekCommencingSession() {
        when(courtScheduleRepository.findActiveWeekCommencingSessions(anyString(), anyString(), any(), any()))
                .thenReturn(List.of());

        final JsonObject result = sessionsService.validateWeekCommencingIntegrity(
                COURT_CENTRE_ID, BUSINESS_TYPE, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 25));

        assertThat(result.isEmpty(), is(true));
        assertThat(result.get("errorMessage"), is(nullValue()));
    }

    private void givenDurationBasedBusinessTypeAndCrownCourtRoom() {
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(BUSINESS_TYPE)))
                .thenReturn(Optional.of(new BusinessType().typeCode(BUSINESS_TYPE).slot(false).duration(true)));
        when(referenceDataCache.getCpCourtRoomByCourtRoomIdAndCourtCentreId(COURT_ROOM_ID, COURT_CENTRE_ID))
                .thenReturn(Optional.of(new CourtRoom().id(COURT_ROOM_ID).oucodeUUID(COURT_CENTRE_ID)));
    }

    private static CreateSessionRequestParam weekCommencingRequest(final Boolean isWeekCommencing, final String start, final String end) {
        final Session session = new Session()
                .courtCentreId(COURT_CENTRE_ID)
                .courtRoomId(COURT_ROOM_ID)
                .jurisdiction("CROWN")
                .sessionType("AD")
                .businessType(BUSINESS_TYPE)
                .duration(1800)
                .allDaySplit(false)
                .maxDurationForMorning(0)
                .maxDurationForAfternoon(0)
                .panel("ADULT")
                .repeatDays(List.of("Monday"))
                .isOverbookingAllowed(false)
                .isDraft(true)
                .isWeekCommencing(isWeekCommencing);
        return new CreateSessionRequestParam()
                .sessions(List.of(session))
                .repeatPattern(new RepeatPattern().startDate(start).endDate(end).repeatFor(1).frequency("EVERY_WEEK"));
    }
}
