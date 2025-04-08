package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public class Hearing {
    private String hearingId;
    private List<CourtSchedule> courtSchedules;

    // Getters and Setters
    public String getHearingId() {
        return hearingId;
    }

    public void setHearingId(String hearingId) {
        this.hearingId = hearingId;
    }

    public List<CourtSchedule> getCourtSchedules() {
        return courtSchedules;
    }

    public void setCourtSchedules(List<CourtSchedule> courtSchedules) {
        this.courtSchedules = courtSchedules;
    }
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Hearing that = (Hearing) o;
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
                ", courtSchedules=" + courtSchedules +
                '}';
    }
}
