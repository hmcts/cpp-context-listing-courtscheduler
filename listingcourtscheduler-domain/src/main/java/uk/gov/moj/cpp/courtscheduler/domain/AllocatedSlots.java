package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class AllocatedSlots {

    private List<AllocatedSlot> hearingSlots;

    @SuppressWarnings("squid:S1186")
    public AllocatedSlots() {
    }


    public List<AllocatedSlot> getHearingSlots() {
        return hearingSlots;
    }

    public void setHearingSlots(List<AllocatedSlot> hearingSlots) {
        this.hearingSlots = hearingSlots;
    }
}
