package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;

/**
 * Request for {@code courtscheduler.mags.search.and.book} (SPRDT-1089).
 *
 * <p>MAGISTRATES search-and-book (SPI route) — single OR multi-day. MAGS NEVER anchors, so
 * {@code courtScheduleId} is not a valid input (the validator rejects it). Multi-day books
 * CONSECUTIVE weekday sessions (one room + business type) and is expressed by
 * {@code durationInMinutes > MAX_SINGLE_DAY_MINUTES} OR {@code endDate > hearingDate}.
 * {@code isPolice} drives the overbooking path.</p>
 */
public class MagsSearchAndBookRequest {

    public static final int MAX_SINGLE_DAY_MINUTES = 360;

    private String hearingId;
    private String courtCentreId;
    private String courtRoomId;
    private LocalDate hearingDate;
    private LocalDate endDate;
    private String hearingStartTime;
    private String hearingSessionDateSearchCutOff;
    private int durationInMinutes;
    private boolean isPolice;
    private String courtScheduleId;

    public String getHearingId() {
        return hearingId;
    }

    public MagsSearchAndBookRequest setHearingId(final String hearingId) {
        this.hearingId = hearingId;
        return this;
    }

    public String getCourtCentreId() {
        return courtCentreId;
    }

    public MagsSearchAndBookRequest setCourtCentreId(final String courtCentreId) {
        this.courtCentreId = courtCentreId;
        return this;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public MagsSearchAndBookRequest setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
        return this;
    }

    public LocalDate getHearingDate() {
        return hearingDate;
    }

    public MagsSearchAndBookRequest setHearingDate(final LocalDate hearingDate) {
        this.hearingDate = hearingDate;
        return this;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public MagsSearchAndBookRequest setEndDate(final LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public String getHearingStartTime() {
        return hearingStartTime;
    }

    public MagsSearchAndBookRequest setHearingStartTime(final String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
        return this;
    }

    public String getHearingSessionDateSearchCutOff() {
        return hearingSessionDateSearchCutOff;
    }

    public MagsSearchAndBookRequest setHearingSessionDateSearchCutOff(final String hearingSessionDateSearchCutOff) {
        this.hearingSessionDateSearchCutOff = hearingSessionDateSearchCutOff;
        return this;
    }

    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public MagsSearchAndBookRequest setDurationInMinutes(final int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
        return this;
    }

    public boolean isPolice() {
        return isPolice;
    }

    public MagsSearchAndBookRequest setIsPolice(final boolean isPolice) {
        this.isPolice = isPolice;
        return this;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public MagsSearchAndBookRequest setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
        return this;
    }

    public boolean hasCourtScheduleId() {
        return courtScheduleId != null && !courtScheduleId.isBlank();
    }

    public boolean hasEndDate() {
        return endDate != null;
    }
}
