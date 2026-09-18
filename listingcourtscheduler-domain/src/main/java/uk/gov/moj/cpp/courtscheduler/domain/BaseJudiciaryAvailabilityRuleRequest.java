package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Base class for judiciary availability rule requests containing common fields.
 * All specific request types extend this class to inherit common functionality.
 */
public abstract class BaseJudiciaryAvailabilityRuleRequest {
    
    protected String ruleId;
    protected String judiciaryId;
    protected String courtHouseId;
    protected LocalDate startDate;
    protected LocalDate endDate;

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(final String ruleId) {
        this.ruleId = ruleId;
    }

    public String getJudiciaryId() {
        return judiciaryId;
    }

    public void setJudiciaryId(final String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    public String getCourtHouseId() {
        return courtHouseId;
    }

    public void setCourtHouseId(final String courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(final LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(final LocalDate endDate) {
        this.endDate = endDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final BaseJudiciaryAvailabilityRuleRequest that = (BaseJudiciaryAvailabilityRuleRequest) o;
        return Objects.equals(ruleId, that.ruleId) &&
                Objects.equals(judiciaryId, that.judiciaryId) &&
                Objects.equals(courtHouseId, that.courtHouseId) &&
                Objects.equals(startDate, that.startDate) &&
                Objects.equals(endDate, that.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ruleId, judiciaryId, courtHouseId, startDate, endDate);
    }

    @Override
    public String toString() {
        return "BaseJudiciaryAvailabilityRuleRequest{" +
                "ruleId='" + ruleId + '\'' +
                ", judiciaryId='" + judiciaryId + '\'' +
                ", courtHouseId='" + courtHouseId + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                '}';
    }
}
