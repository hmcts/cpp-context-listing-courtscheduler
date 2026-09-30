package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Objects;

public class CrownFallbackRequest {

    public static final int MAX_SINGLE_DAY_MINUTES = 360;

    private String hearingId;
    private String courtCentreId;
    private String courtRoomId;
    private LocalDate hearingDate;
    private String earliestHearingTime;
    private int durationInMinutes;
    private String source;
    // SPRDT-1283: optional caller-supplied centre metadata so a session can be auto-created even
    // when the centre has no existing session to copy metadata from (never-seeded centre).
    private String ouCode;
    private String courtCentreName;
    private String courtRoomName;

    public String getHearingId() {
        return hearingId;
    }

    public CrownFallbackRequest setHearingId(final String hearingId) {
        this.hearingId = hearingId;
        return this;
    }

    public String getCourtCentreId() {
        return courtCentreId;
    }

    public CrownFallbackRequest setCourtCentreId(final String courtCentreId) {
        this.courtCentreId = courtCentreId;
        return this;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public CrownFallbackRequest setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
        return this;
    }

    public LocalDate getHearingDate() {
        return hearingDate;
    }

    public CrownFallbackRequest setHearingDate(final LocalDate hearingDate) {
        this.hearingDate = hearingDate;
        return this;
    }

    public String getEarliestHearingTime() {
        return earliestHearingTime;
    }

    public CrownFallbackRequest setEarliestHearingTime(final String earliestHearingTime) {
        this.earliestHearingTime = earliestHearingTime;
        return this;
    }

    public int getDurationInMinutes() {
        return durationInMinutes;
    }

    public CrownFallbackRequest setDurationInMinutes(final int durationInMinutes) {
        this.durationInMinutes = durationInMinutes;
        return this;
    }

    public String getSource() {
        return source;
    }

    public CrownFallbackRequest setSource(final String source) {
        this.source = source;
        return this;
    }

    public String getOuCode() {
        return ouCode;
    }

    public CrownFallbackRequest setOuCode(final String ouCode) {
        this.ouCode = ouCode;
        return this;
    }

    public String getCourtCentreName() {
        return courtCentreName;
    }

    public CrownFallbackRequest setCourtCentreName(final String courtCentreName) {
        this.courtCentreName = courtCentreName;
        return this;
    }

    public String getCourtRoomName() {
        return courtRoomName;
    }

    public CrownFallbackRequest setCourtRoomName(final String courtRoomName) {
        this.courtRoomName = courtRoomName;
        return this;
    }

    public boolean hasOuCode() {
        return ouCode != null && !ouCode.isBlank();
    }

    public boolean hasCourtRoomId() {
        return courtRoomId != null && !courtRoomId.isBlank();
    }

    public boolean hasEarliestHearingTime() {
        return earliestHearingTime != null && !earliestHearingTime.isBlank();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CrownFallbackRequest that = (CrownFallbackRequest) o;
        return durationInMinutes == that.durationInMinutes
                && Objects.equals(hearingId, that.hearingId)
                && Objects.equals(courtCentreId, that.courtCentreId)
                && Objects.equals(courtRoomId, that.courtRoomId)
                && Objects.equals(hearingDate, that.hearingDate)
                && Objects.equals(earliestHearingTime, that.earliestHearingTime)
                && Objects.equals(source, that.source)
                && Objects.equals(ouCode, that.ouCode)
                && Objects.equals(courtCentreName, that.courtCentreName)
                && Objects.equals(courtRoomName, that.courtRoomName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hearingId, courtCentreId, courtRoomId, hearingDate,
                earliestHearingTime, durationInMinutes, source, ouCode, courtCentreName, courtRoomName);
    }

    @Override
    public String toString() {
        return "CrownFallbackRequest{" +
                "hearingId='" + hearingId + '\'' +
                ", courtCentreId='" + courtCentreId + '\'' +
                ", courtRoomId='" + courtRoomId + '\'' +
                ", hearingDate=" + hearingDate +
                ", earliestHearingTime='" + earliestHearingTime + '\'' +
                ", durationInMinutes=" + durationInMinutes +
                ", source='" + source + '\'' +
                ", ouCode='" + ouCode + '\'' +
                ", courtCentreName='" + courtCentreName + '\'' +
                ", courtRoomName='" + courtRoomName + '\'' +
                '}';
    }
}
