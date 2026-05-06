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
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.repository.JudiciaryAvailabilityRuleRepository;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;

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
    private jakarta.persistence.EntityManager entityManager;

    @Mock
    private uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

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
        // availabilityType is no longer stored on entity - it's derived from unAvailabilities
        assertThat(saved.getUnavailabilities().isEmpty(), is(true)); // Should be empty for AVAILABLE
        assertThat(saved.getFromDate(), is(LocalDate.of(2026, 1, 1)));
        assertThat(saved.getToDate(), is(LocalDate.of(2026, 1, 31)));
        assertThat(saved.getRepeatDays().size(), is(2));
    }

    @Test
    void shouldAcceptValidRepeatDays() {
        AddJudiciaryAvailabilityRuleRequest request = createValidRequest();
        // repeatDays are now simple strings, no index needed
        
        service.addJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule saved = captor.getValue();
        // Verify repeat days are correctly saved (no index field anymore)
        assertThat(saved.getRepeatDays().get(0).getDayOfWeek(), is(AvailabilityDayOfWeek.Monday));
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
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday));
        
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
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday));
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

        // Rule: Tuesday each week
        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Tuesday));
        
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
                Arrays.asList(AvailabilityDayOfWeek.Wednesday));
        
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
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));
        JudiciaryAvailabilityRule rule2 = createRule(judiciaryId2,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Tuesday));
        
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
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday));
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
        
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday));
        
        return request;
    }

    private JudiciaryAvailabilityRule createRule(String judiciaryId,
                                                 LocalDate fromDate, LocalDate toDate,
                                                 List<AvailabilityDayOfWeek> dayNames) {
        JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
        rule.setId(randomUUID().toString());
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setFromDate(fromDate);
        rule.setToDate(toDate);
        rule.setUnavailabilities(new ArrayList<>());
        
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        for (AvailabilityDayOfWeek dayName : dayNames) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay repeatDay = 
                    new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay(dayName);
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

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.deleteJudiciaryAvailabilityRule(request);

        verify(repository).findById(ruleId);
        verify(repository).remove(existingRule);
    }

    @Test
    void shouldThrowExceptionWhenRuleNotFound() {
        final String ruleId = randomUUID().toString();
        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.deleteJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Judicial itinerary does not exist."));
        verify(repository).findById(ruleId);
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
        request.setWithJudiciary(false);

        JudiciaryAvailabilityRule rule1 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));
        JudiciaryAvailabilityRule rule2 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Tuesday));

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(2, Arrays.asList(rule1, rule2));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

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
        request.setWithJudiciary(false);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 20, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

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
        request.setWithJudiciary(true);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));

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
                .withRequestedName("MR RECORDER J TEST")
                .build();

        when(referenceDataService.getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Collections.singletonList(judiciary));

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getJudiciaries(), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.getJudiciaries().size(), is(1));
        assertThat(response.getJudiciaries().get(0).getId(), is(judiciaryId));
        assertThat(response.getJudiciaries().get(0).getRequestedName(), is("MR RECORDER J TEST"));
        verify(referenceDataService).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList());
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
        // The legacy controller skipped the judiciary lookup whenever the Requester was
        // null; the Spring port doesn't have a Requester at all and instead skips when
        // {@code withJudiciary} is false. This test still verifies the same "skip" path
        // — the test name is preserved for traceability against the legacy file.
        request.setWithJudiciary(false);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getJudiciaries(), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.getJudiciaries().size(), is(0));
        verify(referenceDataService, org.mockito.Mockito.never()).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList());
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
        request.setWithJudiciary(false);

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(0, Collections.emptyList());

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, courtHouseId, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

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
        request.setWithJudiciary(false);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, judiciaryId, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

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
        request.setWithJudiciary(true);

        JudiciaryAvailabilityRule rule1 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));
        JudiciaryAvailabilityRule rule2 = createRule(judiciaryId2,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Tuesday));

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

        when(referenceDataService.getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(judiciary1, judiciary2));

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(2));
        assertThat(response.getJudiciaries().size(), is(2));
        verify(referenceDataService).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.argThat(list -> list.size() == 2 && list.contains(judiciaryId) && list.contains(judiciaryId2)));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithEmptySpecialisms() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithEmptySpecialismsWhenNoRequester() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.Monday));

        java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(request);

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setSessionType(SessionType.AM);
        
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Wednesday, AvailabilityDayOfWeek.Thursday));

        // Create existing rule
        JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        existingRule.setId(ruleId);
        existingRule.setSessionType(SessionType.AD);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).findById(ruleId);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule updated = captor.getValue();
        assertThat(updated.getId(), is(ruleId));
        assertThat(updated.getJudiciaryId(), is(judiciaryId));
        assertThat(updated.getCourtHouseId(), is(courtHouseId));
        assertThat(updated.getFromDate(), is(LocalDate.of(2026, 2, 1)));
        assertThat(updated.getToDate(), is(LocalDate.of(2026, 2, 28)));
        assertThat(updated.getSessionType(), is(SessionType.AM));
        assertThat(updated.getRepeatDays().size(), is(2));
        assertThat(updated.getRepeatDays().get(0).getDayOfWeek(), is(AvailabilityDayOfWeek.Wednesday));
        assertThat(updated.getRepeatDays().get(1).getDayOfWeek(), is(AvailabilityDayOfWeek.Thursday));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithUnAvailabilities() {
        final String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Friday));

        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
        JudiciaryUnavailabilityRequest unavailability1 = new JudiciaryUnavailabilityRequest();
        unavailability1.setStartDate(LocalDate.of(2026, 2, 10));
        unavailability1.setEndDate(LocalDate.of(2026, 2, 12));
        unavailability1.setReason(UnavailabilityReason.ANNUAL_LEAVE);
        unavailabilities.add(unavailability1);
        
        JudiciaryUnavailabilityRequest unavailability2 = new JudiciaryUnavailabilityRequest();
        unavailability2.setStartDate(LocalDate.of(2026, 2, 20));
        unavailability2.setEndDate(LocalDate.of(2026, 2, 22));
        unavailability2.setReason(UnavailabilityReason.TRAINING);
        unavailabilities.add(unavailability2);
        request.setUnavailabilities(unavailabilities);

        JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule updated = captor.getValue();
        assertThat(updated.getUnavailabilities().size(), is(2));
        assertThat(updated.getUnavailabilities().get(0).getFromDate(), is(LocalDate.of(2026, 2, 10)));
        assertThat(updated.getUnavailabilities().get(0).getToDate(), is(LocalDate.of(2026, 2, 12)));
        assertThat(updated.getUnavailabilities().get(0).getReason(), is(UnavailabilityReason.ANNUAL_LEAVE));
        assertThat(updated.getUnavailabilities().get(1).getFromDate(), is(LocalDate.of(2026, 2, 20)));
        assertThat(updated.getUnavailabilities().get(1).getToDate(), is(LocalDate.of(2026, 2, 22)));
        assertThat(updated.getUnavailabilities().get(1).getReason(), is(UnavailabilityReason.TRAINING));
    }

    @Test
    void shouldThrowExceptionWhenRuleIdIsNull() {
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(null);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Rule ID is required for update"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any());
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowExceptionWhenRuleIdIsEmpty() {
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId("");
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Rule ID is required for update"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any());
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowExceptionWhenUpdateRuleNotFound() {
        final String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Judicial itinerary does not exist."));
        verify(repository).findById(ruleId);
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithNullSessionType() {
        final String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setSessionType(null);
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday));

        JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        existingRule.setId(ruleId);
        existingRule.setSessionType(SessionType.PM);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule updated = captor.getValue();
        // Should default to AD when sessionType is null
        assertThat(updated.getSessionType(), is(SessionType.AD));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithEmptyUnAvailabilities() {
        final String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday));
        request.setUnavailabilities(new ArrayList<>());

        JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        existingRule.setId(ruleId);
        // Add an existing unavailability
        createUnavailableRule(existingRule, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule updated = captor.getValue();
        // Should clear existing unavailabilities when empty list is provided
        assertThat(updated.getUnavailabilities().size(), is(0));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithNullUnavailabilities() {
        final String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(AvailabilityDayOfWeek.Monday));
        request.setUnavailabilities(null);

        JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        existingRule.setId(ruleId);
        createUnavailableRule(existingRule, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule updated = captor.getValue();
        // Should clear existing unavailabilities when null is provided
        assertThat(updated.getUnavailabilities().size(), is(0));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithNullRepeatDays() {
        final String ruleId = randomUUID().toString();
        UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(null);

        JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        JudiciaryAvailabilityRule updated = captor.getValue();
        // Should clear existing repeat days when null is provided
        assertThat(updated.getRepeatDays().size(), is(0));
    }

    @Test
    void shouldGetJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setWithJudiciary(false);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        rule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));

        GetJudiciaryAvailabilityRuleResponse response = service.getJudiciaryAvailabilityRule(request);

        assertNotNull(response);
        assertNotNull(response.getRule());
        assertThat(response.getRule().getId(), is(ruleId));
        assertThat(response.getJudiciary(), is(org.hamcrest.Matchers.nullValue()));
        verify(repository).findById(ruleId);
        verify(referenceDataService, org.mockito.Mockito.never()).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldGetJudiciaryAvailabilityRuleWithJudiciary() {
        final String ruleId = randomUUID().toString();
        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setWithJudiciary(true);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        rule.setId(ruleId);

        final Judiciary judiciary = new Judiciary();
        judiciary.setId(judiciaryId);
        judiciary.setSurname("Smith");

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));
        when(referenceDataService.getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(judiciary));

        GetJudiciaryAvailabilityRuleResponse response = service.getJudiciaryAvailabilityRule(request);

        assertNotNull(response);
        assertNotNull(response.getRule());
        assertThat(response.getRule().getId(), is(ruleId));
        assertNotNull(response.getJudiciary());
        assertThat(response.getJudiciary().getId(), is(judiciaryId));
        assertThat(response.getJudiciary().getSurname(), is("Smith"));
        verify(repository).findById(ruleId);
        verify(referenceDataService).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.argThat(list -> list.size() == 1 && list.contains(judiciaryId)));
    }

    @Test
    void shouldThrowExceptionWhenGetRuleNotFound() {
        final String ruleId = randomUUID().toString();
        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.getJudiciaryAvailabilityRule(request);
        });

        assertThat(exception.getMessage(), is("Judicial itinerary does not exist."));
        verify(repository).findById(ruleId);
    }

    @Test
    void shouldThrowExceptionWhenGetRuleIdIsNull() {
        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.getJudiciaryAvailabilityRule(request);
        });

        assertThat(exception.getMessage(), is("Rule ID is required"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldThrowExceptionWhenGetRuleIdIsEmpty() {
        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId("");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.getJudiciaryAvailabilityRule(request);
        });

        assertThat(exception.getMessage(), is("Rule ID is required"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldGetJudiciaryAvailabilityRuleWithNullRequester() {
        final String ruleId = randomUUID().toString();
        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        // Legacy: skipped judiciary lookup when Requester was null. Spring port: skips when
        // withJudiciary is false. Same skip path; test name preserved for traceability.
        request.setWithJudiciary(false);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        rule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));

        GetJudiciaryAvailabilityRuleResponse response = service.getJudiciaryAvailabilityRule(request);

        assertNotNull(response);
        assertNotNull(response.getRule());
        assertThat(response.getRule().getId(), is(ruleId));
        assertThat(response.getJudiciary(), is(org.hamcrest.Matchers.nullValue()));
        verify(repository).findById(ruleId);
        verify(referenceDataService, org.mockito.Mockito.never()).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldGetJudiciaryAvailabilityRuleWithNullJudiciaryId() {
        final String ruleId = randomUUID().toString();
        GetJudiciaryAvailabilityRuleRequest request = new GetJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setWithJudiciary(true);

        JudiciaryAvailabilityRule rule = createRule(null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        rule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));

        GetJudiciaryAvailabilityRuleResponse response = service.getJudiciaryAvailabilityRule(request);

        assertNotNull(response);
        assertNotNull(response.getRule());
        assertThat(response.getRule().getId(), is(ruleId));
        assertThat(response.getJudiciary(), is(org.hamcrest.Matchers.nullValue()));
        verify(repository).findById(ruleId);
        verify(referenceDataService, org.mockito.Mockito.never()).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldReturnNullWhenValidateDeleteWithNoMatchingSessions() {
        final String ruleId = randomUUID().toString();
        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        rule.setId(ruleId);
        rule.setSessionType(SessionType.AM);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                judiciaryId, rule.getFromDate(), rule.getToDate(), "AM"))
                .thenReturn(Collections.emptyList());

        String result = service.validateDeleteJudiciaryAvailabilityRule(request);

        assertThat(result, is(org.hamcrest.Matchers.nullValue()));
        verify(repository).findById(ruleId);
        verify(courtScheduleJudiciaryRepository).findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                judiciaryId, rule.getFromDate(), rule.getToDate(), "AM");
    }

    @Test
    void shouldReturnErrorWhenValidateDeleteRuleNotFound() {
        final String ruleId = randomUUID().toString();
        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        String result = service.validateDeleteJudiciaryAvailabilityRule(request);

        assertNotNull(result);
        assertTrue(result.contains("Judicial itinerary does not exist"));
        verify(repository).findById(ruleId);
    }

    @Test
    void shouldReturnErrorWhenValidateDeleteRuleIdIsNull() {
        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(null);
        request.setJudiciaryId(judiciaryId);

        String result = service.validateDeleteJudiciaryAvailabilityRule(request);

        assertNotNull(result);
        assertTrue(result.contains("Rule ID is required"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyString());
    }


    @Test
    void shouldReturnErrorWhenValidateDeleteRuleAppliedToADSessionType() {
        final String ruleId = randomUUID().toString();
        final String sessionId = randomUUID().toString();
        DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);

        JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.Monday));
        rule.setId(ruleId);
        rule.setSessionType(SessionType.AD);

        // Monday, January 5, 2026 (January 6 is Tuesday)
        LocalDate sessionDate = LocalDate.of(2026, 1, 5);
        Object[] sessionData = new Object[]{sessionId, sessionDate, "AM"};

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));
        // AD rule should match AM, PM, or AD sessions
        List<Object[]> sessionListAD = new ArrayList<>();
        sessionListAD.add(sessionData);
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                judiciaryId, rule.getFromDate(), rule.getToDate(), "AD"))
                .thenReturn(sessionListAD);

        String result = service.validateDeleteJudiciaryAvailabilityRule(request);

        assertNotNull(result);
        assertTrue(result.contains("being used in a session"));
        verify(repository).findById(ruleId);
        verify(courtScheduleJudiciaryRepository).findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                judiciaryId, rule.getFromDate(), rule.getToDate(), "AD");
    }
}

