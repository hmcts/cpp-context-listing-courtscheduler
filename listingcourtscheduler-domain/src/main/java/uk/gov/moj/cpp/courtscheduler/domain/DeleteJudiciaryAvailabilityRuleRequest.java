package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class DeleteJudiciaryAvailabilityRuleRequest {

    private String ruleId;

    public String getRuleId() {
        return this.ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final DeleteJudiciaryAvailabilityRuleRequest that = (DeleteJudiciaryAvailabilityRuleRequest) o;
        return Objects.equals(this.ruleId, that.ruleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.ruleId);
    }

    @Override
    public String toString() {
        return "DeleteJudiciaryAvailabilityRuleRequest{" +
                "ruleId='" + this.ruleId + '\'' +
                '}';
    }
}



