package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class RequestedCourtSchedule {

        private String courtScheduleId;
        private String hearingStartTime;
        private Integer durationInMinutes;

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    public Integer getDurationInMinutes() {
        return durationInMinutes;
    }

    public void setDurationInMinutes(Integer durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RequestedCourtSchedule that)) return false;
        return Objects.equals(courtScheduleId, that.courtScheduleId) &&
                Objects.equals(hearingStartTime, that.hearingStartTime) &&
                Objects.equals(durationInMinutes, that.durationInMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleId, hearingStartTime, durationInMinutes);
    }

    @Override
    public String toString() {
        return "CourtScheduleId{" +
                "CourtScheduleId='" + courtScheduleId + '\'' +
                ", hearingStartTime='" + hearingStartTime + '\'' +
                ", duration=" + durationInMinutes +
                '}';
    }
}
