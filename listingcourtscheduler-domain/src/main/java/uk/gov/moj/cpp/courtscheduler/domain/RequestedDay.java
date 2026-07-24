package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;

/**
 * A single requested day within a {@link ChangeCourtRoomForMultidayHearingRequest} — the target
 * session (and its own duration) a specific date of an existing multi-day hearing should be
 * re-allocated onto.
 */
public class RequestedDay {

    private LocalDate sessionDate;
    private String courtScheduleId;
    private int durationInMinutes;

    public RequestedDay() {
    }

    public RequestedDay(final LocalDate sessionDate, final String courtScheduleId, final int durationInMinutes) {
        this.sessionDate = sessionDate;
        this.courtScheduleId = courtScheduleId;
        this.durationInMinutes = durationInMinutes;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public RequestedDay setSessionDate(final LocalDate sessionDate) {
        this.sessionDate = sessionDate;
        return this;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public RequestedDay setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
        return this;
    }

    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public RequestedDay setDurationInMinutes(final int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
        return this;
    }
}
