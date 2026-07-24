package uk.gov.moj.cpp.courtscheduler.domain;

import static java.util.Objects.nonNull;

public class AllocatedListingTotalBooked {

    private String courtScheduleId;
    private Integer totalBooked;

    public AllocatedListingTotalBooked() {}

    public AllocatedListingTotalBooked(String courtScheduleId, Long totalBooked) {
        this.courtScheduleId = courtScheduleId;
        this.totalBooked = nonNull(totalBooked) ? totalBooked.intValue() : null;
    }

    public Integer getTotalBooked() {
        return totalBooked;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }
}
