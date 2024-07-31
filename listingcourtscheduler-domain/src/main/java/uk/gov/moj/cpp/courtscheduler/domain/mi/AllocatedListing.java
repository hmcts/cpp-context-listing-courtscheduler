package uk.gov.moj.cpp.courtscheduler.domain.mi;

import java.util.Date;
import java.util.Objects;

public class AllocatedListing {

    private String id;

    private String court_schedule_id;

    private String booking_id;

    private String hearing_id;

    private String oucode;

    private Integer court_room_id;

    private String rota_business_type;

    private Integer duration;

    private Date hearing_start_time;

    private Date updated_on;

    private Date created_on;

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
        return court_schedule_id;
    }

    public void setCourtScheduleId(String courtScheduleId) {
        this.court_schedule_id = courtScheduleId;
    }

    public String getBookingId() {
        return booking_id;
    }

    public void setBookingId(String bookingId) {
        this.booking_id = bookingId;
    }

    public String getHearingId() {
        return hearing_id;
    }

    public void setHearingId(String hearingId) {
        this.hearing_id = hearingId;
    }

    public String getOucode() {
        return oucode;
    }

    public void setOucode(String oucode) {
        this.oucode = oucode;
    }

    public Integer getCourtRoomId() {
        return court_room_id;
    }

    public void setCourtRoomId(Integer courtRoomId) {
        this.court_room_id = courtRoomId;
    }

    public String getRotaBusinessType() {
        return rota_business_type;
    }

    public void setRotaBusinessType(String rotaBusinessType) {
        this.rota_business_type = rotaBusinessType;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public Date getCreatedOn() {
        return created_on;
    }

    public void setCreatedOn(Date createdOn) {
        this.created_on = createdOn;
    }

    public Date getUpdatedOn() {
        return updated_on;
    }

    public void setUpdatedOn(Date updatedOn) {
        this.updated_on = updatedOn;
    }

    public Date getHearingStartTime() {
        return hearing_start_time;
    }

    public void setHearingStartTime(Date hearingStartTime) {
        this.hearing_start_time = hearingStartTime;
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
