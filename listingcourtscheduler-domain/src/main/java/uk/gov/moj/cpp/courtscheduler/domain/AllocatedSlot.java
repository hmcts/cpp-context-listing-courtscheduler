package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public class AllocatedSlot {
    private int duration;
    private String sessionDate;
    private String hearingStartTime;
    private String session;
    private String courtRoomId;
    private String ouCode;
    private String hearingId;
    private String courtScheduleId;
    private boolean isSlotBased;
    private String bookingId;

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
                Objects.equals(hearingId, that.hearingId) &&
                Objects.equals(courtScheduleId, that.courtScheduleId) &&
                Objects.equals(bookingId, that.bookingId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duration, sessionDate, hearingStartTime, session, courtRoomId, ouCode, hearingId, courtScheduleId, isSlotBased, bookingId);
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
                ", hearingId='" + hearingId + '\'' +
                ", courtScheduleId='" + courtScheduleId + '\'' +
                ", isSlotBased=" + isSlotBased +
                ", bookingId='" + bookingId + '\'' +
                '}';
    }
}
