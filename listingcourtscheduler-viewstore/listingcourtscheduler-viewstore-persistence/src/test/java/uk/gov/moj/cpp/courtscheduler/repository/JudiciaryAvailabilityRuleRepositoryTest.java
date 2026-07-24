package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
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

import org.junit.jupiter.api.AfterEach;
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
    public void shouldFindRulesByDateRange() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);

        JudiciaryAvailabilityRule rule = createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday)
        );

        List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRange(
                startDate, endDate, null, null);

        assertNotNull(result);
        assertTrue(result.size() >= 1);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(rule.getId())));
    }

    @Test
    public void shouldReturnEmptyForFindRulesByDateRangeAndJudiciaryIdsWhenIdListEmpty() {
        List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                Collections.emptyList());
        assertThat(result.size(), is(0));
    }

    @Test
    public void shouldReturnEmptyForFindRulesByDateRangeAndJudiciaryIdsWhenIdListNull() {
        List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                null);
        assertThat(result.size(), is(0));
    }

    @Test
    public void shouldFindRulesByDateRangeAndJudiciaryIds() {
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 2, 28);
        String jMatch = randomUUID().toString();
        String jOther = randomUUID().toString();

        JudiciaryAvailabilityRule ruleMatch = createAndSaveRule(
                randomUUID().toString(),
                jMatch,
                randomUUID().toString(),
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.Monday)
        );
        createAndSaveRule(
                randomUUID().toString(),
                jOther,
                randomUUID().toString(),
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.Tuesday)
        );

        List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                startDate, endDate, null, Collections.singletonList(jMatch));

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(ruleMatch.getId())));
        assertTrue(result.stream().allMatch(r -> jMatch.equals(r.getJudiciaryId())));
    }

    @Test
    public void shouldFindRulesByDateRangeAndJudiciaryIdsWithCourtHouseFilter() {
        LocalDate startDate = LocalDate.of(2026, 2, 1);
        LocalDate endDate = LocalDate.of(2026, 2, 28);
        String judiciaryId = randomUUID().toString();
        String courtHouseId = randomUUID().toString();

        JudiciaryAvailabilityRule included = createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseId,
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.Monday)
        );

        createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                randomUUID().toString(),
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.Tuesday)
        );

        List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                startDate, endDate, courtHouseId, Collections.singletonList(judiciaryId));

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(included.getId())));
        assertTrue(result.stream().allMatch(r -> courtHouseId.equals(r.getCourtHouseId())));
    }

    @Test
    public void shouldIgnoreBlankCourtHouseIdWhenFindingByJudiciaryIds() {
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
                List.of(AvailabilityDayOfWeek.Monday)
        );
        createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseB,
                startDate,
                endDate,
                List.of(AvailabilityDayOfWeek.Tuesday)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRangeAndJudiciaryIds(
                startDate, endDate, "   ", Collections.singletonList(judiciaryId));

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(onHouseA.getId())));
        assertTrue(result.stream().anyMatch(r -> courtHouseB.equals(r.getCourtHouseId())));
    }

    @Test
    public void shouldFindRulesByDateRangeWithCourtHouseIdFilter() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        String courtHouseId = randomUUID().toString();

        JudiciaryAvailabilityRule rule = createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                courtHouseId,
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRange(
                startDate, endDate, courtHouseId, null);

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(rule.getId())));
    }

    @Test
    public void shouldFindRulesByDateRangeWithJudiciaryIdFilter() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);
        String judiciaryId = randomUUID().toString();

        JudiciaryAvailabilityRule rule = createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRange(
                startDate, endDate, null, judiciaryId);

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(rule.getId())));
    }

    @Test
    public void shouldFindRulesByDateRangeIgnoringWhitespaceCourtHouseId() {
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
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );
        createAndSaveRule(
                randomUUID().toString(),
                judiciaryId,
                courtHouseB,
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Tuesday)
        );

        final List<JudiciaryAvailabilityRule> result = repository.findRulesByDateRange(
                startDate, endDate, "   ", judiciaryId);

        assertNotNull(result);
        assertTrue(result.stream().anyMatch(r -> r.getId().equals(onA.getId())));
        assertTrue(result.stream().anyMatch(r -> courtHouseB.equals(r.getCourtHouseId())));
    }

    @Test
    public void shouldFindRulesByDateRangeWithPaginationAndCourtHouseAndJudiciaryFilters() {
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
                Collections.singletonList(AvailabilityDayOfWeek.Monday)
        );
        createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                courtHouseId,
                startDate,
                endDate,
                Collections.singletonList(AvailabilityDayOfWeek.Tuesday)
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
    public void shouldFindRulesByDateRangeWithPagination() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);

        createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Tuesday)
        );

        Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1);

        assertNotNull(result);
        assertThat(result.getKey(), is(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        assertThat(result.getValue().size(), is(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    public void shouldFindRulesByDateRangeWithPaginationEmptyResult() {
        LocalDate startDate = LocalDate.of(2099, 1, 1);
        LocalDate endDate = LocalDate.of(2099, 1, 31);

        Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1);

        assertNotNull(result);
        assertThat(result.getKey(), is(0));
        assertThat(result.getValue().size(), is(0));
    }

    @Test
    public void shouldFindRulesByDateRangeWithPaginationWithRepeatDays() {
        LocalDate startDate = LocalDate.of(2026, 1, 1);
        LocalDate endDate = LocalDate.of(2026, 1, 31);

        JudiciaryAvailabilityRule rule = createAndSaveRule(
                randomUUID().toString(),
                randomUUID().toString(),
                randomUUID().toString(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday, AvailabilityDayOfWeek.Wednesday)
        );

        Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = 
                repository.findRulesByDateRangeWithPagination(startDate, endDate, null, null, 10, 1);

        assertNotNull(result);
        assertThat(result.getValue().size(), is(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        
        // Find the rule we created
        JudiciaryAvailabilityRule foundRule = result.getValue().stream()
                .filter(r -> r.getId().equals(rule.getId()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(foundRule);
        assertThat(foundRule.getRepeatDays().size(), is(3));
    }

    private JudiciaryAvailabilityRule createAndSaveRule(String ruleId, String judiciaryId,
                                                         String courtHouseId, LocalDate fromDate,
                                                         LocalDate toDate, List<AvailabilityDayOfWeek> repeatDays) {
        // Spring's @DataJpaTest provides the transaction; the legacy explicit
        // EntityTransaction.begin()/commit() conflicted with that.
        try {
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

            for (AvailabilityDayOfWeek day : repeatDays) {
                JudiciaryAvailabilityRuleRepeatDay repeatDay =
                        new JudiciaryAvailabilityRuleRepeatDay(day);
                rule.getRepeatDays().add(repeatDay);
            }

            rule = repository.save(rule);
            createdRuleIds.add(ruleId);
            entityManager.flush();
            return rule;
        } catch (Exception e) {
            throw e;
        }
    }
}
