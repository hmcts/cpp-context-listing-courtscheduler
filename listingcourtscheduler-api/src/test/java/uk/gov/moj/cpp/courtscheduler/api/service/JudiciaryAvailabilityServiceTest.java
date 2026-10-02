package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.DateSessionType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.JudiciaryUnavailability;
import uk.gov.moj.cpp.courtscheduler.openapi.model.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerFindJudiciaryAvailabilityRuleQuery;
import uk.gov.moj.cpp.courtscheduler.openapi.model.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.repository.JudiciaryAvailabilityRuleRepository;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryAvailabilityServiceTest {
    private static final String MONDAY_2 = "Monday";

    private static final String JUDGE = "Judge";
    private static final String HOUSE = "house";


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
        final AddJudiciaryAvailabilityRuleRequest request = createValidRequest();
        
        service.addJudiciaryAvailabilityRule(request);

        final ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        final JudiciaryAvailabilityRule saved = captor.getValue();
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
        final AddJudiciaryAvailabilityRuleRequest request = createValidRequest();
        // repeatDays are now simple strings, no index needed
        
        service.addJudiciaryAvailabilityRule(request);

        final ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        final JudiciaryAvailabilityRule saved = captor.getValue();
        // Verify repeat days are correctly saved (no index field anymore)
        assertThat(saved.getRepeatDays().get(0).getDayOfWeek(), is(AvailabilityDayOfWeek.MONDAY));
    }

    @Test
    void shouldFindAvailableJudiciariesWithSimpleAvailableRule() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 7); // Week with Monday and Tuesday
        
        final FindAvailabilityRequestFixture request = new FindAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Create rule: Available on Monday and Tuesday
        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY));
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Collections.singletonList(rule));

        final FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        assertNotNull(response);
        assertThat(response.getAvailableJudiciaries().size(), is(1));
        assertThat(response.getAvailableJudiciaries().get(0), is(judiciaryId));
    }

    @Test
    void shouldExcludeJudiciariesWithUnavailableRule() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        final FindAvailabilityRequestFixture request = new FindAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Create rule: Available on Monday, but Unavailable on Tuesday
        final JudiciaryAvailabilityRule availableRule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY));
        final LocalDate unavailableDate = LocalDate.of(2026, 1, 6); //Tuesday
        createUnavailableRule(availableRule, unavailableDate, unavailableDate);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Arrays.asList(availableRule));

        final FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        // Should still be available because Monday matches
        assertThat(response.getAvailableJudiciaries().size(), is(1));
        assertThat(response.getAvailableJudiciaries().get(0), is(judiciaryId));
    }

    @Test
    void shouldFilterByCourtHouseId() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        final FindAvailabilityRequestFixture request = new FindAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setCourtHouseId(courtHouseId);

        when(repository.findRulesByDateRange(startDate, endDate, courtHouseId, null))
                .thenReturn(Collections.emptyList());

        final FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        assertThat(response.getAvailableJudiciaries().size(), is(0));
    }

    @Test
    void shouldFilterByJudiciaryId() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        final FindAvailabilityRequestFixture request = new FindAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setJudiciaryId(judiciaryId);

        when(repository.findRulesByDateRange(startDate, endDate, null, judiciaryId))
                .thenReturn(Collections.emptyList());

        final FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        assertThat(response.getAvailableJudiciaries().size(), is(0));
    }

    @Test
    void shouldHandleMonthlyRecurringWithIndex() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 3, 31); // 3 months
        
        final FindAvailabilityRequestFixture request = new FindAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Rule: Tuesday each week
        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.TUESDAY));
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Collections.singletonList(rule));

        final FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        // Should find matches for 2nd Tuesday in Jan, Feb, Mar
        assertThat(response.getAvailableJudiciaries().size(), is(1));
    }

    @Test
    void shouldNotIncludeJudiciaryWhenNoMatchingDates() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        final FindAvailabilityRequestFixture request = new FindAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Rule: Available only on Friday, but query range is Mon-Sun (no Friday in first week of Jan 2026)
        // Actually, let's use a range that definitely doesn't have the day
        // Jan 1, 2026 is a Thursday, so Jan 1-7 includes: Thu, Fri, Sat, Sun, Mon, Tue, Wed
        // So Friday IS in the range. Let's use Wednesday only
        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), 
                Arrays.asList(AvailabilityDayOfWeek.WEDNESDAY));
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Collections.singletonList(rule));

        service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        // Jan 1-7, 2026: Thu, Fri, Sat, Sun, Mon, Tue, Wed - Wednesday is Jan 6, so should match
        // Let me use a better test - rule only for dates outside the query range
        rule.setFromDate(LocalDate.of(2026, 2, 1));
        rule.setToDate(LocalDate.of(2026, 2, 28));
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Collections.singletonList(rule));

        final FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        // Rule is in February, query is in January - no match
        assertThat(response.getAvailableJudiciaries().size(), is(0));
    }

    @Test
    void shouldHandleMultipleJudiciaries() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        final FindAvailabilityRequestFixture request = new FindAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        final String judiciaryId2 = randomUUID().toString();
        
        final JudiciaryAvailabilityRule rule1 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        final JudiciaryAvailabilityRule rule2 = createRule(judiciaryId2,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.TUESDAY));
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Arrays.asList(rule1, rule2));

        final FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        assertThat(response.getAvailableJudiciaries().size(), is(2));
        assertTrue(response.getAvailableJudiciaries().contains(judiciaryId));
        assertTrue(response.getAvailableJudiciaries().contains(judiciaryId2));
    }

    @Test
    void shouldCompileRulesCorrectly_AvailableOverridesUnavailable() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 7);
        
        final FindAvailabilityRequestFixture request = new FindAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);

        // Available on Monday and Tuesday
        final JudiciaryAvailabilityRule availableRule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY));
        // Unavailable on Tuesday (should remove Tuesday)
        final LocalDate unavailableDate = LocalDate.of(2026, 1, 6);//Tuesday
        createUnavailableRule(availableRule, unavailableDate, unavailableDate);
        
        when(repository.findRulesByDateRange(startDate, endDate, null, null))
                .thenReturn(Arrays.asList(availableRule));

        final FindJudiciaryAvailabilityResponse response = service.findJudiciaryAvailability(request.getStartDate(), request.getEndDate(), request.getCourtHouseId(), request.getJudiciaryId());

        // Should still be available because Monday matches (Tuesday was removed by Unavailable rule)
        assertThat(response.getAvailableJudiciaries().size(), is(1));
    }

    private AddJudiciaryAvailabilityRuleRequest createValidRequest() {
        final AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        request.setEndDate(LocalDate.of(2026, 1, 31));
        
        request.setRepeatDays(Arrays.asList(MONDAY_2, "Tuesday"));
        
        return request;
    }

    private JudiciaryAvailabilityRule createRule(final String judiciaryId,
                                                 final LocalDate fromDate, final LocalDate toDate,
                                                 final List<AvailabilityDayOfWeek> dayNames) {
        final JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
        rule.setId(randomUUID().toString());
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setFromDate(fromDate);
        rule.setToDate(toDate);
        rule.setUnavailabilities(new ArrayList<>());
        
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> repeatDays = dayNames.stream()
                .map(uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay::new)
                .collect(Collectors.toCollection(ArrayList::new));
        rule.setRepeatDays(repeatDays);
        
        return rule;
    }
    
    private void createUnavailableRule(final JudiciaryAvailabilityRule rule,
                                       final LocalDate fromDate, final LocalDate toDate) {
        // Create a corresponding unavailability record
        final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability = 
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
        final DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        final JudiciaryAvailabilityRule existingRule = new JudiciaryAvailabilityRule();
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
        final DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.deleteJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Judicial itinerary does not exist."));
        verify(repository).findById(ruleId);
        verify(repository, org.mockito.Mockito.never()).remove(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithPagination() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciary(false);

        final JudiciaryAvailabilityRule rule1 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        final JudiciaryAvailabilityRule rule2 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.TUESDAY));

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(2, Arrays.asList(rule1, rule2));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(null);
        request.setPageNumber(null);
        request.setWithJudiciary(false);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 20, 1))
                .thenReturn(result);

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getTotalCount(), is(1));
        assertThat(response.getPageNumber(), is(1));
        assertThat(response.getPageSize(), is(20));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciaries() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciary(true);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        final Judiciary judiciary = new Judiciary()
                .id(judiciaryId)
                .surname("Test")
                .forenames(JUDGE)
                .judiciaryType(JUDGE)
                .seqId(1)
                .requestedName("MR RECORDER J TEST");

        when(referenceDataService.getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Collections.singletonList(judiciary));

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        // The legacy controller skipped the judiciary lookup whenever the Requester was
        // null; the Spring port doesn't have a Requester at all and instead skips when
        // {@code withJudiciary} is false. This test still verifies the same "skip" path
        // — the test name is preserved for traceability against the legacy file.
        request.setWithJudiciary(false);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getJudiciaries(), is(org.hamcrest.Matchers.notNullValue()));
        assertThat(response.getJudiciaries().size(), is(0));
        verify(referenceDataService, org.mockito.Mockito.never()).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithCourtHouseIdFilter() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setCourtHouseId(courtHouseId);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciary(false);

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(0, Collections.emptyList());

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, courtHouseId, null, 10, 1))
                .thenReturn(result);

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

        assertNotNull(response);
        assertThat(response.getRules().size(), is(0));
        assertThat(response.getTotalCount(), is(0));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciaryIdFilter() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setJudiciaryId(judiciaryId);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciary(false);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, judiciaryId, 10, 1))
                .thenReturn(result);

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
        assertThat(response.getTotalCount(), is(1));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithMultipleJudiciaries() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        final String judiciaryId2 = randomUUID().toString();
        
        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);
        request.setWithJudiciary(true);

        final JudiciaryAvailabilityRule rule1 = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        final JudiciaryAvailabilityRule rule2 = createRule(judiciaryId2,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.TUESDAY));

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(2, Arrays.asList(rule1, rule2));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        final Judiciary judiciary1 = new Judiciary()
                .id(judiciaryId)
                .surname("Test1")
                .forenames(JUDGE)
                .judiciaryType(JUDGE)
                .seqId(1);

        final Judiciary judiciary2 = new Judiciary()
                .id(judiciaryId2)
                .surname("Test2")
                .forenames(JUDGE)
                .judiciaryType(JUDGE)
                .seqId(2);

        when(referenceDataService.getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(judiciary1, judiciary2));

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

        assertNotNull(response);
        assertThat(response.getRules().size(), is(2));
        assertThat(response.getJudiciaries().size(), is(2));
        verify(referenceDataService).getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.argThat(list -> list.size() == 2 && list.contains(judiciaryId) && list.contains(judiciaryId2)));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithEmptySpecialisms() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithEmptySpecialismsWhenNoRequester() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        
        final FindRuleAvailabilityRequestFixture request = new FindRuleAvailabilityRequestFixture();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        request.setPageSize(10);
        request.setPageNumber(1);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                startDate, endDate, Arrays.asList(AvailabilityDayOfWeek.MONDAY));

        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                new java.util.AbstractMap.SimpleEntry<>(1, Collections.singletonList(rule));

        when(repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1))
                .thenReturn(result);

        final FindJudiciaryAvailabilityRuleResponse response = service.findJudiciaryAvailabilityRules(new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .courtHouseId(request.getCourtHouseId()).judiciaryId(request.getJudiciaryId())
                .pageSize(request.getPageSize()).pageNumber(request.getPageNumber())
                .withJudiciary(request.isWithJudiciary()));

        assertNotNull(response);
        assertThat(response.getRules().size(), is(1));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setSessionType("AM");
        
        request.setRepeatDays(Arrays.asList("Wednesday", "Thursday"));

        // Create existing rule
        final JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        existingRule.setId(ruleId);
        existingRule.setSessionType(SessionType.AD);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        final ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).findById(ruleId);
        verify(repository).save(captor.capture());
        
        final JudiciaryAvailabilityRule updated = captor.getValue();
        assertThat(updated.getId(), is(ruleId));
        assertThat(updated.getJudiciaryId(), is(judiciaryId));
        assertThat(updated.getCourtHouseId(), is(courtHouseId));
        assertThat(updated.getFromDate(), is(LocalDate.of(2026, 2, 1)));
        assertThat(updated.getToDate(), is(LocalDate.of(2026, 2, 28)));
        assertThat(updated.getSessionType(), is(SessionType.AM));
        assertThat(updated.getRepeatDays().size(), is(2));
        assertThat(updated.getRepeatDays().get(0).getDayOfWeek(), is(AvailabilityDayOfWeek.WEDNESDAY));
        assertThat(updated.getRepeatDays().get(1).getDayOfWeek(), is(AvailabilityDayOfWeek.THURSDAY));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithUnAvailabilities() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList("Friday"));

        final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
        final JudiciaryUnavailability unavailability1 = new JudiciaryUnavailability();
        unavailability1.setStartDate(LocalDate.of(2026, 2, 10));
        unavailability1.setEndDate(LocalDate.of(2026, 2, 12));
        unavailability1.setReason("ANNUAL_LEAVE");
        unavailabilities.add(unavailability1);
        
        final JudiciaryUnavailability unavailability2 = new JudiciaryUnavailability();
        unavailability2.setStartDate(LocalDate.of(2026, 2, 20));
        unavailability2.setEndDate(LocalDate.of(2026, 2, 22));
        unavailability2.setReason("TRAINING");
        unavailabilities.add(unavailability2);
        request.setUnavailabilities(unavailabilities);

        final JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        final ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        final JudiciaryAvailabilityRule updated = captor.getValue();
        assertThat(updated.getUnavailabilities().size(), is(2));
        assertThat(updated.getUnavailabilities().get(0).getFromDate(), is(LocalDate.of(2026, 2, 10)));
        assertThat(updated.getUnavailabilities().get(0).getToDate(), is(LocalDate.of(2026, 2, 12)));
        assertThat(updated.getUnavailabilities().get(0).getReason().name(), is("ANNUAL_LEAVE"));
        assertThat(updated.getUnavailabilities().get(1).getFromDate(), is(LocalDate.of(2026, 2, 20)));
        assertThat(updated.getUnavailabilities().get(1).getToDate(), is(LocalDate.of(2026, 2, 22)));
        assertThat(updated.getUnavailabilities().get(1).getReason().name(), is("TRAINING"));
    }

    @Test
    void shouldThrowExceptionWhenRuleIdIsNull() {
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(null);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(MONDAY_2));

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Rule ID is required for update"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any());
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowExceptionWhenRuleIdIsEmpty() {
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId("");
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(MONDAY_2));

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Rule ID is required for update"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any());
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldThrowExceptionWhenUpdateRuleNotFound() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(MONDAY_2));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.updateJudiciaryAvailabilityRule(request));

        assertThat(exception.getMessage(), is("Judicial itinerary does not exist."));
        verify(repository).findById(ruleId);
        verify(repository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithNullSessionType() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setSessionType(null);
        request.setRepeatDays(Arrays.asList(MONDAY_2));

        final JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        existingRule.setId(ruleId);
        existingRule.setSessionType(SessionType.PM);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        final ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        final JudiciaryAvailabilityRule updated = captor.getValue();
        // Should default to AD when sessionType is null
        assertThat(updated.getSessionType(), is(SessionType.AD));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithEmptyUnAvailabilities() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(MONDAY_2));
        request.setUnavailabilities(new ArrayList<>());

        final JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        existingRule.setId(ruleId);
        // Add an existing unavailability
        createUnavailableRule(existingRule, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        final ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        final JudiciaryAvailabilityRule updated = captor.getValue();
        // Should clear existing unavailabilities when empty list is provided
        assertThat(updated.getUnavailabilities().size(), is(0));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithNullUnavailabilities() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(Arrays.asList(MONDAY_2));
        request.setUnavailabilities(null);

        final JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        existingRule.setId(ruleId);
        createUnavailableRule(existingRule, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 15));

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        final ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        final JudiciaryAvailabilityRule updated = captor.getValue();
        // Should clear existing unavailabilities when null is provided
        assertThat(updated.getUnavailabilities().size(), is(0));
    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRuleWithNullRepeatDays() {
        final String ruleId = randomUUID().toString();
        final UpdateJudiciaryAvailabilityRuleRequest request = new UpdateJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);
        request.setJudiciaryId(judiciaryId);
        request.setCourtHouseId(courtHouseId);
        request.setStartDate(LocalDate.of(2026, 2, 1));
        request.setEndDate(LocalDate.of(2026, 2, 28));
        request.setRepeatDays(null);

        final JudiciaryAvailabilityRule existingRule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        existingRule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(existingRule));

        service.updateJudiciaryAvailabilityRule(request);

        final ArgumentCaptor<JudiciaryAvailabilityRule> captor = ArgumentCaptor.forClass(JudiciaryAvailabilityRule.class);
        verify(repository).save(captor.capture());
        
        final JudiciaryAvailabilityRule updated = captor.getValue();
        // Should clear existing repeat days when null is provided
        assertThat(updated.getRepeatDays().size(), is(0));
    }

    @Test
    void shouldGetJudiciaryAvailabilityRule() {
        final String ruleId = randomUUID().toString();
        final GetRuleAvailabilityRequestFixture request = new GetRuleAvailabilityRequestFixture();
        request.setRuleId(ruleId);
        request.setWithJudiciary(false);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        rule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));

        final GetJudiciaryAvailabilityRuleResponse response = service.getJudiciaryAvailabilityRule(request.getRuleId(), request.isWithJudiciary());

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
        final GetRuleAvailabilityRequestFixture request = new GetRuleAvailabilityRequestFixture();
        request.setRuleId(ruleId);
        request.setWithJudiciary(true);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        rule.setId(ruleId);

        final Judiciary judiciary = new Judiciary();
        judiciary.setId(judiciaryId);
        judiciary.setSurname("Smith");

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));
        when(referenceDataService.getJudiciariesWithSpecialismByIds(org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(Arrays.asList(judiciary));

        final GetJudiciaryAvailabilityRuleResponse response = service.getJudiciaryAvailabilityRule(request.getRuleId(), request.isWithJudiciary());

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
        final GetRuleAvailabilityRequestFixture request = new GetRuleAvailabilityRequestFixture();
        request.setRuleId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.getJudiciaryAvailabilityRule(request.getRuleId(), request.isWithJudiciary());
        });

        assertThat(exception.getMessage(), is("Judicial itinerary does not exist."));
        verify(repository).findById(ruleId);
    }

    @Test
    void shouldThrowExceptionWhenGetRuleIdIsNull() {
        final GetRuleAvailabilityRequestFixture request = new GetRuleAvailabilityRequestFixture();
        request.setRuleId(null);

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.getJudiciaryAvailabilityRule(request.getRuleId(), request.isWithJudiciary());
        });

        assertThat(exception.getMessage(), is("Rule ID is required"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldThrowExceptionWhenGetRuleIdIsEmpty() {
        final GetRuleAvailabilityRequestFixture request = new GetRuleAvailabilityRequestFixture();
        request.setRuleId("");

        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.getJudiciaryAvailabilityRule(request.getRuleId(), request.isWithJudiciary());
        });

        assertThat(exception.getMessage(), is("Rule ID is required"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldGetJudiciaryAvailabilityRuleWithNullRequester() {
        final String ruleId = randomUUID().toString();
        final GetRuleAvailabilityRequestFixture request = new GetRuleAvailabilityRequestFixture();
        request.setRuleId(ruleId);
        // Legacy: skipped judiciary lookup when Requester was null. Spring port: skips when
        // withJudiciary is false. Same skip path; test name preserved for traceability.
        request.setWithJudiciary(false);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        rule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));

        final GetJudiciaryAvailabilityRuleResponse response = service.getJudiciaryAvailabilityRule(request.getRuleId(), request.isWithJudiciary());

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
        final GetRuleAvailabilityRequestFixture request = new GetRuleAvailabilityRequestFixture();
        request.setRuleId(ruleId);
        request.setWithJudiciary(true);

        final JudiciaryAvailabilityRule rule = createRule(null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        rule.setId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));

        final GetJudiciaryAvailabilityRuleResponse response = service.getJudiciaryAvailabilityRule(request.getRuleId(), request.isWithJudiciary());

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
        final DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        rule.setId(ruleId);
        rule.setSessionType(SessionType.AM);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                judiciaryId, rule.getFromDate(), rule.getToDate(), "AM"))
                .thenReturn(Collections.emptyList());

        final String result = service.validateDeleteJudiciaryAvailabilityRule(request);

        assertThat(result, is(org.hamcrest.Matchers.nullValue()));
        verify(repository).findById(ruleId);
        verify(courtScheduleJudiciaryRepository).findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                judiciaryId, rule.getFromDate(), rule.getToDate(), "AM");
    }

    @Test
    void shouldReturnErrorWhenValidateDeleteRuleNotFound() {
        final String ruleId = randomUUID().toString();
        final DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.empty());

        final String result = service.validateDeleteJudiciaryAvailabilityRule(request);

        assertNotNull(result);
        assertTrue(result.contains("Judicial itinerary does not exist"));
        verify(repository).findById(ruleId);
    }

    @Test
    void shouldReturnErrorWhenValidateDeleteRuleIdIsNull() {
        final DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(null);

        final String result = service.validateDeleteJudiciaryAvailabilityRule(request);

        assertNotNull(result);
        assertTrue(result.contains("Rule ID is required"));
        verify(repository, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.anyString());
    }


    @Test
    void shouldReturnErrorWhenValidateDeleteRuleAppliedToADSessionType() {
        final String ruleId = randomUUID().toString();
        final String sessionId = randomUUID().toString();
        final DeleteJudiciaryAvailabilityRuleRequest request = new DeleteJudiciaryAvailabilityRuleRequest();
        request.setRuleId(ruleId);

        final JudiciaryAvailabilityRule rule = createRule(judiciaryId,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                Arrays.asList(AvailabilityDayOfWeek.MONDAY));
        rule.setId(ruleId);
        rule.setSessionType(SessionType.AD);

        // Monday, January 5, 2026 (January 6 is Tuesday)
        final LocalDate sessionDate = LocalDate.of(2026, 1, 5);
        final Object[] sessionData = {sessionId, sessionDate, "AM"};

        when(repository.findById(ruleId)).thenReturn(java.util.Optional.of(rule));
        // AD rule should match AM, PM, or AD sessions
        final List<Object[]> sessionListAD = new ArrayList<>();
        sessionListAD.add(sessionData);
        when(courtScheduleJudiciaryRepository.findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                judiciaryId, rule.getFromDate(), rule.getToDate(), "AD"))
                .thenReturn(sessionListAD);

        final String result = service.validateDeleteJudiciaryAvailabilityRule(request);

        assertNotNull(result);
        assertTrue(result.contains("being used in a session"));
        verify(repository).findById(ruleId);
        verify(courtScheduleJudiciaryRepository).findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                judiciaryId, rule.getFromDate(), rule.getToDate(), "AD");
    }

    @Test
    void coversSessionTypeAdCoversAllSessions() {
        assertTrue(JudiciaryAvailabilityService.coversSessionType(SessionType.AD, SessionType.AM));
        assertTrue(JudiciaryAvailabilityService.coversSessionType(SessionType.AD, SessionType.PM));
        assertTrue(JudiciaryAvailabilityService.coversSessionType(SessionType.AD, SessionType.AD));
    }

    @Test
    void coversSessionTypeAmPmOnlyMatchSelf() {
        assertTrue(JudiciaryAvailabilityService.coversSessionType(SessionType.AM, SessionType.AM));
        assertFalse(JudiciaryAvailabilityService.coversSessionType(SessionType.AM, SessionType.PM));
        assertTrue(JudiciaryAvailabilityService.coversSessionType(SessionType.PM, SessionType.PM));
    }

    @Test
    void coversSessionTypeNullRuleTreatedAsAd() {
        assertTrue(JudiciaryAvailabilityService.coversSessionType(null, SessionType.PM));
    }

    @Test
    void findAvailableJudiciaryIdsFromListKeepsAllWhenNoRulesLoaded() {
        when(repository.findRulesByDateRangeAndJudiciaryIds(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                HOUSE,
                Arrays.asList("a", "b")))
                .thenReturn(Collections.emptyList());

        final List<String> out = service.findAvailableJudiciaryIdsFromList(
                Arrays.asList("a", "b"),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                HOUSE,
                Collections.singletonList(new DateSessionType(LocalDate.of(2026, 1, 5), null)),
                false);

        assertThat(out, is(Arrays.asList("a", "b")));
    }

    @Test
    void findAvailableJudiciaryIdsFromListExcludesWhenSessionDoesNotMatch() {
        final String jid = "j1";
        final JudiciaryAvailabilityRule pmRule = createRule(
                jid,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                List.of(AvailabilityDayOfWeek.MONDAY)
        );
        pmRule.setSessionType(SessionType.PM);

        when(repository.findRulesByDateRangeAndJudiciaryIds(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                HOUSE,
                Collections.singletonList(jid)))
                .thenReturn(Collections.singletonList(pmRule));

        final List<String> out = service.findAvailableJudiciaryIdsFromList(
                Collections.singletonList(jid),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                HOUSE,
                Collections.singletonList(new DateSessionType(LocalDate.of(2026, 1, 5), SessionType.AM)),
                true);

        assertTrue(out.isEmpty());
    }

    @Test
    void findAvailableJudiciaryIdsFromListKeepsWhenRuleDayMatchesDate() {
        final String jid = "j1";
        final JudiciaryAvailabilityRule amRule = createRule(
                jid,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                List.of(AvailabilityDayOfWeek.MONDAY)
        );
        amRule.setSessionType(SessionType.AM);

        when(repository.findRulesByDateRangeAndJudiciaryIds(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                HOUSE,
                Collections.singletonList(jid)))
                .thenReturn(Collections.singletonList(amRule));

        final List<String> out = service.findAvailableJudiciaryIdsFromList(
                Collections.singletonList(jid),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                HOUSE,
                Collections.singletonList(new DateSessionType(LocalDate.of(2026, 1, 5), SessionType.AM)),
                true);

        assertThat(out, is(Collections.singletonList(jid)));
    }

    // Test-local fixtures standing in for the GET-query-param bundles that used to be hand-written
    // domain request POJOs (FindJudiciaryAvailabilityRequest / FindJudiciaryAvailabilityRuleRequest /
    // GetJudiciaryAvailabilityRuleRequest). Those endpoints now take individual query params
    // directly on the service methods (no OpenAPI schema for a GET's query-param bundle) — these
    // fixtures just let the existing test bodies keep building a "request" object with setters,
    // unpacked into the primitive service-call args at each call site.
    private static final class FindAvailabilityRequestFixture {
        private LocalDate startDate;
        private LocalDate endDate;
        private String courtHouseId;
        private String judiciaryId;

        private LocalDate getStartDate() { return startDate; }
        private void setStartDate(final LocalDate v) { startDate = v; }
        private LocalDate getEndDate() { return endDate; }
        private void setEndDate(final LocalDate v) { endDate = v; }
        private String getCourtHouseId() { return courtHouseId; }
        private void setCourtHouseId(final String v) { courtHouseId = v; }
        private String getJudiciaryId() { return judiciaryId; }
        private void setJudiciaryId(final String v) { judiciaryId = v; }
    }

    private static final class FindRuleAvailabilityRequestFixture {
        private LocalDate startDate;
        private LocalDate endDate;
        private String courtHouseId;
        private String judiciaryId;
        private Integer pageSize;
        private Integer pageNumber;
        private Boolean withJudiciary;

        private LocalDate getStartDate() { return startDate; }
        private void setStartDate(final LocalDate v) { startDate = v; }
        private LocalDate getEndDate() { return endDate; }
        private void setEndDate(final LocalDate v) { endDate = v; }
        private String getCourtHouseId() { return courtHouseId; }
        private void setCourtHouseId(final String v) { courtHouseId = v; }
        private String getJudiciaryId() { return judiciaryId; }
        private void setJudiciaryId(final String v) { judiciaryId = v; }
        private Integer getPageSize() { return pageSize; }
        private void setPageSize(final Integer v) { pageSize = v; }
        private Integer getPageNumber() { return pageNumber; }
        private void setPageNumber(final Integer v) { pageNumber = v; }
        private Boolean isWithJudiciary() { return withJudiciary; }
        private void setWithJudiciary(final Boolean v) { withJudiciary = v; }
    }

    private static final class GetRuleAvailabilityRequestFixture {
        private String ruleId;
        private Boolean withJudiciary;

        private String getRuleId() { return ruleId; }
        private void setRuleId(final String v) { ruleId = v; }
        private Boolean isWithJudiciary() { return withJudiciary; }
        private void setWithJudiciary(final Boolean v) { withJudiciary = v; }
    }
}

