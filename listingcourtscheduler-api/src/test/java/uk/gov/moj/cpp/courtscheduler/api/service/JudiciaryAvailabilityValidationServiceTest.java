package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RecurringType;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.repository.JudiciaryAvailabilityRuleRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryAvailabilityValidationServiceTest {

    @Mock
    private JudiciaryAvailabilityRuleRepository repository;

    @Mock
    private javax.persistence.EntityManager entityManager;

    @Mock
    private uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @InjectMocks
    private JudiciaryAvailabilityService service;

    private String judiciaryId;
    private String courtHouseId;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        judiciaryId = randomUUID().toString();
        courtHouseId = randomUUID().toString();
        today = LocalDate.now();
    }

    @Test
    void shouldReturnNoErrorsForValidAddRequest() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(1), today.plusDays(31));

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenDateRangeExceeds3Years() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(1), today.plusDays(1).plusYears(4));

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("3 years")));
    }

    @Test
    void shouldReturnErrorWhenStartDateIsInPastForAdd() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.minusDays(1), today.plusDays(31));

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("future") && e.contains("creation")));
    }

    @Test
    void shouldReturnErrorWhenEndDateIsInPastForAdd() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(1), today.minusDays(1));

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("future") && e.contains("creation")));
    }

    @Test
    void shouldReturnErrorWhenUnavailabilityStartDateIsBeforeAvailabilityStartDate() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
        JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();
        unavailability.setStartDate(today.plusDays(5)); // Before availability start
        unavailability.setEndDate(today.plusDays(15));
        unavailability.setReason(UnavailabilityReason.ANNUAL_LEAVE);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unavailability") && e.contains("within")));
    }

    @Test
    void shouldReturnErrorWhenUnavailabilityEndDateIsAfterAvailabilityEndDate() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
        JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(45)); // After availability end
        unavailability.setReason(UnavailabilityReason.ANNUAL_LEAVE);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unavailability") && e.contains("within")));
    }

    @Test
    void shouldReturnErrorWhenUnavailabilitiesOverlap() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
        
        JudiciaryUnavailabilityRequest u1 = new JudiciaryUnavailabilityRequest();
        u1.setStartDate(today.plusDays(15));
        u1.setEndDate(today.plusDays(20));
        u1.setReason(UnavailabilityReason.ANNUAL_LEAVE);
        unavailabilities.add(u1);
        
        JudiciaryUnavailabilityRequest u2 = new JudiciaryUnavailabilityRequest();
        u2.setStartDate(today.plusDays(18)); // Overlaps with u1
        u2.setEndDate(today.plusDays(25));
        u2.setReason(UnavailabilityReason.SICK_LEAVE);
        unavailabilities.add(u2);
        
        request.setUnavailabilities(unavailabilities);

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Unavailabilities cannot overlap")));
    }

    @Test
    void shouldReturnErrorWhenOverlappingRuleExistsForSameJudiciary() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        request.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null)
        ));
        request.setRecurringType(RecurringType.WEEKLY);

        // Create an existing overlapping rule
        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(15), today.plusDays(35));
        existingRule.setRecurringType(RecurringType.WEEKLY);
        existingRule.setRepeatDays(Arrays.asList(
                createEntityRepeatDay(AvailabilityDayOfWeek.Monday)
        ));

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("one place at a time")));
    }

    @Test
    void shouldNotReturnErrorWhenOverlappingRuleHasDifferentRepeatDays() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        request.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null)
        ));
        request.setRecurringType(RecurringType.WEEKLY);

        // Create an existing overlapping rule with different days
        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(15), today.plusDays(35));
        existingRule.setRecurringType(RecurringType.WEEKLY);
        existingRule.setRepeatDays(Arrays.asList(
                createEntityRepeatDay(AvailabilityDayOfWeek.Tuesday) // Different day
        ));

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        // Should not error because different days don't conflict
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldReturnNoErrorsForValidUpdateRequest() {
        String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(1), today.plusDays(31));

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(1), today.plusDays(31));
        existingRule.setId(ruleId);
        when(repository.findBy(ruleId)).thenReturn(existingRule);
        when(repository.findRulesByDateRange(today.plusDays(1), today.plusDays(31), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenChangedStartDateIsInPastForUpdate() {
        String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.minusDays(5), today.plusDays(31));

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(1), today.plusDays(31));
        existingRule.setId(ruleId);
        when(repository.findBy(ruleId)).thenReturn(existingRule);

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("changed") && e.contains("future")));
    }

    @Test
    void shouldNotReturnErrorWhenUnchangedStartDateIsInPastForUpdate() {
        String ruleId = randomUUID().toString();
        LocalDate pastDate = today.minusDays(10);
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, pastDate, today.plusDays(31));

        JudiciaryAvailabilityRule existingRule = createExistingRule(pastDate, today.plusDays(31));
        existingRule.setId(ruleId);
        when(repository.findBy(ruleId)).thenReturn(existingRule);
        when(repository.findRulesByDateRange(pastDate, today.plusDays(31), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because date wasn't changed
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenChangedEndDateIsInPastForUpdate() {
        String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(1), today.minusDays(5));

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(1), today.plusDays(31));
        existingRule.setId(ruleId);
        when(repository.findBy(ruleId)).thenReturn(existingRule);

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("changed") && e.contains("future")));
    }

    @Test
    void shouldReturnErrorWhenUpdateRuleIdIsNull() {
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(null, today.plusDays(1), today.plusDays(31));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("Rule ID is required")));
    }

    @Test
    void shouldReturnErrorWhenUpdateRuleNotFound() {
        String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(1), today.plusDays(31));

        when(repository.findBy(ruleId)).thenReturn(null);

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("not found")));
    }

    @Test
    void shouldReturnErrorWhenOverlappingRuleExistsForUpdateExcludingCurrentRule() {
        String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), today.plusDays(40));
        request.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null)
        ));
        request.setRecurringType(RecurringType.WEEKLY);

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), today.plusDays(40));
        existingRule.setId(ruleId);
        
        // Create another overlapping rule
        JudiciaryAvailabilityRule otherRule = createExistingRule(today.plusDays(15), today.plusDays(35));
        otherRule.setId(randomUUID().toString());
        otherRule.setRecurringType(RecurringType.WEEKLY);
        otherRule.setRepeatDays(Arrays.asList(
                createEntityRepeatDay(AvailabilityDayOfWeek.Monday)
        ));

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule, otherRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("one place at a time")));
    }

    @Test
    void shouldNotReturnErrorWhenOnlyCurrentRuleOverlapsForUpdate() {
        String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), today.plusDays(40));
        request.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null)
        ));
        request.setRecurringType(RecurringType.WEEKLY);

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), today.plusDays(40));
        existingRule.setId(ruleId);
        existingRule.setRecurringType(RecurringType.WEEKLY);
        existingRule.setRepeatDays(Arrays.asList(
                createEntityRepeatDay(AvailabilityDayOfWeek.Monday)
        ));

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule)); // Only current rule

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because only the current rule overlaps (which is expected)
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenUnavailabilityWouldAffectAssignedSessionsForAdd() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
        JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(20));
        unavailability.setReason(UnavailabilityReason.ANNUAL_LEAVE);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        // Mock that there are assigned sessions in the unavailability date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, today.plusDays(15), today.plusDays(20)))
                .thenReturn(Arrays.asList("session1", "session2"));

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("unavailability") && 
                e.contains("assigned session")));
    }

    @Test
    void shouldNotReturnErrorWhenUnavailabilityDoesNotAffectAssignedSessionsForAdd() {
        AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
        JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(20));
        unavailability.setReason(UnavailabilityReason.ANNUAL_LEAVE);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        // Mock that there are no assigned sessions in the unavailability date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, today.plusDays(15), today.plusDays(20)))
                .thenReturn(new ArrayList<>());

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(new ArrayList<>());

        List<String> errors = service.validateAddJudiciaryAvailabilityRule(request);

        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenStartDateChangeWouldAffectAssignedSessionsForUpdate() {
        String ruleId = randomUUID().toString();
        LocalDate oldStart = today.plusDays(10);
        LocalDate newStart = today.plusDays(15); // Moving start date forward
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, newStart, today.plusDays(40));

        JudiciaryAvailabilityRule existingRule = createExistingRule(oldStart, today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        
        // Mock that there are assigned sessions in the removed date range (oldStart to newStart - 1)
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, oldStart, newStart.minusDays(1)))
                .thenReturn(Arrays.asList("session1", "session2"));

        when(repository.findRulesByDateRange(newStart, today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("start date") && 
                e.contains("assigned session")));
    }

    @Test
    void shouldReturnErrorWhenEndDateChangeWouldAffectAssignedSessionsForUpdate() {
        String ruleId = randomUUID().toString();
        LocalDate oldEnd = today.plusDays(40);
        LocalDate newEnd = today.plusDays(35); // Moving end date backward
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), newEnd);

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), oldEnd);
        existingRule.setId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        
        // Mock that there are assigned sessions in the removed date range (newEnd + 1 to oldEnd)
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, newEnd.plusDays(1), oldEnd))
                .thenReturn(Arrays.asList("session1", "session2"));

        when(repository.findRulesByDateRange(today.plusDays(10), newEnd, null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("end date") && 
                e.contains("assigned session")));
    }

    @Test
    void shouldNotReturnErrorWhenStartDateChangeDoesNotAffectAssignedSessionsForUpdate() {
        String ruleId = randomUUID().toString();
        LocalDate oldStart = today.plusDays(10);
        LocalDate newStart = today.plusDays(15); // Moving start date forward
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, newStart, today.plusDays(40));

        JudiciaryAvailabilityRule existingRule = createExistingRule(oldStart, today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        
        // Mock that there are no assigned sessions in the removed date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, oldStart, newStart.minusDays(1)))
                .thenReturn(new ArrayList<>());

        when(repository.findRulesByDateRange(newStart, today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because no sessions are affected
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldNotReturnErrorWhenEndDateChangeDoesNotAffectAssignedSessionsForUpdate() {
        String ruleId = randomUUID().toString();
        LocalDate oldEnd = today.plusDays(40);
        LocalDate newEnd = today.plusDays(35); // Moving end date backward
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), newEnd);

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), oldEnd);
        existingRule.setId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        
        // Mock that there are no assigned sessions in the removed date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, newEnd.plusDays(1), oldEnd))
                .thenReturn(new ArrayList<>());

        when(repository.findRulesByDateRange(today.plusDays(10), newEnd, null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because no sessions are affected
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldReturnErrorWhenUnavailabilityWouldAffectAssignedSessionsForUpdate() {
        String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), today.plusDays(40));
        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
        JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(20));
        unavailability.setReason(UnavailabilityReason.ANNUAL_LEAVE);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        
        // Mock that there are assigned sessions in the unavailability date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, today.plusDays(15), today.plusDays(20)))
                .thenReturn(Arrays.asList("session1", "session2"));

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("unavailability") && 
                e.contains("assigned session")));
    }

    @Test
    void shouldNotReturnErrorWhenUnavailabilityDoesNotAffectAssignedSessionsForUpdate() {
        String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), today.plusDays(40));
        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
        JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(20));
        unavailability.setReason(UnavailabilityReason.ANNUAL_LEAVE);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        
        // Mock that there are no assigned sessions in the unavailability date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, today.plusDays(15), today.plusDays(20)))
                .thenReturn(new ArrayList<>());

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because no sessions are affected
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldNotReturnErrorWhenStartDateIsMovedBackwardForUpdate() {
        String ruleId = randomUUID().toString();
        LocalDate oldStart = today.plusDays(15);
        LocalDate newStart = today.plusDays(10); // Moving start date backward (expanding range)
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, newStart, today.plusDays(40));

        JudiciaryAvailabilityRule existingRule = createExistingRule(oldStart, today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        when(repository.findRulesByDateRange(newStart, today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because moving start date backward doesn't remove any dates
        assertTrue(errors.isEmpty());
    }

    @Test
    void shouldNotReturnErrorWhenEndDateIsMovedForwardForUpdate() {
        String ruleId = randomUUID().toString();
        LocalDate oldEnd = today.plusDays(35);
        LocalDate newEnd = today.plusDays(40); // Moving end date forward (expanding range)
        UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), newEnd);

        JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), oldEnd);
        existingRule.setId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);
        when(repository.findRulesByDateRange(today.plusDays(10), newEnd, null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        List<String> errors = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because moving end date forward doesn't remove any dates
        assertTrue(errors.isEmpty());
    }

    private AddJudiciaryAvailabilityRuleRequest createValidAddRequest(LocalDate startDate, LocalDate endDate) {
        AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setRecurringType(RecurringType.WEEKLY);
        request.setSessionType(SessionType.AD);
        request.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null),
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Tuesday, null)
        ));
        return request;
    }

    private UpdateJudiciaryAvailabilityRuleRequest createValidUpdateRequest(String ruleId, LocalDate startDate, LocalDate endDate) {
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setRecurringType(RecurringType.WEEKLY);
        request.setSessionType(SessionType.AD);
        request.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Monday, null),
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.Tuesday, null)
        ));
        return request;
    }

    private JudiciaryAvailabilityRule createExistingRule(LocalDate startDate, LocalDate endDate) {
        JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
        rule.setId(randomUUID().toString());
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setFromDate(startDate);
        rule.setToDate(endDate);
        rule.setRecurringType(RecurringType.WEEKLY);
        rule.setSessionType(SessionType.AD);
        return rule;
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay createEntityRepeatDay(AvailabilityDayOfWeek dayOfWeek) {
        uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay repeatDay = 
                new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay();
        repeatDay.setDayOfWeek(dayOfWeek);
        repeatDay.setIndex(0);
        return repeatDay;
    }
}

