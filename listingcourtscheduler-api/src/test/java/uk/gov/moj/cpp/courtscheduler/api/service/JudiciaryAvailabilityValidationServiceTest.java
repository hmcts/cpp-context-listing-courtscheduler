package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.openapi.model.JudiciaryUnavailability;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateJudiciaryAvailabilityRuleRequest;
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
    private static final String ANNUAL_LEAVE_2 = "ANNUAL_LEAVE";
    private static final String MONDAY_2 = "Monday";

    private static final String SESSION1 = "session1";
    private static final String SESSION2 = "session2";


    @Mock
    private JudiciaryAvailabilityRuleRepository repository;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

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
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(1), today.plusDays(31));

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, nullValue());
    }

    @Test
    void shouldReturnErrorWhenDateRangeExceeds3Years() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(1), today.plusDays(1).plusYears(4));

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("The date range must be 3 years or less"));
    }

    @Test
    void shouldReturnErrorWhenStartDateIsInPastForAdd() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.minusDays(1), today.plusDays(31));

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("The start date must be in the future"));
    }

    @Test
    void shouldReturnErrorWhenEndDateIsInPastForAdd() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(1), today.minusDays(1));

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("The end date must be in the future"));
    }

    @Test
    void shouldReturnErrorWhenUnavailabilityStartDateIsBeforeAvailabilityStartDate() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
        final JudiciaryUnavailability unavailability = new JudiciaryUnavailability();
        unavailability.setStartDate(today.plusDays(5)); // Before availability start
        unavailability.setEndDate(today.plusDays(15));
        unavailability.setReason(ANNUAL_LEAVE_2);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is(String.format("Unavailability %s start date must be between %s and %s", 1, request.getStartDate(), request.getEndDate())));
    }

    @Test
    void shouldReturnErrorWhenUnavailabilityEndDateIsAfterAvailabilityEndDate() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
        final JudiciaryUnavailability unavailability = new JudiciaryUnavailability();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(45)); // After availability end
        unavailability.setReason(ANNUAL_LEAVE_2);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is(String.format("Unavailability %s end date must be between %s and %s", 1, request.getStartDate(), request.getEndDate())));
    }

    @Test
    void shouldReturnErrorWhenUnavailabilitiesOverlap() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
        
        final JudiciaryUnavailability u1 = new JudiciaryUnavailability();
        u1.setStartDate(today.plusDays(15));
        u1.setEndDate(today.plusDays(20));
        u1.setReason(ANNUAL_LEAVE_2);
        unavailabilities.add(u1);
        
        final JudiciaryUnavailability u2 = new JudiciaryUnavailability();
        u2.setStartDate(today.plusDays(18)); // Overlaps with u1
        u2.setEndDate(today.plusDays(25));
        u2.setReason("SICK_LEAVE");
        unavailabilities.add(u2);
        
        request.setUnavailabilities(unavailabilities);

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("Unavailability dates cannot overlap"));
    }

    @Test
    void shouldReturnErrorWhenOverlappingRuleExistsForSameJudiciary() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        request.setRepeatDays(Arrays.asList(MONDAY_2));

        // Create an existing overlapping rule
        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(15), today.plusDays(35));
        existingRule.setRepeatDays(Arrays.asList(
                createEntityRepeatDay(AvailabilityDayOfWeek.MONDAY)
        ));

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("The judiciary is already assigned during these dates"));
    }

    @Test
    void shouldReturnErrorWhenOverlappingRuleHasDifferentRepeatDays() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        request.setRepeatDays(Arrays.asList(MONDAY_2));

        // Create an existing overlapping rule with different days
        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(15), today.plusDays(35));
        existingRule.setRepeatDays(Arrays.asList(
                createEntityRepeatDay(AvailabilityDayOfWeek.TUESDAY) // Different day
        ));

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        // Should not error because different days don't conflict
        assertThat(error, is("The judiciary is already assigned during these dates"));
    }

    @Test
    void shouldReturnNoErrorsForValidUpdateRequest() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(1), today.plusDays(31));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(1), today.plusDays(31));
        existingRule.setId(ruleId);
        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        when(repository.findRulesByDateRange(today.plusDays(1), today.plusDays(31), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, nullValue());
    }

    @Test
    void shouldReturnErrorWhenChangedStartDateIsInPastForUpdate() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.minusDays(5), today.plusDays(31));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(1), today.plusDays(31));
        existingRule.setId(ruleId);
        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("The new start date must be in the future"));
    }

    @Test
    void shouldNotReturnErrorWhenUnchangedStartDateIsInPastForUpdate() {
        final String ruleId = randomUUID().toString();
        final LocalDate pastDate = today.minusDays(10);
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, pastDate, today.plusDays(31));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(pastDate, today.plusDays(31));
        existingRule.setId(ruleId);
        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        when(repository.findRulesByDateRange(pastDate, today.plusDays(31), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because date wasn't changed
        assertThat(error, nullValue());
    }

    @Test
    void shouldReturnErrorWhenChangedEndDateIsInPastForUpdate() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(1), today.minusDays(5));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(1), today.plusDays(31));
        existingRule.setId(ruleId);
        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("The new end date must be in the future"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRuleIdIsNull() {
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(null, today.plusDays(1), today.plusDays(31));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("Rule ID is required for update"));
    }

    @Test
    void shouldReturnErrorWhenUpdateRuleNotFound() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(1), today.plusDays(31));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("Judicial itinerary does not exist."));
    }

    @Test
    void shouldReturnErrorWhenOverlappingRuleExistsForUpdateExcludingCurrentRule() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), today.plusDays(40));
        request.setRepeatDays(Arrays.asList(MONDAY_2));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), today.plusDays(40));
        existingRule.setId(ruleId);
        
        // Create another overlapping rule
        final JudiciaryAvailabilityRule otherRule = createExistingRule(today.plusDays(15), today.plusDays(35));
        otherRule.setId(randomUUID().toString());
        otherRule.setRepeatDays(Arrays.asList(
                createEntityRepeatDay(AvailabilityDayOfWeek.MONDAY)
        ));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule, otherRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("The judiciary is already assigned during these dates"));
    }

    @Test
    void shouldNotReturnErrorWhenOnlyCurrentRuleOverlapsForUpdate() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), today.plusDays(40));
        request.setRepeatDays(Arrays.asList(MONDAY_2));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), today.plusDays(40));
        existingRule.setId(ruleId);
        existingRule.setRepeatDays(Arrays.asList(
                createEntityRepeatDay(AvailabilityDayOfWeek.MONDAY)
        ));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule)); // Only current rule

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because only the current rule overlaps (which is expected)
        assertThat(error, nullValue());
    }

    @Test
    void shouldReturnErrorWhenUnavailabilityWouldAffectAssignedSessionsForAdd() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
        final JudiciaryUnavailability unavailability = new JudiciaryUnavailability();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(20));
        unavailability.setReason(ANNUAL_LEAVE_2);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        // Mock that there are assigned sessions in the unavailability date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, today.plusDays(15), today.plusDays(20)))
                .thenReturn(Arrays.asList(SESSION1, SESSION2));

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is(String.format("Adding unavailability from %s to %s would affect %s already assigned session(s). Review the assigned sessions before you continue", 
                today.plusDays(15), today.plusDays(20), 2)));
    }

    @Test
    void shouldNotReturnErrorWhenUnavailabilityDoesNotAffectAssignedSessionsForAdd() {
        final AddJudiciaryAvailabilityRuleRequest request = createValidAddRequest(today.plusDays(10), today.plusDays(40));
        final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
        final JudiciaryUnavailability unavailability = new JudiciaryUnavailability();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(20));
        unavailability.setReason(ANNUAL_LEAVE_2);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        // Mock that there are no assigned sessions in the unavailability date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, today.plusDays(15), today.plusDays(20)))
                .thenReturn(new ArrayList<>());

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(new ArrayList<>());

        final String error = service.validateAddJudiciaryAvailabilityRule(request);

        assertThat(error, nullValue());
    }

    @Test
    void shouldReturnErrorWhenStartDateChangeWouldAffectAssignedSessionsForUpdate() {
        final String ruleId = randomUUID().toString();
        final LocalDate oldStart = today.plusDays(10);
        final LocalDate newStart = today.plusDays(15); // Moving start date forward
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, newStart, today.plusDays(40));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(oldStart, today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        
        // Mock that there are assigned sessions in the removed date range (oldStart to newStart - 1)
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, oldStart, newStart.minusDays(1)))
                .thenReturn(Arrays.asList(SESSION1, SESSION2));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is(String.format("Changing the start date affects %s sessions already assigned between %s and %s. Review these sessions before you continue.", 
                2, oldStart, newStart)));
    }

    @Test
    void shouldReturnErrorWhenEndDateChangeWouldAffectAssignedSessionsForUpdate() {
        final String ruleId = randomUUID().toString();
        final LocalDate oldEnd = today.plusDays(40);
        final LocalDate newEnd = today.plusDays(35); // Moving end date backward
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), newEnd);

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), oldEnd);
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        
        // Mock that there are assigned sessions in the removed date range (newEnd + 1 to oldEnd)
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, newEnd.plusDays(1), oldEnd))
                .thenReturn(Arrays.asList(SESSION1, SESSION2));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is("Changing end date from " + oldEnd + " to " + newEnd + 
                " would affect 2 already assigned session(s) in the removed date range. Please review the assigned sessions before proceeding."));
    }

    @Test
    void shouldNotReturnErrorWhenStartDateChangeDoesNotAffectAssignedSessionsForUpdate() {
        final String ruleId = randomUUID().toString();
        final LocalDate oldStart = today.plusDays(10);
        final LocalDate newStart = today.plusDays(15); // Moving start date forward
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, newStart, today.plusDays(40));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(oldStart, today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        
        // Mock that there are no assigned sessions in the removed date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, oldStart, newStart.minusDays(1)))
                .thenReturn(new ArrayList<>());

        when(repository.findRulesByDateRange(newStart, today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because no sessions are affected
        assertThat(error, nullValue());
    }

    @Test
    void shouldNotReturnErrorWhenEndDateChangeDoesNotAffectAssignedSessionsForUpdate() {
        final String ruleId = randomUUID().toString();
        final LocalDate oldEnd = today.plusDays(40);
        final LocalDate newEnd = today.plusDays(35); // Moving end date backward
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), newEnd);

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), oldEnd);
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        
        // Mock that there are no assigned sessions in the removed date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, newEnd.plusDays(1), oldEnd))
                .thenReturn(new ArrayList<>());

        when(repository.findRulesByDateRange(today.plusDays(10), newEnd, null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because no sessions are affected
        assertThat(error, nullValue());
    }

    @Test
    void shouldReturnErrorWhenUnavailabilityWouldAffectAssignedSessionsForUpdate() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), today.plusDays(40));
        final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
        final JudiciaryUnavailability unavailability = new JudiciaryUnavailability();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(20));
        unavailability.setReason(ANNUAL_LEAVE_2);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        
        // Mock that there are assigned sessions in the unavailability date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, today.plusDays(15), today.plusDays(20)))
                .thenReturn(Arrays.asList(SESSION1, SESSION2));

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        assertThat(error, notNullValue());
        assertThat(error, is(String.format("Adding unavailability from %s to %s would affect %s already assigned session(s). Review the assigned sessions before you continue", 
                today.plusDays(15), today.plusDays(20), 2)));
    }

    @Test
    void shouldNotReturnErrorWhenUnavailabilityDoesNotAffectAssignedSessionsForUpdate() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), today.plusDays(40));
        final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
        final JudiciaryUnavailability unavailability = new JudiciaryUnavailability();
        unavailability.setStartDate(today.plusDays(15));
        unavailability.setEndDate(today.plusDays(20));
        unavailability.setReason(ANNUAL_LEAVE_2);
        unavailabilities.add(unavailability);
        request.setUnavailabilities(unavailabilities);

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        
        // Mock that there are no assigned sessions in the unavailability date range
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryAndDateRange(
                judiciaryId, today.plusDays(15), today.plusDays(20)))
                .thenReturn(new ArrayList<>());

        when(repository.findRulesByDateRange(today.plusDays(10), today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because no sessions are affected
        assertThat(error, nullValue());
    }

    @Test
    void shouldNotReturnErrorWhenStartDateIsMovedBackwardForUpdate() {
        final String ruleId = randomUUID().toString();
        final LocalDate oldStart = today.plusDays(15);
        final LocalDate newStart = today.plusDays(10); // Moving start date backward (expanding range)
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, newStart, today.plusDays(40));

        final JudiciaryAvailabilityRule existingRule = createExistingRule(oldStart, today.plusDays(40));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        when(repository.findRulesByDateRange(newStart, today.plusDays(40), null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because moving start date backward doesn't remove any dates
        assertThat(error, nullValue());
    }

    @Test
    void shouldNotReturnErrorWhenEndDateIsMovedForwardForUpdate() {
        final String ruleId = randomUUID().toString();
        final LocalDate oldEnd = today.plusDays(35);
        final LocalDate newEnd = today.plusDays(40); // Moving end date forward (expanding range)
        final UpdateJudiciaryAvailabilityRuleRequest request = createValidUpdateRequest(ruleId, today.plusDays(10), newEnd);

        final JudiciaryAvailabilityRule existingRule = createExistingRule(today.plusDays(10), oldEnd);
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));
        when(repository.findRulesByDateRange(today.plusDays(10), newEnd, null, judiciaryId))
                .thenReturn(Arrays.asList(existingRule));

        final String error = service.validateUpdateJudiciaryAvailabilityRule(request);

        // Should not error because moving end date forward doesn't remove any dates
        assertThat(error, nullValue());
    }

    private AddJudiciaryAvailabilityRuleRequest createValidAddRequest(final LocalDate startDate, final LocalDate endDate) {
        final AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setSessionType("AD");
        request.setRepeatDays(Arrays.asList(MONDAY_2, "Tuesday"));
        return request;
    }

    private UpdateJudiciaryAvailabilityRuleRequest createValidUpdateRequest(final String ruleId, final LocalDate startDate, final LocalDate endDate) {
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setSessionType("AD");
        request.setRepeatDays(Arrays.asList(MONDAY_2, "Tuesday"));
        return request;
    }

    private JudiciaryAvailabilityRule createExistingRule(final LocalDate startDate, final LocalDate endDate) {
        final JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
        rule.setId(randomUUID().toString());
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setFromDate(startDate);
        rule.setToDate(endDate);
        rule.setSessionType(SessionType.AD);
        return rule;
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay createEntityRepeatDay(final AvailabilityDayOfWeek dayOfWeek) {
        return new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay(dayOfWeek);
    }
}

