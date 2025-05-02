package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class Hearing {
    private String hearingId;
    private String courtScheduleId;
    private String sessionStartTime;
    private Integer duration;

    public String getHearingId() {
        return hearingId;
    }

    public void setHearingId(String hearingId) {
        this.hearingId = hearingId;
    }

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

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hearing that)) return false;
        return Objects.equals(hearingId, that.hearingId) &&
                Objects.equals(courtScheduleId, that.courtScheduleId) &&
                Objects.equals(sessionStartTime, that.sessionStartTime) &&
                Objects.equals(duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hearingId, courtScheduleId, sessionStartTime, duration);
    }

    @Override
    public String toString() {
        return "HearingSlot{" +
                "hearingId='" + hearingId + '\'' +
                ", courtScheduleId='" + courtScheduleId + '\'' +
                ", sessionStartTime='" + sessionStartTime + '\'' +
                ", duration=" + duration +
                '}';
    }
}
