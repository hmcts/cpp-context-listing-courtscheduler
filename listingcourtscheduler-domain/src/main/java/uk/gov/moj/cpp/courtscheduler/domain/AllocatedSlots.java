package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class AllocatedSlots {

    private List<AllocatedSlot> hearingSlots;

    public List<AllocatedSlot> getHearingSlots() {
        return hearingSlots;
    }

    public void setHearingSlots(final List<AllocatedSlot> hearingSlots) {
        this.hearingSlots = hearingSlots;
    }
}
