package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Request for finding judiciary availability rules.
 */
public class FindJudiciaryAvailabilityRuleRequest extends BaseJudiciaryAvailabilityRuleRequest {

    private Integer pageSize; // Optional, default 20
    private Integer pageNumber; // Optional, default 1
    private Boolean withJudiciary; // Optional, default false

    public FindJudiciaryAvailabilityRuleRequest() {
        super();
    }

    public FindJudiciaryAvailabilityRuleRequest(final LocalDate startDate, final LocalDate endDate, final String courtHouseId, final String judiciaryId, final Integer pageSize, final Integer pageNumber) {
        super();
        this.startDate = startDate;
        this.endDate = endDate;
        this.courtHouseId = courtHouseId;
        this.judiciaryId = judiciaryId;
        this.pageSize = pageSize;
        this.pageNumber = pageNumber;
    }

    public Integer getPageSize() {
        return this.pageSize;
    }

    public void setPageSize(final Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageNumber() {
        return this.pageNumber;
    }

    public void setPageNumber(final Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Boolean getWithJudiciary() {
        return this.withJudiciary;
    }

    public void setWithJudiciary(final Boolean withJudiciary) {
        this.withJudiciary = withJudiciary;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FindJudiciaryAvailabilityRuleRequest that = (FindJudiciaryAvailabilityRuleRequest) o;
        return Objects.equals(this.pageSize, that.pageSize) &&
                Objects.equals(this.pageNumber, that.pageNumber) &&
                Objects.equals(this.withJudiciary, that.withJudiciary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), this.pageSize, this.pageNumber, this.withJudiciary);
    }

    @Override
    public String toString() {
        return "FindJudiciaryAvailabilityRuleRequest{" +
                "startDate=" + this.startDate +
                ", endDate=" + this.endDate +
                ", courtHouseId='" + this.courtHouseId + '\'' +
                ", judiciaryId='" + this.judiciaryId + '\'' +
                ", pageSize=" + this.pageSize +
                ", pageNumber=" + this.pageNumber +
                ", withJudiciary=" + this.withJudiciary +
                '}';
    }
}

