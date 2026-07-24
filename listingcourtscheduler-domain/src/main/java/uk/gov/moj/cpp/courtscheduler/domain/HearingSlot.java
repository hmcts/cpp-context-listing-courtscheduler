package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public class HearingSlot {
    private String hearingId;
    private List<RequestedCourtSchedule> courtScheduleIds;

    public String getHearingId() {
        return hearingId;
    }

    public void setHearingId(String hearingId) {
        this.hearingId = hearingId;
    }

    public List<RequestedCourtSchedule> getCourtScheduleIds() {
        return courtScheduleIds;
    }

    public void setCourtScheduleIds(List<RequestedCourtSchedule> courtScheduleIds) {
        this.courtScheduleIds = courtScheduleIds;
    }
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HearingSlot that = (HearingSlot) o;
        return Objects.equals(hearingId, that.hearingId); //need to compare courtschedules?
    }

    @Override
    public int hashCode() {
        return Objects.hash(hearingId);
    }

    @Override
    public String toString() {
        return "Hearing{" +
                "hearingId='" + hearingId + '\'' +
                ", courtSchedules=" + courtScheduleIds +
                '}';
    }
}
