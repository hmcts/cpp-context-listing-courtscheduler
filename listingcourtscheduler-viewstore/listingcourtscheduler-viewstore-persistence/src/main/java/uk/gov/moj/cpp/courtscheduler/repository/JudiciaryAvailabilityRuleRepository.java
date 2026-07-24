package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;

import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Migrated from DeltaSpike's {@code AbstractEntityRepository<JudiciaryAvailabilityRule, String>}
 * to a Spring Data JPA interface. Standard CRUD comes from {@link JpaRepository}; the two
 * dynamic-query methods that need an {@code EntityManager} (Criteria API + paged native
 * SQL with a window function) live in the package-private
 * {@link JudiciaryAvailabilityRuleRepositoryCustom} fragment + its {@code …Impl} class —
 * both colocated in this file so a single read shows the whole repository.
 */
@Repository
public interface JudiciaryAvailabilityRuleRepository
        extends JpaRepository<JudiciaryAvailabilityRule, String>, JudiciaryAvailabilityRuleRepositoryCustom {

    // ---------------------------------------------------------------------
    //  Backwards-compatible alias for DeltaSpike's auto-generated {@code findBy(K)}.
    //  JpaRepository provides {@code findById(K)} which returns Optional; legacy callers
    //  expect the raw entity (or {@code null}).
    // ---------------------------------------------------------------------

    default JudiciaryAvailabilityRule findBy(final String id) {
        return id == null ? null : findById(id).orElse(null);
    }

    default void remove(final JudiciaryAvailabilityRule rule) {
        delete(rule);
    }
}

/**
 * Spring Data {@code Custom} fragment for {@link JudiciaryAvailabilityRuleRepository}. Holds
 * the two methods that build their query dynamically — one with the JPA Criteria API,
 * the other with a native SQL window-function query for paginated results.
 */
interface JudiciaryAvailabilityRuleRepositoryCustom {

    /**
     * Find all rules that overlap with {@code [queryStartDate, queryEndDate]}, optionally
     * filtered by {@code courtHouseId} and/or {@code judiciaryId}. Built with the JPA
     * Criteria API so the predicates can be added conditionally.
     */
    List<JudiciaryAvailabilityRule> findRulesByDateRange(LocalDate queryStartDate,
                                                         LocalDate queryEndDate,
                                                         String courtHouseId,
                                                         String judiciaryId);

    /**
     * Same overlap predicate as {@link #findRulesByDateRange} but paginated. Returns a
     * {@code (totalCount, page)} pair using a single SQL call — a {@code COUNT(*) OVER()}
     * window in the inner SELECT yields the unbounded row count, and a LEFT JOIN onto
     * {@code judiciary_availability_rule_repeat_day} and {@code judiciary_unavailability}
     * pulls the rule's children in the same round-trip.
     */
    Map.Entry<Integer, List<JudiciaryAvailabilityRule>> findRulesByDateRangeWithPagination(LocalDate queryStartDate,
                                                                                            LocalDate queryEndDate,
                                                                                            String courtHouseId,
                                                                                            String judiciaryId,
                                                                                            int pageSize,
                                                                                            int pageNumber);

    /**
     * Same overlap semantics as {@link #findRulesByDateRange}, restricted to the given judiciary IDs.
     */
    List<JudiciaryAvailabilityRule> findRulesByDateRangeAndJudiciaryIds(LocalDate queryStartDate,
                                                                        LocalDate queryEndDate,
                                                                        String courtHouseId,
                                                                        List<String> judiciaryIds);
}

/**
 * Spring Data picks this up by the {@code …Impl} naming convention as the implementation
 * of {@link JudiciaryAvailabilityRuleRepositoryCustom}.
 */
class JudiciaryAvailabilityRuleRepositoryImpl implements JudiciaryAvailabilityRuleRepositoryCustom {

    private static final String COURT_HOUSE_ID = "courtHouseId";
    private static final String JUDICIARY_ID = "judiciaryId";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<JudiciaryAvailabilityRule> findRulesByDateRange(
            final LocalDate queryStartDate,
            final LocalDate queryEndDate,
            final String courtHouseId,
            final String judiciaryId) {

        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<JudiciaryAvailabilityRule> cq = cb.createQuery(JudiciaryAvailabilityRule.class);
        final Root<JudiciaryAvailabilityRule> root = cq.from(JudiciaryAvailabilityRule.class);

        // Rule overlaps if: rule.fromDate <= queryEndDate AND rule.toDate >= queryStartDate
        final Predicate fromDatePredicate = cb.lessThanOrEqualTo(root.get("fromDate"), queryEndDate);
        final Predicate toDatePredicate = cb.greaterThanOrEqualTo(root.get("toDate"), queryStartDate);
        final Predicate dateRangePredicate = cb.and(fromDatePredicate, toDatePredicate);

        final List<Predicate> additionalPredicates = new ArrayList<>();
        if (isNotEmpty(courtHouseId)) {
            additionalPredicates.add(cb.equal(root.get(COURT_HOUSE_ID), courtHouseId));
        }
        if (isNotEmpty(judiciaryId)) {
            additionalPredicates.add(cb.equal(root.get(JUDICIARY_ID), judiciaryId));
        }

        if (additionalPredicates.isEmpty()) {
            cq.where(dateRangePredicate);
        } else {
            additionalPredicates.add(0, dateRangePredicate);
            cq.where(cb.and(additionalPredicates.toArray(new Predicate[0])));
        }

        final TypedQuery<JudiciaryAvailabilityRule> query = entityManager.createQuery(cq);
        return query.getResultList();
    }

    @Override
    public List<JudiciaryAvailabilityRule> findRulesByDateRangeAndJudiciaryIds(
            final LocalDate queryStartDate,
            final LocalDate queryEndDate,
            final String courtHouseId,
            final List<String> judiciaryIds) {

        if (judiciaryIds == null || judiciaryIds.isEmpty()) {
            return Collections.emptyList();
        }

        final CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        final CriteriaQuery<JudiciaryAvailabilityRule> cq = cb.createQuery(JudiciaryAvailabilityRule.class);
        final Root<JudiciaryAvailabilityRule> root = cq.from(JudiciaryAvailabilityRule.class);

        final Predicate fromDatePredicate = cb.lessThanOrEqualTo(root.get("fromDate"), queryEndDate);
        final Predicate toDatePredicate = cb.greaterThanOrEqualTo(root.get("toDate"), queryStartDate);
        final Predicate dateRangePredicate = cb.and(fromDatePredicate, toDatePredicate);
        final Predicate inJudiciary = root.get(JUDICIARY_ID).in(judiciaryIds);

        final List<Predicate> predicates = new ArrayList<>();
        predicates.add(dateRangePredicate);
        predicates.add(inJudiciary);

        if (isNotEmpty(courtHouseId)) {
            predicates.add(cb.equal(root.get(COURT_HOUSE_ID), courtHouseId));
        }

        cq.where(cb.and(predicates.toArray(new Predicate[0])));

        final TypedQuery<JudiciaryAvailabilityRule> query = entityManager.createQuery(cq);
        return query.getResultList();
    }

    @Override
    public Map.Entry<Integer, List<JudiciaryAvailabilityRule>> findRulesByDateRangeWithPagination(
            final LocalDate queryStartDate,
            final LocalDate queryEndDate,
            final String courtHouseId,
            final String judiciaryId,
            final int pageSize,
            final int pageNumber) {

        final String queryString = buildQueryString(courtHouseId, judiciaryId);
        final Query nativeQuery = entityManager.createNativeQuery(queryString);
        setQueryParameters(nativeQuery, queryStartDate, queryEndDate, courtHouseId, judiciaryId, pageSize, pageNumber);

        @SuppressWarnings("unchecked")
        final List<Object[]> resultList = nativeQuery.getResultList();

        if (resultList.isEmpty()) {
            return new AbstractMap.SimpleEntry<>(0, new ArrayList<>());
        }

        final int totalCount = extractTotalCount(resultList);
        final List<JudiciaryAvailabilityRule> rules = processResultRows(resultList);
        return new AbstractMap.SimpleEntry<>(totalCount, rules);
    }

    private String buildQueryString(final String courtHouseId, final String judiciaryId) {
        final StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT r.id, r.judiciary_id, r.court_house_id, r.from_date, r.to_date, ");
        queryBuilder.append("r.session_type, r.created_on, r.updated_on, r.totalCount, ");
        queryBuilder.append("rd.rule_id as rd_rule_id, rd.day_of_week as rd_day_of_week, ");
        queryBuilder.append("u.id as u_id, u.availability_rule_id as u_availability_rule_id, ");
        queryBuilder.append("u.from_date as u_from_date, u.to_date as u_to_date, u.reason as u_reason, ");
        queryBuilder.append("u.created_on as u_created_on, u.updated_on as u_updated_on ");
        queryBuilder.append("FROM (SELECT *, COUNT(*) OVER() as totalCount FROM judiciary_availability_rule ");
        queryBuilder.append("WHERE from_date <= :queryEndDate AND to_date >= :queryStartDate ");

        if (isNotEmpty(courtHouseId)) {
            queryBuilder.append("AND court_house_id = :courtHouseId ");
        }
        if (isNotEmpty(judiciaryId)) {
            queryBuilder.append("AND judiciary_id = :judiciaryId ");
        }

        queryBuilder.append("ORDER BY from_date ASC ");
        queryBuilder.append("LIMIT :pageSize OFFSET :offset) r ");
        queryBuilder.append("LEFT JOIN judiciary_availability_rule_repeat_day rd ON r.id = rd.rule_id ");
        queryBuilder.append("LEFT JOIN judiciary_unavailability u ON r.id = u.availability_rule_id ");
        queryBuilder.append("ORDER BY r.from_date ASC, rd.day_of_week ASC");

        return queryBuilder.toString();
    }

    private void setQueryParameters(final Query query,
                                    final LocalDate queryStartDate,
                                    final LocalDate queryEndDate,
                                    final String courtHouseId,
                                    final String judiciaryId,
                                    final int pageSize,
                                    final int pageNumber) {
        query.setParameter("queryStartDate", queryStartDate);
        query.setParameter("queryEndDate", queryEndDate);

        if (isNotEmpty(courtHouseId)) {
            query.setParameter(COURT_HOUSE_ID, courtHouseId);
        }
        if (isNotEmpty(judiciaryId)) {
            query.setParameter(JUDICIARY_ID, judiciaryId);
        }

        query.setParameter("pageSize", pageSize);
        query.setParameter("offset", (pageNumber - 1) * pageSize);
    }

    private int extractTotalCount(final List<Object[]> resultList) {
        // r.totalCount is the 9th projected column (index 8).
        return ((Number) resultList.get(0)[8]).intValue();
    }

    private List<JudiciaryAvailabilityRule> processResultRows(final List<Object[]> resultList) {
        final Map<String, JudiciaryAvailabilityRule> rulesMap = new LinkedHashMap<>();
        final Map<String, Integer> ruleOrder = new HashMap<>();
        int orderIndex = 0;

        for (final Object[] row : resultList) {
            final String ruleId = (String) row[0];

            if (!rulesMap.containsKey(ruleId)) {
                rulesMap.put(ruleId, createRuleFromRow(row));
                ruleOrder.put(ruleId, orderIndex++);
            }

            final JudiciaryAvailabilityRule rule = rulesMap.get(ruleId);
            addRepeatDayToRule(rule, row);
            addUnavailabilityToRule(rule, row);
        }

        return convertToOrderedList(rulesMap, ruleOrder);
    }

    private JudiciaryAvailabilityRule createRuleFromRow(final Object[] row) {
        final JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
        rule.setId((String) row[0]);
        rule.setJudiciaryId((String) row[1]);
        rule.setCourtHouseId((String) row[2]);
        rule.setFromDate(toLocalDate(row[3]));
        rule.setToDate(toLocalDate(row[4]));

        if (row[5] != null) {
            rule.setSessionType(uk.gov.moj.cpp.courtscheduler.domain.SessionType.valueOf((String) row[5]));
        }

        rule.setCreatedOn(toUtilDate(row[6]));
        rule.setUpdatedOn(toUtilDate(row[7]));
        rule.setRepeatDays(new ArrayList<>());
        rule.setUnavailabilities(new ArrayList<>());
        return rule;
    }

    private void addRepeatDayToRule(final JudiciaryAvailabilityRule rule, final Object[] row) {
        // rd columns: rule_id (index 9), day_of_week (index 10)
        if (row[10] == null) {
            return;
        }
        final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay repeatDay =
                new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay(
                        uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.valueOf((String) row[10]));
        if (!rule.getRepeatDays().contains(repeatDay)) {
            rule.getRepeatDays().add(repeatDay);
        }
    }

    private void addUnavailabilityToRule(final JudiciaryAvailabilityRule rule, final Object[] row) {
        // u columns: id (11), availability_rule_id (12), from_date (13), to_date (14),
        // reason (15), created_on (16), updated_on (17)
        if (row[11] == null) {
            return;
        }
        final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability = createUnavailabilityFromRow(rule, row);
        if (rule.getUnavailabilities().stream().noneMatch(u -> u.getId().equals(unavailability.getId()))) {
            rule.getUnavailabilities().add(unavailability);
        }
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability createUnavailabilityFromRow(
            final JudiciaryAvailabilityRule rule, final Object[] row) {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability =
                new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability();
        unavailability.setId((String) row[11]);
        unavailability.setRule(rule);
        unavailability.setFromDate(toLocalDate(row[13]));
        unavailability.setToDate(toLocalDate(row[14]));

        if (row[15] != null) {
            unavailability.setReason(uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason.valueOf((String) row[15]));
        }

        unavailability.setCreatedOn(toUtilDate(row[16]));
        unavailability.setUpdatedOn(toUtilDate(row[17]));
        return unavailability;
    }

    private List<JudiciaryAvailabilityRule> convertToOrderedList(
            final Map<String, JudiciaryAvailabilityRule> rulesMap,
            final Map<String, Integer> ruleOrder) {
        final List<JudiciaryAvailabilityRule> rules = new ArrayList<>(rulesMap.values());
        rules.sort((r1, r2) -> Integer.compare(
                ruleOrder.getOrDefault(r1.getId(), Integer.MAX_VALUE),
                ruleOrder.getOrDefault(r2.getId(), Integer.MAX_VALUE)));
        return rules;
    }

    private static boolean isNotEmpty(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * Hibernate 7 + the recent Postgres JDBC driver return DATE columns as
     * {@link LocalDate} from native queries, but legacy code expected
     * {@link java.sql.Date}. Accept both shapes — including the older
     * {@code java.util.Date} the previous driver returned.
     */
    private static LocalDate toLocalDate(final Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof LocalDate ld) {
            return ld;
        }
        if (item instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (item instanceof java.util.Date utilDate) {
            return new java.sql.Date(utilDate.getTime()).toLocalDate();
        }
        throw new IllegalArgumentException("Unsupported date shape from native query: " + item.getClass());
    }

    /** Same idea as {@link #toLocalDate} but for {@code created_on}/{@code updated_on}-style columns. */
    private static java.util.Date toUtilDate(final Object item) {
        if (item == null) {
            return null;
        }
        if (item instanceof java.util.Date d) {
            return d;
        }
        if (item instanceof java.time.Instant instant) {
            return java.util.Date.from(instant);
        }
        if (item instanceof java.time.LocalDateTime ldt) {
            return java.util.Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        if (item instanceof java.time.OffsetDateTime odt) {
            return java.util.Date.from(odt.toInstant());
        }
        throw new IllegalArgumentException("Unsupported timestamp shape from native query: " + item.getClass());
    }
}
