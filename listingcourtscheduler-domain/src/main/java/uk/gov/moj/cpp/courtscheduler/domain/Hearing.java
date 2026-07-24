package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Hearing {
    private String hearingId;
    private String courtScheduleId;
    private String hearingStartTime;
    private Integer duration;
    private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
    private String source;

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

    public String getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public List<CourtScheduleJudiciary> getJudiciaries() {
        return judiciaries;
    }

    public void setJudiciaries(final List<CourtScheduleJudiciary> judiciaries) {
        this.judiciaries = judiciaries;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hearing that)) return false;
        return Objects.equals(hearingId, that.hearingId) &&
                Objects.equals(courtScheduleId, that.courtScheduleId) &&
                Objects.equals(hearingStartTime, that.hearingStartTime) &&
                Objects.equals(duration, that.duration) &&
                Objects.equals(judiciaries, that.judiciaries) &&
                Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hearingId, courtScheduleId, hearingStartTime, duration, judiciaries, source);
    }

    @Override
    public String toString() {
        return "HearingSlot{" +
                "hearingId='" + hearingId + '\'' +
                ", courtScheduleId='" + courtScheduleId + '\'' +
                ", hearingStartTime='" + hearingStartTime + '\'' +
                ", duration=" + duration +
                ", judiciaries=" + judiciaries +
                ", source='" + source + '\'' +
                '}';
    }
}
