package uk.gov.moj.cpp.courtscheduler.persist.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.sql.Timestamp;

@Entity
@Table(name = "provisional_booking")
@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class ProvisionalBooking {

    @Id
    private ProvisionalBookingKey provisionalBookingKey;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "updated_on", nullable = false)
    private Timestamp updatedOn;

    @Column(name = "created_on", nullable = false)
    private Timestamp createdOn;

    @Column(name = "hearing_start_time", nullable = false)
    private String hearingStartTime;

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

    public Timestamp getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(Timestamp updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Timestamp getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Timestamp createdOn) {
        this.createdOn = createdOn;
    }

    public String getHearingStartTime() {
        return hearingStartTime;
    }

    public void setHearingStartTime(String hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }
}
