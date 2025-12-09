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
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

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

        // Build native SQL query with LEFT JOIN to fetch rules, their repeat days, and unavailabilities
        // Use COUNT(*) OVER() windowing function in the subquery to get total count of distinct rules
        // Explicitly list columns to avoid duplicate aliases (both r and u have 'id' columns)
        final StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT r.id, r.judiciary_id, r.court_house_id, r.from_date, r.to_date, ");
        queryBuilder.append("r.recurring_type, r.session_type, r.created_on, r.updated_on, r.totalCount, ");
        queryBuilder.append("rd.rule_id as rd_rule_id, rd.day_of_week as rd_day_of_week, rd.day_index as rd_day_index, ");
        queryBuilder.append("u.id as u_id, u.availability_rule_id as u_availability_rule_id, ");
        queryBuilder.append("u.from_date as u_from_date, u.to_date as u_to_date, u.reason as u_reason, ");
        queryBuilder.append("u.created_on as u_created_on, u.updated_on as u_updated_on ");
        queryBuilder.append("FROM (SELECT *, COUNT(*) OVER() as totalCount FROM judiciary_availability_rule ");
        queryBuilder.append("WHERE from_date <= :queryEndDate AND to_date >= :queryStartDate ");
        if (courtHouseId != null && !courtHouseId.trim().isEmpty()) {
            queryBuilder.append("AND court_house_id = :courtHouseId ");
        }
        if (judiciaryId != null && !judiciaryId.trim().isEmpty()) {
            queryBuilder.append("AND judiciary_id = :judiciaryId ");
        }
        queryBuilder.append("ORDER BY from_date ASC ");
        queryBuilder.append("LIMIT :pageSize OFFSET :offset) r ");
        queryBuilder.append("LEFT JOIN judiciary_availability_rule_repeat_day rd ON r.id = rd.rule_id ");
        queryBuilder.append("LEFT JOIN judiciary_unavailability u ON r.id = u.availability_rule_id ");
        queryBuilder.append("ORDER BY r.from_date ASC, rd.day_of_week ASC");

        final javax.persistence.Query nativeQuery = this.entityManager.createNativeQuery(queryBuilder.toString());

        // Set parameters
        nativeQuery.setParameter("queryStartDate", queryStartDate);
        nativeQuery.setParameter("queryEndDate", queryEndDate);
        if (courtHouseId != null && !courtHouseId.trim().isEmpty()) {
            nativeQuery.setParameter("courtHouseId", courtHouseId);
        }
        if (judiciaryId != null && !judiciaryId.trim().isEmpty()) {
            nativeQuery.setParameter("judiciaryId", judiciaryId);
        }
        nativeQuery.setParameter("pageSize", pageSize);
        nativeQuery.setParameter("offset", (pageNumber - 1) * pageSize);

        @SuppressWarnings("unchecked")
        final List<Object[]> resultList = nativeQuery.getResultList();

        if (resultList.isEmpty()) {
            return new java.util.AbstractMap.SimpleEntry<>(0, new ArrayList<>());
        }

        // Extract total count from first row
        // Column order: r.id(0), r.judiciary_id(1), r.court_house_id(2), r.from_date(3), r.to_date(4),
        // r.recurring_type(5), r.session_type(6), r.created_on(7), r.updated_on(8), r.totalCount(9),
        // rd.rule_id(10), rd.day_of_week(11), rd.day_index(12),
        // u.id(13), u.availability_rule_id(14), u.from_date(15), u.to_date(16), u.reason(17),
        // u.created_on(18), u.updated_on(19)
        final int totalCount = ((Number) resultList.get(0)[9]).intValue();

        // Group rows by rule ID and build entities
        final java.util.Map<String, JudiciaryAvailabilityRule> rulesMap = new java.util.LinkedHashMap<>();
        final java.util.Map<String, Integer> ruleOrder = new java.util.HashMap<>();
        int orderIndex = 0;

        for (Object[] row : resultList) {
            final String ruleId = (String) row[0];
            
            if (!rulesMap.containsKey(ruleId)) {
                // Create new rule entity
                final JudiciaryAvailabilityRule rule = new JudiciaryAvailabilityRule();
                rule.setId(ruleId);
                rule.setJudiciaryId((String) row[1]);
                rule.setCourtHouseId((String) row[2]);
                rule.setFromDate(((java.sql.Date) row[3]).toLocalDate());
                rule.setToDate(((java.sql.Date) row[4]).toLocalDate());
                if (row[5] != null) {
                    rule.setRecurringType(uk.gov.moj.cpp.courtscheduler.domain.RecurringType.valueOf((String) row[5]));
                }
                if (row[6] != null) {
                    rule.setSessionType(uk.gov.moj.cpp.courtscheduler.domain.SessionType.valueOf((String) row[6]));
                }
                rule.setCreatedOn((java.util.Date) row[7]);
                rule.setUpdatedOn((java.util.Date) row[8]);
                rule.setRepeatDays(new ArrayList<>());
                rule.setUnavailabilities(new ArrayList<>());
                
                rulesMap.put(ruleId, rule);
                ruleOrder.put(ruleId, orderIndex++);
            }

            final JudiciaryAvailabilityRule rule = rulesMap.get(ruleId);

            // Add repeat day if present (LEFT JOIN may return null)
            // rd columns: rule_id (index 10), day_of_week (index 11), day_index (index 12)
            if (row[11] != null) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay repeatDay =
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay();
                repeatDay.setDayOfWeek(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.valueOf((String) row[11]));
                final Integer dayIndex = row[12] != null ? ((Number) row[12]).intValue() : 0;
                repeatDay.setIndex(dayIndex > 0 ? dayIndex : null);
                
                // Only add if not already present (avoid duplicates)
                if (!rule.getRepeatDays().contains(repeatDay)) {
                    rule.getRepeatDays().add(repeatDay);
                }
            }

            // Add unavailability if present (LEFT JOIN may return null)
            // u columns: id (13), availability_rule_id (14), from_date (15), to_date (16),
            // reason (17), created_on (18), updated_on (19)
            if (row[13] != null) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability =
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability();
                unavailability.setId((String) row[13]);
                unavailability.setRule(rule);
                unavailability.setFromDate(((java.sql.Date) row[15]).toLocalDate());
                unavailability.setToDate(((java.sql.Date) row[16]).toLocalDate());
                if (row[17] != null) {
                    final String reasonString = (String) row[17];
                    unavailability.setReason(uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason.valueOf(reasonString));
                }
                unavailability.setCreatedOn((java.util.Date) row[18]);
                unavailability.setUpdatedOn((java.util.Date) row[19]);
                
                // Only add if not already present (avoid duplicates)
                if (rule.getUnavailabilities().stream().noneMatch(u -> u.getId().equals(unavailability.getId()))) {
                    rule.getUnavailabilities().add(unavailability);
                }
            }
        }

        // Convert to list maintaining order
        final List<JudiciaryAvailabilityRule> rules = new ArrayList<>(rulesMap.values());
        rules.sort((r1, r2) -> Integer.compare(
                ruleOrder.getOrDefault(r1.getId(), Integer.MAX_VALUE),
                ruleOrder.getOrDefault(r2.getId(), Integer.MAX_VALUE)));

        return new java.util.AbstractMap.SimpleEntry<>(totalCount, rules);
    }
}

