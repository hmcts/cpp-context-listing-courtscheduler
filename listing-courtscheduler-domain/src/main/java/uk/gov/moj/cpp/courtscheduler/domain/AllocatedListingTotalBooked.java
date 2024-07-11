package uk.gov.moj.cpp.courtscheduler.domain;

public class AllocatedListingTotalBooked {

    private String courtScheduleId;
    private Integer totalBooked;

    public AllocatedListingTotalBooked() {}

    public AllocatedListingTotalBooked(String courtScheduleId, Integer totalBooked) {
        this.courtScheduleId = courtScheduleId;
        this.totalBooked = totalBooked;
    }

    public Integer getTotalBooked() {
        return totalBooked;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }
}
