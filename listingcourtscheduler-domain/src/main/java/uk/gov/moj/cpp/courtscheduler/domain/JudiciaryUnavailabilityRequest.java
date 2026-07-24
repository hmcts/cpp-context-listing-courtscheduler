package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Objects;

public class JudiciaryUnavailabilityRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private UnavailabilityReason reason;

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public UnavailabilityReason getReason() {
        return reason;
    }

    public void setReason(UnavailabilityReason reason) {
        this.reason = reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JudiciaryUnavailabilityRequest that = (JudiciaryUnavailabilityRequest) o;
        return Objects.equals(startDate, that.startDate) &&
                Objects.equals(endDate, that.endDate) &&
                Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startDate, endDate, reason);
    }

    @Override
    public String toString() {
        return "JudiciaryUnavailabilityRequest{" +
                "startDate=" + startDate +
                ", endDate=" + endDate +
                ", reason='" + reason + '\'' +
                '}';
    }
}

