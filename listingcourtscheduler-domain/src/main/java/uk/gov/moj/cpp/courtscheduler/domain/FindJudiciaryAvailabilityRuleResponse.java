package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public class FindJudiciaryAvailabilityRuleResponse {

    private List<JudiciaryAvailabilityRuleResponse> rules;
    private Integer totalCount;
    private Integer pageNumber;
    private Integer pageSize;
    private List<Judiciary> judiciaries;

    public FindJudiciaryAvailabilityRuleResponse() {
    }

    public FindJudiciaryAvailabilityRuleResponse(List<JudiciaryAvailabilityRuleResponse> rules, Integer totalCount, Integer pageNumber, Integer pageSize) {
        this.rules = rules;
        this.totalCount = totalCount;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public List<JudiciaryAvailabilityRuleResponse> getRules() {
        return this.rules;
    }

    public void setRules(List<JudiciaryAvailabilityRuleResponse> rules) {
        this.rules = rules;
    }

    public Integer getTotalCount() {
        return this.totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getPageNumber() {
        return this.pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getPageSize() {
        return this.pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public List<Judiciary> getJudiciaries() {
        return this.judiciaries;
    }

    public void setJudiciaries(List<Judiciary> judiciaries) {
        this.judiciaries = judiciaries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final FindJudiciaryAvailabilityRuleResponse that = (FindJudiciaryAvailabilityRuleResponse) o;
        return Objects.equals(this.rules, that.rules) &&
                Objects.equals(this.totalCount, that.totalCount) &&
                Objects.equals(this.pageNumber, that.pageNumber) &&
                Objects.equals(this.pageSize, that.pageSize) &&
                Objects.equals(this.judiciaries, that.judiciaries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.rules, this.totalCount, this.pageNumber, this.pageSize, this.judiciaries);
    }

    @Override
    public String toString() {
        return "FindJudiciaryAvailabilityRuleResponse{" +
                "rules=" + this.rules +
                ", totalCount=" + this.totalCount +
                ", pageNumber=" + this.pageNumber +
                ", pageSize=" + this.pageSize +
                ", judiciaries=" + this.judiciaries +
                '}';
    }
}

