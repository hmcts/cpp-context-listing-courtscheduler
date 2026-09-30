package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class RequestedCourtSchedule {

        private String courtScheduleId;
        private String hearingStartTime;
        private Integer durationInMinutes;
        private String source;

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(final String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    public Integer getDurationInMinutes() {
        return durationInMinutes;
    }

    public void setDurationInMinutes(final Integer durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
    }

    public String getSource() {
        return source;
    }

    public void setSource(final String source) {
        this.source = source;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RequestedCourtSchedule that)) {
            return false;
        }
        return Objects.equals(courtScheduleId, that.courtScheduleId) &&
                Objects.equals(hearingStartTime, that.hearingStartTime) &&
                Objects.equals(durationInMinutes, that.durationInMinutes) &&
                Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleId, hearingStartTime, durationInMinutes, source);
    }

    @Override
    public String toString() {
        return "CourtScheduleId{" +
                "CourtScheduleId='" + courtScheduleId + '\'' +
                ", hearingStartTime='" + hearingStartTime + '\'' +
                ", duration=" + durationInMinutes +
                ", source='" + source + '\'' +
                '}';
    }
}
