package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class ProvisionalBookingSlots {

    List<ProvisionalSlot> provisionalSlots;

    public List<ProvisionalSlot> getProvisionalSlots() {
        return provisionalSlots;
    }

    public void setProvisionalSlots(final List<ProvisionalSlot> provisionalSlots) {
        this.provisionalSlots = provisionalSlots;
    }
}
