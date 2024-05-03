package uk.gov.moj.cpp.courtscheduler.persist.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "allocated_listing")
public class AllocatedListing {

    @Id
    private String id;

    @Column(name = "court_schedule_id", nullable = false)
    private String courtScheduleId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    @Column(name = "hearing_id", nullable = false)
    private String hearingId;

    @Column(name = "oucode", nullable = false)
    private String oucode;

    @Column(name = "court_room_id", nullable = false)
    private Integer courtRoomId;

    @Column(name = "rota_business_type", nullable = false)
    private String rotaBusinessType;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "created_on", nullable = false)
    private Timestamp createdOn;

    @Column(name = "updated_on", nullable = false)
    private Timestamp updatedOn;

    @Column(name = "hearing_start_time", nullable = false)
    private Timestamp hearingStartTime;

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

    public Timestamp getCreatedOn() {
        return new Timestamp(createdOn.getTime());
    }

    public void setCreatedOn(Timestamp createdOn) {
        this.createdOn = new Timestamp(createdOn.getTime());
    }

    public Timestamp getUpdatedOn() {
        return new Timestamp(updatedOn.getTime());
    }

    public void setUpdatedOn(Timestamp updatedOn) {
        this.updatedOn = new Timestamp(updatedOn.getTime());
    }

    public Timestamp getHearingStartTime() {
        return new Timestamp(hearingStartTime.getTime());
    }

    public void setHearingStartTime(Timestamp hearingStartTime) {
        this.hearingStartTime = new Timestamp(hearingStartTime.getTime());
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
