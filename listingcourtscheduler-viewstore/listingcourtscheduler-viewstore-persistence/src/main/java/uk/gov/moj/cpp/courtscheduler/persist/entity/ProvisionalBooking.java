package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.util.Date;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "provisional_booking")
@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class ProvisionalBooking {

    @EmbeddedId
    private ProvisionalBookingKey provisionalBookingKey;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_on", nullable = false)
    private java.util.Date updatedOn;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_on", nullable = false)
    private java.util.Date createdOn;


    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "hearing_start_time", nullable = false)
    private java.util.Date hearingStartTime;


    public ProvisionalBooking() {
        //For JPA
    }

    public ProvisionalBookingKey getProvisionalBookingKey() {
        return provisionalBookingKey;
    }

    public void setProvisionalBookingKey(ProvisionalBookingKey provisionalBookingKey) {
        this.provisionalBookingKey = provisionalBookingKey;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public java.util.Date getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(java.util.Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    public java.util.Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(java.util.Date createdOn) {
        this.createdOn = createdOn;
    }

    public Date getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(Date hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    @Override
    public String toString() {
        return "ProvisionalBooking{" +
                "provisionalBookingKey=" + provisionalBookingKey +
                ", active=" + active +
                ", updatedOn=" + updatedOn +
                ", createdOn=" + createdOn +
                ", hearingStartTime=" + hearingStartTime +
                '}';
    }
}
