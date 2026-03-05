package uk.gov.moj.cpp.courtscheduler.repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;

@Repository(forEntity = JudiciaryAvailabilityRule.class)
public abstract class JudiciaryAvailabilityRuleRepository extends AbstractEntityRepository<JudiciaryAvailabilityRule, String> {

    @Inject
    EntityManager entityManager;

    /**
     * Find all rules that overlap with the given date range.
     * A rule overlaps if: rule.fromDate <= queryEndDate AND rule.toDate >= queryStartDate
     * Optionally filter by courtHouseId and/or judiciaryId.
     */
    public List<JudiciaryAvailabilityRule> findRulesByDateRange(
            final LocalDate queryStartDate,
            final LocalDate queryEndDate,
            final String courtHouseId,
            final String judiciaryId) {

        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<JudiciaryAvailabilityRule> cq = cb.createQuery(JudiciaryAvailabilityRule.class);
        Root<JudiciaryAvailabilityRule> root = cq.from(JudiciaryAvailabilityRule.class);

        // Build predicates
        // Rule overlaps if: rule.fromDate <= queryEndDate AND rule.toDate >= queryStartDate
        Predicate fromDatePredicate = cb.lessThanOrEqualTo(root.get("fromDate"), queryEndDate);
        Predicate toDatePredicate = cb.greaterThanOrEqualTo(root.get("toDate"), queryStartDate);
        Predicate dateRangePredicate = cb.and(fromDatePredicate, toDatePredicate);

        List<Predicate> additionalPredicates = new ArrayList<>();
        
        if (courtHouseId != null && !courtHouseId.trim().isEmpty()) {
            additionalPredicates.add(cb.equal(root.get("courtHouseId"), courtHouseId));
        }
        
        if (judiciaryId != null && !judiciaryId.trim().isEmpty()) {
            additionalPredicates.add(cb.equal(root.get("judiciaryId"), judiciaryId));
        }

        if (!additionalPredicates.isEmpty()) {
            additionalPredicates.add(0, dateRangePredicate);
            cq.where(cb.and(additionalPredicates.toArray(new Predicate[0])));
        } else {
            cq.where(dateRangePredicate);
        }

        TypedQuery<JudiciaryAvailabilityRule> query = this.entityManager.createQuery(cq);
        return query.getResultList();
    }

    /**
     * Find all rules that overlap with the given date range with pagination.
     * A rule overlaps if: rule.fromDate <= queryEndDate AND rule.toDate >= queryStartDate
     * Optionally filter by courtHouseId and/or judiciaryId.
     * Returns a pair of (totalCount, paginatedResults).
     * Uses a single database call with LEFT JOIN to fetch full entities and relationships,
     * and COUNT(DISTINCT) OVER() window function to get total count of distinct rules.
     */
    public java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> findRulesByDateRangeWithPagination(
            final LocalDate queryStartDate,
            final LocalDate queryEndDate,
            final String courtHouseId,
            final String judiciaryId,
            final int pageSize,
            final int pageNumber) {

        final String queryString = buildQueryString(courtHouseId, judiciaryId);
        final javax.persistence.Query nativeQuery = this.entityManager.createNativeQuery(queryString);
        setQueryParameters(nativeQuery, queryStartDate, queryEndDate, courtHouseId, judiciaryId, pageSize, pageNumber);

        @SuppressWarnings("unchecked")
        final List<Object[]> resultList = nativeQuery.getResultList();

        if (resultList.isEmpty()) {
            return new java.util.AbstractMap.SimpleEntry<>(0, new ArrayList<>());
        }

        final int totalCount = extractTotalCount(resultList);
        final List<JudiciaryAvailabilityRule> rules = processResultRows(resultList);

        return new java.util.AbstractMap.SimpleEntry<>(totalCount, rules);
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

    private void setQueryParameters(final javax.persistence.Query query,
                                     final LocalDate queryStartDate,
                                     final LocalDate queryEndDate,
                                     final String courtHouseId,
                                     final String judiciaryId,
                                     final int pageSize,
                                     final int pageNumber) {
        query.setParameter("queryStartDate", queryStartDate);
        query.setParameter("queryEndDate", queryEndDate);
        
        if (isNotEmpty(courtHouseId)) {
            query.setParameter("courtHouseId", courtHouseId);
        }
        if (isNotEmpty(judiciaryId)) {
            query.setParameter("judiciaryId", judiciaryId);
        }
        
        query.setParameter("pageSize", pageSize);
        query.setParameter("offset", (pageNumber - 1) * pageSize);
    }

    private int extractTotalCount(final List<Object[]> resultList) {
        // Column order: r.id(0), r.judiciary_id(1), r.court_house_id(2), r.from_date(3), r.to_date(4),
        // r.session_type(5), r.created_on(6), r.updated_on(7), r.totalCount(8),
        // rd.rule_id(9), rd.day_of_week(10),
        // u.id(11), u.availability_rule_id(12), u.from_date(13), u.to_date(14), u.reason(15),
        // u.created_on(16), u.updated_on(17)
        return ((Number) resultList.get(0)[8]).intValue();
    }

    private List<JudiciaryAvailabilityRule> processResultRows(final List<Object[]> resultList) {
        final java.util.Map<String, JudiciaryAvailabilityRule> rulesMap = new java.util.LinkedHashMap<>();
        final java.util.Map<String, Integer> ruleOrder = new java.util.HashMap<>();
        int orderIndex = 0;

        for (Object[] row : resultList) {
            final String ruleId = (String) row[0];
            
            if (!rulesMap.containsKey(ruleId)) {
                final JudiciaryAvailabilityRule rule = createRuleFromRow(row);
                rulesMap.put(ruleId, rule);
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
        rule.setFromDate(((java.sql.Date) row[3]).toLocalDate());
        rule.setToDate(((java.sql.Date) row[4]).toLocalDate());
        
        if (row[5] != null) {
            rule.setSessionType(uk.gov.moj.cpp.courtscheduler.domain.SessionType.valueOf((String) row[5]));
        }
        
        rule.setCreatedOn((java.util.Date) row[6]);
        rule.setUpdatedOn((java.util.Date) row[7]);
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
        
        final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability =
                createUnavailabilityFromRow(rule, row);
        
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
        unavailability.setFromDate(((java.sql.Date) row[13]).toLocalDate());
        unavailability.setToDate(((java.sql.Date) row[14]).toLocalDate());
        
        if (row[15] != null) {
            unavailability.setReason(uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason.valueOf((String) row[15]));
        }
        
        unavailability.setCreatedOn((java.util.Date) row[16]);
        unavailability.setUpdatedOn((java.util.Date) row[17]);
        
        return unavailability;
    }

    private List<JudiciaryAvailabilityRule> convertToOrderedList(
            final java.util.Map<String, JudiciaryAvailabilityRule> rulesMap,
            final java.util.Map<String, Integer> ruleOrder) {
        final List<JudiciaryAvailabilityRule> rules = new ArrayList<>(rulesMap.values());
        rules.sort((r1, r2) -> Integer.compare(
                ruleOrder.getOrDefault(r1.getId(), Integer.MAX_VALUE),
                ruleOrder.getOrDefault(r2.getId(), Integer.MAX_VALUE)));
        return rules;
    }

    private boolean isNotEmpty(final String value) {
        return value != null && !value.trim().isEmpty();
    }
}

