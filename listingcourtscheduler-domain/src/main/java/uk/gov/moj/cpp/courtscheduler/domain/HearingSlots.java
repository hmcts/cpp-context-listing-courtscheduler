package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class HearingSlots {
    private List<Hearing> hearings;

    // Getters and Setters
    public List<Hearing> getHearings() {
        return hearings;
    }

    public void setHearings(List<Hearing> hearings) {
        this.hearings = hearings;
    }
}
