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
}

