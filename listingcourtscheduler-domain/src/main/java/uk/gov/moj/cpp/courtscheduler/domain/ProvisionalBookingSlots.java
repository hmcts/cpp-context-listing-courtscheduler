package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class ProvisionalBookingSlots {

    List<ProvisionalSlot> provisionalSlots;

    private String bookingId;

    public List<ProvisionalSlot> getProvisionalSlots() {
        return provisionalSlots;
    }

    public void setProvisionalSlots(final List<ProvisionalSlot> provisionalSlots) {
        this.provisionalSlots = provisionalSlots;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(final String bookingId) {
        this.bookingId = bookingId;
    }
}
