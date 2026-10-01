package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

/**
 * Request for deleting judiciary availability rule.
 */
public class DeleteJudiciaryAvailabilityRuleRequest extends BaseJudiciaryAvailabilityRuleRequest {

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DeleteJudiciaryAvailabilityRuleRequest that = (DeleteJudiciaryAvailabilityRuleRequest) o;
        // Only compare ruleId (other fields from base class are not used)
        return Objects.equals(ruleId, that.ruleId);
    }

    @Override
    public int hashCode() {
        // Only hash ruleId (other fields from base class are not used)
        return Objects.hash(ruleId);
    }

    @Override
    public String toString() {
        return "DeleteJudiciaryAvailabilityRuleRequest{" +
                "ruleId='" + ruleId + '\'' +
                '}';
    }
}




