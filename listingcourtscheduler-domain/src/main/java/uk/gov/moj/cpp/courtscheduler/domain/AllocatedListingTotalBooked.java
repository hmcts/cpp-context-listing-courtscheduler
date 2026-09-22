package uk.gov.moj.cpp.courtscheduler.domain;

import static java.util.Objects.nonNull;

public class AllocatedListingTotalBooked {

    private String courtScheduleId;
    private Integer totalBooked;

    public AllocatedListingTotalBooked() {}

    public AllocatedListingTotalBooked(final String courtScheduleId, final Long totalBooked) {
        this.courtScheduleId = courtScheduleId;
        if (nonNull(totalBooked)) {
            this.totalBooked = totalBooked.intValue();
        }
    }

    public Integer getTotalBooked() {
        return totalBooked;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }
}
