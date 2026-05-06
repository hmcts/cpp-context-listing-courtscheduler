package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "allocated_listings")
public class AllocatedListing {

    @Id
    private String id;

    @Column(name = "court_schedule_id", nullable = false)
    private String courtScheduleId;

    @Column(name = "booking_id")
    private String bookingId;

    @Column(name = "hearing_id", nullable = false)
    private String hearingId;

    @Column(name = "oucode", nullable = false)
    private String oucode;

    @Column(name = "court_room_id", nullable = false)
    private Integer courtRoomId;

    @Column(name = "rota_business_type")
    private String rotaBusinessType;

    @Column(name = "duration", nullable = false)
    private Integer duration;

    @Column(name = "hearing_start_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private java.util.Date hearingStartTime;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_on", nullable = false)
    private java.util.Date updatedOn;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_on", nullable = false)
    private java.util.Date createdOn;

    @Column(name = "source", nullable = false)
    private String source;

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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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

    @Override
    public String toString() {
        return "AllocatedListing{" +
                "id='" + id + '\'' +
                ", courtScheduleId='" + courtScheduleId + '\'' +
                ", bookingId='" + bookingId + '\'' +
                ", hearingId='" + hearingId + '\'' +
                ", oucode='" + oucode + '\'' +
                ", courtRoomId=" + courtRoomId +
                ", rotaBusinessType='" + rotaBusinessType + '\'' +
                ", duration=" + duration +
                ", hearingStartTime=" + hearingStartTime +
                ", updatedOn=" + updatedOn +
                ", createdOn=" + createdOn +
                ", source=" + source +
                '}';
    }
}
