package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class GetJudiciaryAvailabilityRuleResponse {

    private JudiciaryAvailabilityRuleResponse rule;
    private Judiciary judiciary;

    public GetJudiciaryAvailabilityRuleResponse() {
    }

    public GetJudiciaryAvailabilityRuleResponse(JudiciaryAvailabilityRuleResponse rule, Judiciary judiciary) {
        this.rule = rule;
        this.judiciary = judiciary;
    }

    public JudiciaryAvailabilityRuleResponse getRule() {
        return rule;
    }

    public void setRule(JudiciaryAvailabilityRuleResponse rule) {
        this.rule = rule;
    }

    public Judiciary getJudiciary() {
        return judiciary;
    }

    public void setJudiciary(Judiciary judiciary) {
        this.judiciary = judiciary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GetJudiciaryAvailabilityRuleResponse that = (GetJudiciaryAvailabilityRuleResponse) o;
        return Objects.equals(this.rule, that.rule) &&
                Objects.equals(this.judiciary, that.judiciary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rule, judiciary);
    }

    @Override
    public String toString() {
        return "GetJudiciaryAvailabilityRuleResponse{" +
                "rule=" + rule +
                ", judiciary=" + judiciary +
                '}';
    }
}
