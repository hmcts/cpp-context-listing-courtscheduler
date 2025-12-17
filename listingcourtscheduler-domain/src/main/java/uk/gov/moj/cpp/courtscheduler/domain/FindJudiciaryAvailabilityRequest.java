package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Objects;

public class FindJudiciaryAvailabilityRequest {

    private LocalDate startDate;
    private LocalDate endDate;
    private String courtHouseId; // Optional
    private String judiciaryId; // Optional

    public FindJudiciaryAvailabilityRequest() {
    }

    public FindJudiciaryAvailabilityRequest(LocalDate startDate, LocalDate endDate, String courtHouseId, String judiciaryId) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.courtHouseId = courtHouseId;
        this.judiciaryId = judiciaryId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final FindJudiciaryAvailabilityRequest that = (FindJudiciaryAvailabilityRequest) o;
        return Objects.equals(this.startDate, that.startDate) &&
                Objects.equals(this.endDate, that.endDate) &&
                Objects.equals(this.courtHouseId, that.courtHouseId) &&
                Objects.equals(this.judiciaryId, that.judiciaryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.startDate, this.endDate, this.courtHouseId, this.judiciaryId);
    }

    @Override
    public String toString() {
        return "FindJudiciaryAvailabilityRequest{" +
                "startDate=" + this.startDate +
                ", endDate=" + this.endDate +
                ", courtHouseId='" + this.courtHouseId + '\'' +
                ", judiciaryId='" + this.judiciaryId + '\'' +
                '}';
    }
}

