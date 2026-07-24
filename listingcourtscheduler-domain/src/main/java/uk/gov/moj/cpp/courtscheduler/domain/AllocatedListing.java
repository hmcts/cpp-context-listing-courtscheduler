package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Date;
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

    private Date hearingStartTime;

    private Date updatedOn;

    private Date createdOn;

    public AllocatedListing() {
        //For JPA
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getHearingId() {
        return hearingId;
    }

    public void setHearingId(String hearingId) {
        this.hearingId = hearingId;
    }

    public String getOucode() {
        return oucode;
    }

    public void setOucode(String oucode) {
        this.oucode = oucode;
    }

    public Integer getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(Integer courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public String getRotaBusinessType() {
        return rotaBusinessType;
    }

    public void setRotaBusinessType(String rotaBusinessType) {
        this.rotaBusinessType = rotaBusinessType;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    public Date getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Date getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(Date hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    @Override
    public boolean equals(Object o) {
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
