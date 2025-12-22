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

    public GetJudiciaryAvailabilityRuleRequest(String ruleId, Boolean withJudiciary) {
        this.ruleId = ruleId;
        this.withJudiciary = withJudiciary;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public Boolean getWithJudiciary() {
        return withJudiciary;
    }

    public void setWithJudiciary(Boolean withJudiciary) {
        this.withJudiciary = withJudiciary;
    }

    @Override
    public boolean equals(Object o) {
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
