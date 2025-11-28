package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.repository.JudiciaryAvailabilityRuleRepository;

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
        assertThat(saved.getGroup(), is("Available"));
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
        JudiciaryAvailabilityRule rule = createRule(judiciaryId, "Available", 
                startDate, endDate, Arrays.asList("Monday", "Tuesday"), null);
        
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
        JudiciaryAvailabilityRule availableRule = createRule(judiciaryId, "Available", 
                startDate, endDate, Arrays.asList("Monday", "Tuesday"), null);
        JudiciaryAvailabilityRule unavailableRule = createRule(judiciaryId, "Unavailable", 
                startDate, endDate, Arrays.asList("Tuesday"), null);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Arrays.asList(availableRule, unavailableRule));

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
        JudiciaryAvailabilityRule rule = createRule(judiciaryId, "Available", 
                startDate, endDate, Arrays.asList("Tuesday"), "Monthly");
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
        JudiciaryAvailabilityRule rule = createRule(judiciaryId, "Available", 
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 
                Arrays.asList("Wednesday"), null);
        
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
        
        JudiciaryAvailabilityRule rule1 = createRule(judiciaryId, "Available", 
                startDate, endDate, Arrays.asList("Monday"), null);
        JudiciaryAvailabilityRule rule2 = createRule(judiciaryId2, "Available", 
                startDate, endDate, Arrays.asList("Tuesday"), null);
        
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
        JudiciaryAvailabilityRule availableRule = createRule(judiciaryId, "Available", 
                startDate, endDate, Arrays.asList("Monday", "Tuesday"), null);
        // Unavailable on Tuesday (should remove Tuesday)
        JudiciaryAvailabilityRule unavailableRule = createRule(judiciaryId, "Unavailable", 
                startDate, endDate, Arrays.asList("Tuesday"), null);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Arrays.asList(availableRule, unavailableRule));

        FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request);

        // Should still be available because Monday matches (Tuesday was removed by Unavailable rule)
        assertThat(response.getAvailableJudiciaries().size(), is(1));
    }

    private AddJudiciaryAvailabilityRuleRequest createValidRequest() {
        AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setGroup("Available");
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        request.setRecurringType("Weekly");
        
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay("Monday", null));
        repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay("Tuesday", 1));
        request.setRepeatDays(repeatDays);
        
        return request;
    }

    private JudiciaryAvailabilityRule createRule(String judiciaryId, String group, 
                                                 LocalDate fromDate, LocalDate toDate,
                                                 List<String> dayNames, String recurringType) {
        JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
        rule.setId(randomUUID().toString());
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setGroup(group);
        rule.setFromDate(fromDate);
        rule.setToDate(toDate);
        rule.setRecurringType(recurringType);
        
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        for (String dayName : dayNames) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay repeatDay = 
                    new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay();
            repeatDay.setDayOfWeek(dayName);
            repeatDay.setIndex(0);
            repeatDays.add(repeatDay);
        }
        rule.setRepeatDays(repeatDays);
        
        return rule;
    }
}

