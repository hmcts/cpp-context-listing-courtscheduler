package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class ListHearingSlotsResponse {
    private List<HearingSlot> hearings;

    public List<HearingSlot> getHearings() {
        return hearings;
    }

    public void setHearings(List<HearingSlot> hearings) {
        this.hearings = hearings;
    }

    @Override
    public String toString() {
        return "HearingsResponse{" +
                "hearings=" + hearings +
                '}';
    }
}
