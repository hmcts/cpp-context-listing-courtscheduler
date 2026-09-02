package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class AllocatedSlot {
    private int duration;
    private String sessionDate;
    private String hearingStartTime;
    private String session;
    private String courtRoomId;
    private String courtRoomUUId;
    private String courtCentreId;
    private String ouCode;
    private String hearingId;
    private String courtScheduleId;
    private boolean isSlotBased;
    private String bookingId;
    private String prosecutor;
    private String courtRoom;
    private String hearingSessionDateSearchCutOff;
    private boolean isPolice;
    private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
    private String source;
    private java.util.Date expiresAt;

    @SuppressWarnings("squid:S1186")
    public AllocatedSlot() {
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getHearingId() {
        return hearingId;
    }

    public void setHearingId(final String hearingId) {
        this.hearingId = hearingId;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(final int duration) {
        this.duration = duration;
    }

    public String getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(final String sessionDate) {
        this.sessionDate = sessionDate;
    }

    public String getSession() {
        return session;
    }

    public void setSession(final String session) {
        this.session = session;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public String getOuCode() {
        return ouCode;
    }

    public void setOuCode(final String ouCode) {
        this.ouCode = ouCode;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(final String bookingId) {
        this.bookingId = bookingId;
    }

    public boolean isSlotBased() {
        return isSlotBased;
    }

    public void setSlotBased(final boolean slotBased) {
        isSlotBased = slotBased;
    }

    public String getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(final String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    public String getCourtRoom() {
        return courtRoom;
    }

    public void setCourtRoom(final String courtRoom) {
        this.courtRoom = courtRoom;
    }

    public String getProsecutor() {
        return prosecutor;
    }

    public void setProsecutor(final String prosecutor) {
        this.prosecutor = prosecutor;
    }

    public String getCourtRoomUUId() {
        return courtRoomUUId;
    }

    public void setCourtRoomUUId(final String courtRoomUUId) {
        this.courtRoomUUId = courtRoomUUId;
    }

    public String getHearingSessionDateSearchCutOff() { return hearingSessionDateSearchCutOff; }

    public void setHearingSessionDateSearchCutOff(final String hearingSessionDateSearchCutOff) {
        this.hearingSessionDateSearchCutOff = hearingSessionDateSearchCutOff; }

    public boolean isPolice() {
        return isPolice;
    }

    public void setPolice(boolean police) {
        isPolice = police;
    }

    public List<CourtScheduleJudiciary> getJudiciaries() {
        return judiciaries;
    }

    public void setJudiciaries(final List<CourtScheduleJudiciary> judiciaries) {
        this.judiciaries = judiciaries;
    }

    public String getCourtCentreId() {
        return courtCentreId;
    }

    public AllocatedSlot setCourtCentreId(final String courtCentreId) {
        this.courtCentreId = courtCentreId;
        return this;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public java.util.Date getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(final java.util.Date expiresAt) {
        this.expiresAt = expiresAt;
    }

    @SuppressWarnings("squid:S1067")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AllocatedSlot that = (AllocatedSlot) o;
        return duration == that.duration &&
                isSlotBased == that.isSlotBased &&
                Objects.equals(sessionDate, that.sessionDate) &&
                Objects.equals(hearingStartTime, that.hearingStartTime) &&
                Objects.equals(session, that.session) &&
                Objects.equals(courtRoomId, that.courtRoomId) &&
                Objects.equals(ouCode, that.ouCode) &&
                Objects.equals(courtCentreId, that.courtCentreId) &&
                Objects.equals(hearingId, that.hearingId) &&
                Objects.equals(courtScheduleId, that.courtScheduleId) &&
                Objects.equals(bookingId, that.bookingId) &&
                Objects.equals(prosecutor, that.prosecutor) &&
                Objects.equals(courtRoom, that.courtRoom) &&
                Objects.equals(courtRoomUUId, that.courtRoomUUId) &&
                Objects.equals(hearingSessionDateSearchCutOff, that.hearingSessionDateSearchCutOff) &&
                Objects.equals(isPolice, that.isPolice) &&
                Objects.equals(judiciaries, that.judiciaries) &&
                Objects.equals(source, that.source) &&
                Objects.equals(expiresAt, that.expiresAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, sessionDate, hearingStartTime, session,
                courtRoomId, ouCode,courtCentreId, hearingId, courtScheduleId, isSlotBased, bookingId, prosecutor, courtRoom,
                courtRoomUUId, hearingSessionDateSearchCutOff, isPolice, judiciaries, source, expiresAt);
    }

    @Override
    public String toString() {
        return "AllocatedSlot{" +
                "duration=" + duration +
                ", sessionDate='" + sessionDate + '\'' +
                ", hearingStartTime='" + hearingStartTime + '\'' +
                ", session='" + session + '\'' +
                ", courtRoomId='" + courtRoomId + '\'' +
                ", ouCode='" + ouCode + '\'' +
                ", courtCentreId='" + courtCentreId + '\'' +
                ", hearingId='" + hearingId + '\'' +
                ", courtScheduleId='" + courtScheduleId + '\'' +
                ", isSlotBased=" + isSlotBased +
                ", bookingId='" + bookingId + '\'' +
                ", prosecutor='" + prosecutor + '\'' +
                ", courtRoom='" + courtRoom + '\'' +
                ", courtRoomUUId='" + courtRoomUUId + '\'' +
                ", hearingSessionDateSearchCutOff='" + hearingSessionDateSearchCutOff + '\'' +
                ", isPolice='" + isPolice + '\'' +
                ", judiciaries=" + judiciaries +
                ", source='" + source + '\'' +
                ", expiresAt=" + expiresAt +
                '}';
    }
}
