package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
