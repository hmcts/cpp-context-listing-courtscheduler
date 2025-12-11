package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Objects;

public class FindJudiciaryAvailabilityRuleRequest {

    private LocalDate startDate;
    private LocalDate endDate;
    private String courtHouseId; // Optional
    private String judiciaryId; // Optional
    private Integer pageSize; // Optional, default 20
    private Integer pageNumber; // Optional, default 1
    private Boolean withJudiciary; // Optional, default false

    public FindJudiciaryAvailabilityRuleRequest() {
    }

    public FindJudiciaryAvailabilityRuleRequest(LocalDate startDate, LocalDate endDate, String courtHouseId, String judiciaryId, Integer pageSize, Integer pageNumber) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.courtHouseId = courtHouseId;
        this.judiciaryId = judiciaryId;
        this.pageSize = pageSize;
        this.pageNumber = pageNumber;
    }

    public LocalDate getStartDate() {
        return this.startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return this.endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getCourtHouseId() {
        return this.courtHouseId;
    }

    public void setCourtHouseId(String courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public String getJudiciaryId() {
        return this.judiciaryId;
    }

    public void setJudiciaryId(String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    public Integer getPageSize() {
        return this.pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Integer getPageNumber() {
        return this.pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Boolean getWithJudiciary() {
        return this.withJudiciary;
    }

    public void setWithJudiciary(Boolean withJudiciary) {
        this.withJudiciary = withJudiciary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final FindJudiciaryAvailabilityRuleRequest that = (FindJudiciaryAvailabilityRuleRequest) o;
        return Objects.equals(this.startDate, that.startDate) &&
                Objects.equals(this.endDate, that.endDate) &&
                Objects.equals(this.courtHouseId, that.courtHouseId) &&
                Objects.equals(this.judiciaryId, that.judiciaryId) &&
                Objects.equals(this.pageSize, that.pageSize) &&
                Objects.equals(this.pageNumber, that.pageNumber) &&
                Objects.equals(this.withJudiciary, that.withJudiciary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.startDate, this.endDate, this.courtHouseId, this.judiciaryId, this.pageSize, this.pageNumber, this.withJudiciary);
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

