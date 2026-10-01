package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.Instant;
import java.util.Objects;

public class AllocatedListing {

    private String id;

    private String courtScheduleId;

    private String bookingId;

    private String hearingId;

    private String oucode;

    private Integer courtRoomId;

    private String rotaBusinessType;

    private Integer duration;

    private Instant hearingStartTime;

    private Instant updatedOn;

    private Instant createdOn;

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(final String bookingId) {
        this.bookingId = bookingId;
    }

    public String getHearingId() {
        return hearingId;
    }

    public void setHearingId(final String hearingId) {
        this.hearingId = hearingId;
    }

    public String getOucode() {
        return oucode;
    }

    public void setOucode(final String oucode) {
        this.oucode = oucode;
    }

    public Integer getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(final Integer courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public String getRotaBusinessType() {
        return rotaBusinessType;
    }

    public void setRotaBusinessType(final String rotaBusinessType) {
        this.rotaBusinessType = rotaBusinessType;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(final Integer duration) {
        this.duration = duration;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(final Instant createdOn) {
        this.createdOn = createdOn;
    }

    public Instant getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Instant getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(final Instant hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AllocatedListing that = (AllocatedListing) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
