package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class CourtScheduleId {

        private String courtScheduleId;
        private String sessionStartTime;
        private Integer durationInMinutes;

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(String sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
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
        if (!(o instanceof CourtScheduleId that)) return false;
        return Objects.equals(courtScheduleId, that.courtScheduleId) &&
                Objects.equals(sessionStartTime, that.sessionStartTime) &&
                Objects.equals(durationInMinutes, that.durationInMinutes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleId, sessionStartTime, durationInMinutes);
    }

    @Override
    public String toString() {
        return "CourtScheduleId{" +
                "CourtScheduleId='" + courtScheduleId + '\'' +
                ", sessionStartTime='" + sessionStartTime + '\'' +
                ", duration=" + durationInMinutes +
                '}';
    }
}
