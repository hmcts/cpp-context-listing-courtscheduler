package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test for JudiciaryAvailabilityRuleRepository.
 * Tests the repository methods using a real EntityManager and database.
 */

class JudiciaryAvailabilityRuleRepositoryTest extends uk.gov.moj.cpp.courtscheduler.repository.AbstractRepositoryTest {

    @Autowired
    private JudiciaryAvailabilityRuleRepository repository;

    @Autowired
    private EntityManager entityManager;

    private List<String> createdRuleIds = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        createdRuleIds.clear();
    }

    // tearDown removed: @DataJpaTest is transactional + auto-rolls-back at the end of
    // every test method, so the explicit cleanup the legacy CDI-based test did is now
    // unnecessary. The original {@code entityManager.getTransaction().begin()} call
    // also conflicts with the Spring-managed transaction context.

    @Test
    void shouldFindRulesByDateRange() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        final JudiciaryAvailabilityRule rule = createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRange(
                startDate, endDate, null, null);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(rule.getId())));
    }

    @Test
    void shouldReturnEmptyForFindRulesByDateRangeAndJudiciaryIdsWhenIdListEmpty() {
        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                Collections.emptyList());
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldReturnEmptyForFindRulesByDateRangeAndJudiciaryIdsWhenIdListNull() {
        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                null);
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldFindRulesByDateRangeAndJudiciaryIds() {
        final LocalDate startDate = LocalDate.of(2026, 2, 1);
        final LocalDate endDate = LocalDate.of(2026, 2, 28);
        final String jMatch = randomUUID().toString();
        final String jOther = randomUUID().toString();

        final JudiciaryAvailabilityRule ruleMatch = createAndSaveRule(
                randomUUID().toString(),
                jMatch,
                randomUUID().toString(),
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.MONDAY)
        );
        createAndSaveRule(
                randomUUID().toString(),
                jOther,
                randomUUID().toString(),
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.TUESDAY)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                startDate, endDate, null, Collections.singletonList(jMatch));

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(ruleMatch.getId())));
        assertTrue(result.stream().allMatch(r -> jMatch.equals(r.getJudiciaryId())));
    }

    @Test
    void shouldFindRulesByDateRangeAndJudiciaryIdsWithCourtHouseFilter() {
        final LocalDate startDate = LocalDate.of(2026, 2, 1);
        final LocalDate endDate = LocalDate.of(2026, 2, 28);
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();

        final JudiciaryAvailabilityRule included = createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseId,
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.MONDAY)
        );

        createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                randomUUID().toString(),
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.TUESDAY)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                startDate, endDate, courtHouseId, Collections.singletonList(judiciaryId));

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(included.getId())));
        assertTrue(result.stream().allMatch(r -> courtHouseId.equals(r.getCourtHouseId())));
    }

    @Test
    void shouldIgnoreBlankCourtHouseIdWhenFindingByJudiciaryIds() {
        final LocalDate startDate = LocalDate.of(2026, 3, 1);
        final LocalDate endDate = LocalDate.of(2026, 3, 31);
        final String judiciaryId = randomUUID().toString();
        final String courtHouseA = randomUUID().toString();
        final String courtHouseB = randomUUID().toString();

        final JudiciaryAvailabilityRule onHouseA = createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseA,
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.MONDAY)
        );
        createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseB,
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.TUESDAY)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                startDate, endDate, "   ", Collections.singletonList(judiciaryId));

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(onHouseA.getId())));
        assertTrue(result.stream().anyMatch(r -> courtHouseB.equals(r.getCourtHouseId())));
    }

    @Test
    void shouldFindRulesByDateRangeWithCourtHouseIdFilter() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        final String courtHouseId = randomUUID().toString();

        final JudiciaryAvailabilityRule rule = createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                courtHouseId,
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRange(
                startDate, endDate, courtHouseId, null);

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(rule.getId())));
    }

    @Test
    void shouldFindRulesByDateRangeWithJudiciaryIdFilter() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);
        final String judiciaryId = randomUUID().toString();

        final JudiciaryAvailabilityRule rule = createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRange(
                startDate, endDate, null, judiciaryId);

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(rule.getId())));
    }

    @Test
    void shouldFindRulesByDateRangeIgnoringWhitespaceCourtHouseId() {
        final LocalDate startDate = LocalDate.of(2026, 4, 1);
        final LocalDate endDate = LocalDate.of(2026, 4, 30);
        final String judiciaryId = randomUUID().toString();
        final String courtHouseA = randomUUID().toString();
        final String courtHouseB = randomUUID().toString();

        final JudiciaryAvailabilityRule onA = createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseA,
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );
        createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseB,
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.TUESDAY)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRange(
                startDate, endDate, "   ", judiciaryId);

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(onA.getId())));
        assertTrue(result.stream().anyMatch(r -> courtHouseB.equals(r.getCourtHouseId())));
    }

    @Test
    void shouldFindRulesByDateRangeWithPaginationAndCourtHouseAndJudiciaryFilters() {
        final LocalDate startDate = LocalDate.of(2026, 5, 1);
        final LocalDate endDate = LocalDate.of(2026, 5, 31);
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();

        final JudiciaryAvailabilityRule match = createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseId,
                startDate,
                endDate,
                Collections.singletonList(AvailabilityDayOfWeek.MONDAY)
        );
        createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                courtHouseId,
                startDate,
                endDate,
                Collections.singletonList(AvailabilityDayOfWeek.TUESDAY)
        );

        final Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result =
                repository.findRulesByDateRangeWithPagination(
                        startDate, endDate, courtHouseId, judiciaryId, 10, 1);

        assertNotNull(result);
        assertThat(result.getKey(), is(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        assertTrue(result.getValue().stream().anyMatch(r -> r.getId().equals(match.getId())));
        assertTrue(result.getValue().stream().allMatch(r -> judiciaryId.equals(r.getJudiciaryId())));
        assertTrue(result.getValue().stream().allMatch(r -> courtHouseId.equals(r.getCourtHouseId())));
    }

    @Test
    void shouldFindRulesByDateRangeWithPagination() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.TUESDAY)
        );

        final Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1);

        assertNotNull(result);
        assertThat(result.getKey(), is(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        assertThat(result.getValue().size(), is(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void shouldFindRulesByDateRangeWithPaginationEmptyResult() {
        final LocalDate startDate = LocalDate.of(2099, 1, 1);
        final LocalDate endDate = LocalDate.of(2099, 1, 31);

        final Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1);

        assertNotNull(result);
        assertThat(result.getKey(), is(0));
        assertThat(result.getValue().size(), is(0));
    }

    @Test
    void shouldFindRulesByDateRangeWithPaginationWithRepeatDays() {
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        final JudiciaryAvailabilityRule rule = createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY, AvailabilityDayOfWeek.WEDNESDAY)
        );

        final Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1);

        assertNotNull(result);
        assertThat(result.getValue().size(), is(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        
        // Find the rule we created
        final JudiciaryAvailabilityRule foundRule = result.getValue().stream()
                .filter(r -> r.getId().equals(rule.getId()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(foundRule);
        assertThat(foundRule.getRepeatDays().size(), is(3));
    }

    private JudiciaryAvailabilityRule createAndSaveRule(final String ruleId, final String judiciaryId,
                                                         final String courtHouseId, final LocalDate fromDate,
                                                         final LocalDate toDate, final List<AvailabilityDayOfWeek> repeatDays) {
        // Spring's @DataJpaTest provides the transaction; the legacy explicit
        // EntityTransaction.begin()/commit() conflicted with that.
        JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
        rule.setId(ruleId);
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setFromDate(fromDate);
        rule.setToDate(toDate);
        // session_type was added with NOT NULL + default 'AD' in changeset 049 — set
        // it explicitly here because the JPA mapping doesn't respect the SQL default.
        rule.setSessionType(uk.gov.moj.cpp.courtscheduler.domain.SessionType.AD);
        rule.setRepeatDays(new ArrayList<>());
        rule.setUnavailabilities(new ArrayList<>());

        repeatDays.stream()
                .map(JudiciaryAvailabilityRuleRepeatDay::new)
                .forEach(rule.getRepeatDays()::add);

        rule = repository.save(rule);
        createdRuleIds.add(ruleId);
        entityManager.flush();
        return rule;
    }
}
