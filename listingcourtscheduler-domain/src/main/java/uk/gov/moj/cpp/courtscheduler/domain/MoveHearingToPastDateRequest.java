package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;

/**
 * Request for {@code courtscheduler.move-hearing-to-past-date} (SPRDT-1089, extends PR #839;
 * {@code courtRoomId} added to align the contract with {@code main} — see the review artifact
 * this reconciles).
 *
 * <p>Both jurisdictions, single OR multi-day. Multi-day books CONSECUTIVE weekday sessions
 * (one room + business type) for both jurisdictions; CROWN may supply an optional
 * {@code courtScheduleId} anchor. Multi-day ONLY when {@code endDate > startDate} (a genuine date
 * range) — {@code durationInMinutes} never drives day-count here, since callers send the hearing's
 * own overall estimate (e.g. a multi-day trial's total), unrelated to how many days a given move
 * targets; letting it drive sizing turned an ordinary same-day move with a large estimate into an
 * unsatisfiable multi-consecutive-day search (SPRDT-1361). {@code startDate}/{@code endDate} are the
 * calendar dates derived from the wire contract's {@code startTime}/{@code endTime} UTC instants
 * (see {@code CourtSchedulerApi.moveHearingToPastDate}) — this request stays date-granular
 * internally since court-schedule sessions here are booked per day, not per time-slot.
 * {@code courtRoomId} scopes the session search to the requested room; not used on the CROWN
 * anchor path, where the anchor's own room already applies. The past-only rule is owned by the
 * caller (listing); courtscheduler does not reject future dates.</p>
 */
public class MoveHearingToPastDateRequest {

    public static final int MAX_SINGLE_DAY_MINUTES = 360;

    private String hearingId;
    private String courtCentreId;
    private String courtRoomId;
    private String jurisdiction;
    private LocalDate startDate;
    private LocalDate endDate;
    private int durationInMinutes;
    private String courtScheduleId;

    public String getHearingId() {
        return hearingId;
    }

    public MoveHearingToPastDateRequest setHearingId(final String hearingId) {
        this.hearingId = hearingId;
        return this;
    }

    public String getCourtCentreId() {
        return courtCentreId;
    }

    public MoveHearingToPastDateRequest setCourtCentreId(final String courtCentreId) {
        this.courtCentreId = courtCentreId;
        return this;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public MoveHearingToPastDateRequest setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
        return this;
    }

    public boolean hasCourtRoomId() {
        return courtRoomId != null && !courtRoomId.isBlank();
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public MoveHearingToPastDateRequest setJurisdiction(final String jurisdiction) {
        this.jurisdiction = jurisdiction;
        return this;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public MoveHearingToPastDateRequest setStartDate(final LocalDate startDate) {
        this.startDate = startDate;
        return this;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public MoveHearingToPastDateRequest setEndDate(final LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public MoveHearingToPastDateRequest setDurationInMinutes(final int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
        return this;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public MoveHearingToPastDateRequest setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
        return this;
    }

    public boolean hasCourtScheduleId() {
        return courtScheduleId != null && !courtScheduleId.isBlank();
    }

    public boolean hasEndDate() {
        return endDate != null;
    }

    public boolean hasJurisdiction() {
        return jurisdiction != null && !jurisdiction.isBlank();
    }
}
