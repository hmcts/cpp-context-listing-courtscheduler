package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Judiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.List;

import jakarta.json.Json;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SearchAvailableJudiciariesServiceTest {
    private static final String ABCD = "abcd";
    private static final String COURT_SCHEDULE_IDS = "courtScheduleIds";
    private static final String DATES = "dates";
    private static final String HOUSE_1 = "house-1";
    private static final String ID1 = "id1";
    private static final String SEARCH = "search";


    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private JudiciaryAvailabilityService judiciaryAvailabilityService;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @InjectMocks
    private SearchAvailableJudiciariesService service;

    @Test
    void shouldRejectSearchShorterThanTwoCharacters() {
        final jakarta.json.JsonObject payload = Json.createObjectBuilder().add(SEARCH, "x").build();
        assertThrows(ResponseStatusException.class, () -> service.search(payload));
    }

    @Test
    void shouldRejectDatesAndCourtScheduleIdsTogether() {
        final jakarta.json.JsonObject payload = Json.createObjectBuilder()
                .add(SEARCH, "ab")
                .add(DATES, "2026-01-05")
                .add("courtHouseId", HOUSE_1)
                .add(COURT_SCHEDULE_IDS, ID1)
                .build();
        assertThrows(ResponseStatusException.class, () -> service.search(payload));
    }

    @Test
    void shouldRequireScopeWhenAvailabilityApplied() {
        final jakarta.json.JsonObject payload = Json.createObjectBuilder().add(SEARCH, "ab").build();
        assertThrows(ResponseStatusException.class, () -> service.search(payload));
    }

    @Test
    void ignoreAvailabilitySkipsFiltering() {
        final Judiciary j = new Judiciary().id("11111111-1111-1111-1111-111111111111").surname("Smith");
        when(referenceDataService.searchJudiciaries(eq("abcd"), eq(""), eq(50), eq(true)))
                .thenReturn(List.of(j));

        final List<Judiciary> result = service.search(Json.createObjectBuilder()
                .add(SEARCH, ABCD)
                .add("ignoreAvailability", true)
                .build());

        assertThat(result, is(List.of(j)));
        verifyNoInteractions(judiciaryAvailabilityService);
        verifyNoInteractions(courtScheduleRepository);
    }

    @Test
    void shouldUseDefaultLimitWhenLimitInvalid() {
        when(referenceDataService.searchJudiciaries(eq(ABCD), eq(""), eq(50), eq(true)))
                .thenReturn(List.of());

        service.search(Json.createObjectBuilder()
                .add(SEARCH, ABCD)
                .add("ignoreAvailability", true)
                .add("limit", "oops")
                .build());

        verify(referenceDataService).searchJudiciaries(eq(ABCD), eq(""), eq(50), eq(true));
    }

    @Test
    void shouldRequireCourtHouseIdForDatesMode() {
        final jakarta.json.JsonObject payload = Json.createObjectBuilder()
                .add(SEARCH, "ab")
                .add(DATES, "2026-01-05")
                .build();
        assertThrows(ResponseStatusException.class, () -> service.search(payload));
    }

    @Test
    void shouldThrowWhenDatesInvalid() {
        final jakarta.json.JsonObject payload = Json.createObjectBuilder()
                .add(SEARCH, "ab")
                .add(DATES, "invalid-date")
                .add("courtHouseId", HOUSE_1)
                .build();
        when(referenceDataService.searchJudiciaries(any(), any(), any(Integer.class), eq(true)))
                .thenReturn(List.of());

        assertThrows(RuntimeException.class, () -> service.search(payload));
    }

    @Test
    void shouldFilterByDatesModeUsingAvailabilityServiceIds() {
        final Judiciary a = new Judiciary().id("a").surname("A");
        final Judiciary b = new Judiciary().id("b").surname("B");

        when(referenceDataService.searchJudiciaries(eq(ABCD), eq(""), eq(50), eq(true)))
                .thenReturn(List.of(a, b));
        when(judiciaryAvailabilityService.findAvailableJudiciaryIdsFromList(any(), any(), any(), eq(HOUSE_1), any(), eq(false)))
                .thenReturn(List.of("b"));

        final List<Judiciary> result = service.search(Json.createObjectBuilder()
                .add(SEARCH, ABCD)
                .add(DATES, "2026-01-05,2026-01-06")
                .add("courtHouseId", HOUSE_1)
                .build());

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getId(), is("b"));
    }

    @Test
    void shouldThrowWhenCourtScheduleIdsNotFound() {
        final jakarta.json.JsonObject payload = Json.createObjectBuilder()
                .add(SEARCH, ABCD)
                .add(COURT_SCHEDULE_IDS, ID1)
                .build();
        when(referenceDataService.searchJudiciaries(any(), any(), any(Integer.class), eq(true)))
                .thenReturn(List.of(new Judiciary().id("a").surname("A")));
        when(courtScheduleRepository.findByCourtScheduleIds(List.of("id1"))).thenReturn(List.of());

        assertThrows(ResponseStatusException.class, () -> service.search(payload));
    }

    @Test
    void shouldThrowWhenCourtScheduleIdsFromMixedCourthouses() {
        final jakarta.json.JsonObject payload = Json.createObjectBuilder()
                .add(SEARCH, ABCD)
                .add(COURT_SCHEDULE_IDS, "id1,id2")
                .build();
        final CourtSchedule s1 = mock(CourtSchedule.class);
        final CourtSchedule s2 = mock(CourtSchedule.class);
        when(s1.getCourtScheduleId()).thenReturn(ID1);
        when(s2.getCourtScheduleId()).thenReturn("id2");
        when(s1.getCourtHouseId()).thenReturn(HOUSE_1);
        when(s2.getCourtHouseId()).thenReturn("house-2");

        when(referenceDataService.searchJudiciaries(any(), any(), any(Integer.class), eq(true)))
                .thenReturn(List.of(new Judiciary().id("a").surname("A")));
        when(courtScheduleRepository.findByCourtScheduleIds(List.of("id1", "id2"))).thenReturn(List.of(s1, s2));

        assertThrows(ResponseStatusException.class, () -> service.search(payload));
    }

    @Test
    void shouldFilterByCourtScheduleIdsMode() {
        final CourtSchedule s1 = mock(CourtSchedule.class);
        when(s1.getCourtScheduleId()).thenReturn(ID1);
        when(s1.getCourtHouseId()).thenReturn(HOUSE_1);
        when(s1.getSessionDate()).thenReturn(LocalDate.of(2026, 1, 5));
        when(s1.getCourtSession()).thenReturn("AM");

        final Judiciary a = new Judiciary().id("a").surname("A");
        final Judiciary b = new Judiciary().id("b").surname("B");

        when(referenceDataService.searchJudiciaries(eq(ABCD), eq(""), eq(50), eq(true)))
                .thenReturn(List.of(a, b));
        when(courtScheduleRepository.findByCourtScheduleIds(List.of(ID1))).thenReturn(List.of(s1));
        when(judiciaryAvailabilityService.findAvailableJudiciaryIdsFromList(any(), any(), any(), eq(HOUSE_1), any(), eq(true)))
                .thenReturn(List.of("a"));

        final List<Judiciary> result = service.search(Json.createObjectBuilder()
                .add(SEARCH, ABCD)
                .add(COURT_SCHEDULE_IDS, ID1)
                .build());

        assertThat(result.size(), is(1));
        assertThat(result.get(0).getId(), is("a"));
    }
}
