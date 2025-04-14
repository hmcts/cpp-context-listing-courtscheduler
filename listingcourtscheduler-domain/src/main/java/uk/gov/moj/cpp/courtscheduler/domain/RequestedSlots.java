package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class RequestedSlots {
    private List<HearingSlot> hearingSlots;

    public List<HearingSlot> getHearingSlots() {
        return hearingSlots;
    }

    public void setHearingSlots(List<HearingSlot> hearingSlots) {
        this.hearingSlots = hearingSlots;
    }
}
