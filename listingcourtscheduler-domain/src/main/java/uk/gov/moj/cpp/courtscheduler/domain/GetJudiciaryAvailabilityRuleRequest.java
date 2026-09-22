package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

/**
 * Request for getting a single judiciary availability rule by ID.
 */
public class GetJudiciaryAvailabilityRuleRequest {

    private String ruleId;
    private Boolean withJudiciary; // Optional, default true

    public GetJudiciaryAvailabilityRuleRequest() {
    }

    public GetJudiciaryAvailabilityRuleRequest(final String ruleId, final Boolean withJudiciary) {
        this.ruleId = ruleId;
        this.withJudiciary = withJudiciary;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(final String ruleId) {
        this.ruleId = ruleId;
    }

    public Boolean isWithJudiciary() {
        return withJudiciary;
    }

    public void setWithJudiciary(final Boolean withJudiciary) {
        this.withJudiciary = withJudiciary;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GetJudiciaryAvailabilityRuleRequest that = (GetJudiciaryAvailabilityRuleRequest) o;
        return Objects.equals(this.ruleId, that.ruleId) &&
                Objects.equals(this.withJudiciary, that.withJudiciary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId, withJudiciary);
    }

    @Override
    public String toString() {
        return "GetJudiciaryAvailabilityRuleRequest{" +
                "ruleId='" + ruleId + '\'' +
                ", withJudiciary=" + withJudiciary +
                '}';
    }
}
