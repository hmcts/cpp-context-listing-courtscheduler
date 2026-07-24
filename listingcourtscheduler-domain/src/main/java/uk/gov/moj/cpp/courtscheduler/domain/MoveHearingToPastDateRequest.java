package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;

/**
 * Request for {@code courtscheduler.move-hearing-to-past-date} (SPRDT-1089, extends PR #839).
 *
 * <p>Both jurisdictions, single OR multi-day. Multi-day books CONSECUTIVE weekday sessions
 * (one room + business type) for both jurisdictions; CROWN may supply an optional
 * {@code courtScheduleId} anchor. Multi-day when {@code endDate > startDate} OR
 * {@code durationInMinutes > MAX_SINGLE_DAY_MINUTES}. The past-only rule is owned by the caller
 * (listing); courtscheduler does not reject future dates.</p>
 */
public class MoveHearingToPastDateRequest {

    public static final int MAX_SINGLE_DAY_MINUTES = 360;

    private String hearingId;
    private String courtCentreId;
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
