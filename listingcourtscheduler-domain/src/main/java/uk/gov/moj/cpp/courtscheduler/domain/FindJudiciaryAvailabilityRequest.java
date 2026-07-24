package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Request for finding judiciary availability.
 */
public class FindJudiciaryAvailabilityRequest extends BaseJudiciaryAvailabilityRuleRequest {

    public FindJudiciaryAvailabilityRequest() {
        super();
    }

    public FindJudiciaryAvailabilityRequest(LocalDate startDate, LocalDate endDate, String courtHouseId, String judiciaryId) {
        super();
        this.startDate = startDate;
        this.endDate = endDate;
        this.courtHouseId = courtHouseId;
        this.judiciaryId = judiciaryId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        FindJudiciaryAvailabilityRequest that = (FindJudiciaryAvailabilityRequest) o;
        // Only compare fields that are actually used (not ruleId)
        return Objects.equals(startDate, that.startDate) &&
                Objects.equals(endDate, that.endDate) &&
                Objects.equals(courtHouseId, that.courtHouseId) &&
                Objects.equals(judiciaryId, that.judiciaryId);
    }

    @Override
    public int hashCode() {
        // Only hash fields that are actually used (not ruleId)
        return Objects.hash(startDate, endDate, courtHouseId, judiciaryId);
    }

    @Override
    public String toString() {
        return "FindJudiciaryAvailabilityRequest{" +
                "startDate=" + startDate +
                ", endDate=" + endDate +
                ", courtHouseId='" + courtHouseId + '\'' +
                ", judiciaryId='" + judiciaryId + '\'' +
                '}';
    }
}

