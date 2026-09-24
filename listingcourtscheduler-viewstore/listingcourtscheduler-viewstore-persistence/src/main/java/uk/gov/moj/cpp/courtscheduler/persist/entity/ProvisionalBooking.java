package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "provisional_booking")
@SuppressWarnings("squid:S2384")
public class ProvisionalBooking {

    @EmbeddedId
    private ProvisionalBookingKey provisionalBookingKey;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @UpdateTimestamp
    @Column(name = "updated_on", nullable = false)
    private Instant updatedOn;

    @CreationTimestamp
    @Column(name = "created_on", nullable = false)
    private Instant createdOn;

    @Column(name = "hearing_start_time", nullable = false)
    private Instant hearingStartTime;



    public ProvisionalBookingKey getProvisionalBookingKey() {
        return provisionalBookingKey;
    }

    public void setProvisionalBookingKey(final ProvisionalBookingKey provisionalBookingKey) {
        this.provisionalBookingKey = provisionalBookingKey;
    }

    public Boolean isActive() {
        return active;
    }

    public void setActive(final Boolean active) {
        this.active = active;
    }

    public Instant getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(final Instant createdOn) {
        this.createdOn = createdOn;
    }

    public Instant getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(final Instant hearingStartTime) {
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
