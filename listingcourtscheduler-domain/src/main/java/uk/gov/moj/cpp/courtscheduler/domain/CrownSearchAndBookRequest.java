package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;

/**
 * Request for {@code courtscheduler.crown.search.and.book} (SPRDT-1089).
 *
 * <p>CROWN search-and-book — single OR multi-day. {@code courtScheduleId} is an OPTIONAL anchor:
 * present => book consecutive weekdays in its courtroom + business type; absent => search the
 * court centre for a room with the consecutive run. Multi-day when
 * {@code durationInMinutes > MAX_SINGLE_DAY_MINUTES} OR {@code endDate > hearingDate}.</p>
 */
public class CrownSearchAndBookRequest {

    public static final int MAX_SINGLE_DAY_MINUTES = 360;

    private String hearingId;
    private String courtCentreId;
    private String courtRoomId;
    private LocalDate hearingDate;
    private LocalDate endDate;
    private String earliestHearingTime;
    private int durationInMinutes;
    private String courtScheduleId;
    private String source;

    public String getHearingId() {
        return hearingId;
    }

    public CrownSearchAndBookRequest setHearingId(final String hearingId) {
        this.hearingId = hearingId;
        return this;
    }

    public String getCourtCentreId() {
        return courtCentreId;
    }

    public CrownSearchAndBookRequest setCourtCentreId(final String courtCentreId) {
        this.courtCentreId = courtCentreId;
        return this;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public CrownSearchAndBookRequest setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
        return this;
    }

    public LocalDate getHearingDate() {
        return hearingDate;
    }

    public CrownSearchAndBookRequest setHearingDate(final LocalDate hearingDate) {
        this.hearingDate = hearingDate;
        return this;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public CrownSearchAndBookRequest setEndDate(final LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public String getEarliestHearingTime() {
        return earliestHearingTime;
    }

    public CrownSearchAndBookRequest setEarliestHearingTime(final String earliestHearingTime) {
        this.earliestHearingTime = earliestHearingTime;
        return this;
    }

    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public CrownSearchAndBookRequest setDurationInMinutes(final int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
        return this;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public CrownSearchAndBookRequest setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
        return this;
    }

    public String getSource() {
        return source;
    }

    public CrownSearchAndBookRequest setSource(final String source) {
        this.source = source;
        return this;
    }

    public boolean hasCourtScheduleId() {
        return courtScheduleId != null && !courtScheduleId.isBlank();
    }

    public boolean hasEndDate() {
        return endDate != null;
    }

    public boolean hasSource() {
        return source != null && !source.isBlank();
    }
}
