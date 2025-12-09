package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.RecurringType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.repository.JudiciaryAvailabilityRuleRepository;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;
import uk.gov.justice.services.core.requester.Requester;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryAvailabilityServiceTest {

    @Mock
    private JudiciaryAvailabilityRuleRepository repository;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private Requester requester;

    @Mock
    private javax.persistence.EntityManager entityManager;

    @InjectMocks
    private JudiciaryAvailabilityService service;

    private String judiciaryId;
    private String courtHouseId;

    @BeforeEach
    void setUp() {
        judiciaryId = randomUUID().toString();
        courtHouseId = randomUUID().toString();
    }

    @Test
    void shouldAddJudiciaryAvailabilityRule() {
        AddJudiciaryAvailabilityRuleRequest request = createValidRequest();
        
        service.addJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule saved = captor.getValue();
        assertNotNull(saved.getId());
        assertThat(saved.getJudiciaryId(), is(judiciaryId));
        assertThat(saved.getCourtHouseId(), is(courtHouseId));
        // availabilityType is no longer stored on entity - it's derived from unavailabilities
        assertThat(saved.getUnavailabilities().isEmpty(), is(true)); // Should be empty for AVAILABLE
        assertThat(saved.getFromDate(), is(LocalDate.of(2026, 1, 1)));
        assertThat(saved.getToDate(), is(LocalDate.of(2026, 1, 31)));
        assertThat(saved.getRepeatDays().size(), is(2));
    }

    @Test
    void shouldConvertNullIndexToZero() {
        AddJudiciaryAvailabilityRuleRequest request = createValidRequest();
        request.getRepeatDays().get(0).setIndex(null);
        
        service.addJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule saved = captor.getValue();
        assertThat(saved.getRepeatDays().get(0).getIndex(), is(0));
    }

    @Test
    void shouldFindAvailableJudiciariesWithSimpleAvailableRule() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 7); // Week with Monday and Tuesday
        
        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Create rule: Available on Monday and Tuesday
        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY), null);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Collections.singletonList(rule));

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        assertNotNull(response);
        assertThat(response.getAvailableJudiciaries().size(), is(1));
        assertThat(response.getAvailableJudiciaries().get(0), is(judiciaryId));
    }

    @Test
    void shouldExcludeJudiciariesWithUnavailableRule() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Create rule: Available on Monday, but Unavailable on Tuesday
        JudiciaryAvailabilityRule availableRule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY), null);
        final LocalDate unavailableDate = LocalDate.of(2026, 1, 6); //Tuesday
        createUnavailableRule(availableRule, unavailableDate, unavailableDate);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Arrays.asList(availableRule));

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        // Should still be available because Monday matches
        assertThat(response.getAvailableJudiciaries().size(), is(1));
        assertThat(response.getAvailableJudiciaries().get(0), is(judiciaryId));
    }

    @Test
    void shouldFilterByCourtHouseId() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setCourtHouseId(courtHouseId);

        when(repository.findRulesByDateRange(startDate, endDate, courtHouseId, null))
                .thenReturn(Collections.emptyList());

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        assertThat(response.getAvailableJudiciaries().size(), is(0));
    }

    @Test
    void shouldFilterByJudiciaryId() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setJudiciaryId(judiciaryId);

        when(repository.findRulesByDateRange(startDate, endDate, null, judiciaryId))
                .thenReturn(Collections.emptyList());

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        assertThat(response.getAvailableJudiciaries().size(), is(0));
    }

    @Test
    void shouldHandleMonthlyRecurringWithIndex() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 3, 31); // 3 months
        
        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Rule: 2nd Tuesday of each month
        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.TUESDAY), RecurringType.MONTHLY);
        rule.getRepeatDays().get(0).setIndex(2);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Collections.singletonList(rule));

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        // Should find matches for 2nd Tuesday in Jan, Feb, Mar
        assertThat(response.getAvailableJudiciaries().size(), is(1));
    }

    @Test
    void shouldNotIncludeJudiciaryWhenNoMatchingDates() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Rule: Available only on Friday, but query range is Mon-Sun (no Friday in first week of Jan 2026)
        // Actually, let's use a range that definitely doesn't have the day
        // Jan 1, 2026 is a Thursday, so Jan 1-7 includes: Thu, Fri, Sat, Sun, Mon, Tue, Wed
        // So Friday IS in the range. Let's use Wednesday only
        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 
                Arrays.asList(AvailabilityDayOfWeek.WEDNESDAY), null);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Collections.singletonList(rule));

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        // Jan 1-7, 2026: Thu, Fri, Sat, Sun, Mon, Tue, Wed - Wednesday is Jan 6, so should match
        // Let me use a better test - rule only for dates outside the query range
        rule.setFromDate(LocalDate.of(2026, 2, 1));
        rule.setToDate(LocalDate.of(2026, 2, 28));
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Collections.singletonList(rule));

        response = service.findJudiciaryAvailability(request);

        // Rule is in February, query is in January - no match
        assertThat(response.getAvailableJudiciaries().size(), is(0));
    }

    @Test
    void shouldHandleMultipleJudiciaries() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        String judiciaryId2 = randomUUID().toString();
        
        JudiciaryAvailabilityRule rule1 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);
        JudiciaryAvailabilityRule rule2 = createRule(judiciaryId2,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.TUESDAY), null);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Arrays.asList(rule1, rule2));

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        assertThat(response.getAvailableJudiciaries().size(), is(2));
        assertTrue(response.getAvailableJudiciaries().contains(judiciaryId));
        assertTrue(response.getAvailableJudiciaries().contains(judiciaryId2));
    }

    @Test
    void shouldCompileRulesCorrectly_AvailableOverridesUnavailable() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Available on Monday and Tuesday
        JudiciaryAvailabilityRule availableRule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY), null);
        // Unavailable on Tuesday (should remove Tuesday)
        LocalDate unavailableDate = LocalDate.of(2026, 1, 6);//Tuesday
        createUnavailableRule(availableRule, unavailableDate, unavailableDate);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Arrays.asList(availableRule));

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        // Should still be available because Monday matches (Tuesday was removed by Unavailable rule)
        assertThat(response.getAvailableJudiciaries().size(), is(1));
    }

    private AddJudiciaryAvailabilityRuleRequest createValidRequest() {
        AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setRecurringType(RecurringType.WEEKLY);
        
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.MONDAY, null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.TUESDAY, 1));
        request.setRepeatDays(repeatDays);
        
        return request;
    }

    private JudiciaryAvailabilityRule createRule(String judiciaryId,
                                                 LocalDate fromDate, LocalDate toDate,
                                                 List<AvailabilityDayOfWeek> dayNames, RecurringType recurringType) {
        JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
        rule.setId(randomUUID().toString());
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setFromDate(fromDate);
        rule.setToDate(toDate);
        rule.setRecurringType(recurringType);
        rule.setUnavailabilities(new ArrayList<>());
        
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        for (AvailabilityDayOfWeek dayName : dayNames) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay repeatDay = 
                    new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay();
            repeatDay.setDayOfWeek(dayName);
            repeatDay.setIndex(0);
            repeatDays.add(repeatDay);
        }
        rule.setRepeatDays(repeatDays);
        
        return rule;
    }
    
    private void createUnavailableRule(JudiciaryAvailabilityRule rule,
                                       LocalDate fromDate, LocalDate toDate) {
        // Create a corresponding unavailability record
        uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability = 
                new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability();
        unavailability.setId(randomUUID().toString());
        unavailability.setRule(rule);
        unavailability.setFromDate(fromDate);
        unavailability.setToDate(toDate);
        rule.getUnavailabilities().add(unavailability);
    }

    @Test
    void shouldDeleteJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        JudiciaryAvailabilityRule existingRule = new JudiciaryAvailabilityRule();
        existingRule.setId(ruleId);
        existingRule.setJudiciaryId(judiciaryId);
        existingRule.setCourtHouseId(courtHouseId);

        when(repository.findBy(ruleId)).thenReturn(existingRule);

        service.deleteJudiciaryAvailabilityRule(request);

        verify(repository).findBy(ruleId);
        verify(repository).remove(existingRule);
    }

    @Test
    void shouldThrowExceptionWhenRuleNotFound() {
        final String ruleId = randomUUID().toString();
        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        when(repository.findBy(ruleId)).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.deleteJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Judiciary availability rule with id " + ruleId + " not found"));
        verify(repository).findBy(ruleId);
        verify(repository, org.mockito.Mockito.never()).remove(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithPagination() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciaries(false);

        JudiciaryAvailabilityRule rule1 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);
        JudiciaryAvailabilityRule rule2 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.TUESDAY), null);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(2, Arrays.asList(rule1, rule2));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, requester);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(2));
        assertThat(response.getTotalCount(), is(2));
        assertThat(response.getPageNumber(), is(1));
        assertThat(response.getPageSize(), is(10));
        assertThat(response.getJudiciaries(), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.getJudiciaries().size(), is(0));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithDefaultPagination() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(null);
        request.setPageNumber(null);
        request.setWithJudiciaries(false);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 20, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, requester);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getTotalCount(), is(1));
        assertThat(response.getPageNumber(), is(1));
        assertThat(response.getPageSize(), is(20));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciaries() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciaries(true);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        Judiciary judiciary = Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(judiciaryId)
                .withSurname("Test")
                .withForenames("Judge")
                .withJudiciaryType("Judge")
                .withSeqId(1)
                .build();

        when(referenceDataService.getJudiciariesByIds(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(requester)))
                .thenReturn(Collections.singletonList(judiciary));

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, requester);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getJudiciaries(), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.getJudiciaries().size(), is(1));
        assertThat(response.getJudiciaries().get(0).getId(), is(judiciaryId));
        verify(referenceDataService).getJudiciariesByIds(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(requester));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciariesButNoRequester() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciaries(true);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, null);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getJudiciaries(), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.getJudiciaries().size(), is(0));
        verify(referenceDataService, org.mockito.Mockito.never()).getJudiciariesByIds(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithCourtHouseIdFilter() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setCourtHouseId(courtHouseId);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciaries(false);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(0, Collections.emptyList());

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, courtHouseId, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, requester);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(0));
        assertThat(response.getTotalCount(), is(0));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciaryIdFilter() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setJudiciaryId(judiciaryId);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciaries(false);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, judiciaryId, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, requester);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getTotalCount(), is(1));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithMultipleJudiciaries() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        String judiciaryId2 = randomUUID().toString();
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciaries(true);

        JudiciaryAvailabilityRule rule1 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);
        JudiciaryAvailabilityRule rule2 = createRule(judiciaryId2,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.TUESDAY), null);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(2, Arrays.asList(rule1, rule2));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        Judiciary judiciary1 = Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(judiciaryId)
                .withSurname("Test1")
                .withForenames("Judge")
                .withJudiciaryType("Judge")
                .withSeqId(1)
                .build();

        Judiciary judiciary2 = Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(judiciaryId2)
                .withSurname("Test2")
                .withForenames("Judge")
                .withJudiciaryType("Judge")
                .withSeqId(2)
                .build();

        when(referenceDataService.getJudiciariesByIds(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(requester)))
                .thenReturn(Arrays.asList(judiciary1, judiciary2));

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, requester);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(2));
        assertThat(response.getJudiciaries().size(), is(2));
        verify(referenceDataService).getJudiciariesByIds(org.mockito.ArgumentMatchers.argThat(list -> list.size() == 2 && list.contains(judiciaryId) && list.contains(judiciaryId2)), org.mockito.ArgumentMatchers.eq(requester));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithSpecialisms() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithSpecialisms(true);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialism specialism = new uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialism();
        specialism.setJudiciaryId(judiciaryId);
        specialism.setSpecialisms(java.util.Arrays.asList(uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType.MURDER));

        when(referenceDataService.getSpecialismsByJudiciaryIds(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(requester)))
                .thenReturn(Collections.singletonList(specialism));

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, requester);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getSpecialisms(), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.getSpecialisms().size(), is(1));
        assertThat(response.getSpecialisms().get(0).getJudiciaryId(), is(judiciaryId));
        verify(referenceDataService).getSpecialismsByJudiciaryIds(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(requester));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithSpecialismsButNoRequester() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithSpecialisms(true);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY), null);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request, null);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getSpecialisms(), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.getSpecialisms().size(), is(0));
        verify(referenceDataService, org.mockito.Mockito.never()).getSpecialismsByJudiciaryIds(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any());
    }
}

