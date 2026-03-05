package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public class FindJudiciaryAvailabilityResponse {

    private List<String> availableJudiciaries; // List of judiciary IDs

    public FindJudiciaryAvailabilityResponse() {
    }

    public FindJudiciaryAvailabilityResponse(List<String> availableJudiciaries) {
        this.availableJudiciaries = availableJudiciaries;
    }

    public List<String> getAvailableJudiciaries() {
        return this.availableJudiciaries;
    }

    public void setAvailableJudiciaries(List<String> availableJudiciaries) {
        this.availableJudiciaries = availableJudiciaries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final FindJudiciaryAvailabilityResponse that = (FindJudiciaryAvailabilityResponse) o;
        return Objects.equals(this.availableJudiciaries, that.availableJudiciaries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.availableJudiciaries);
    }

    @Override
    public String toString() {
        return "FindJudiciaryAvailabilityResponse{" +
                "availableJudiciaries=" + this.availableJudiciaries +
                '}';
    }
}

