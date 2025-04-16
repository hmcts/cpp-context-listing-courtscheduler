package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class ListHearingSlotsResponse {
    private List<Hearing> hearings;

    public List<Hearing> getHearings() {
        return hearings;
    }

    public void setHearings(List<Hearing> hearings) {
        this.hearings = hearings;
    }

    @Override
    public String toString() {
        return "HearingsResponse{" +
                "hearings=" + hearings +
                '}';
    }
}
