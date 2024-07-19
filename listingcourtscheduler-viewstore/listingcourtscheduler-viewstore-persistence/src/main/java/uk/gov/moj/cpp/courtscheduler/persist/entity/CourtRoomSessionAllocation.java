package uk.gov.moj.cpp.courtscheduler.persist.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "court_room_session_allocation")
public class CourtRoomSessionAllocation {
    @Id
    private SessionAllocationKey sessionAllocationKey;
    @Column(name = "max_slot", nullable = false)
    private Integer maxSlot;
    @Column(name = "max_duration_mins", nullable = false)
    private Integer maxDurationMins;
    @Column(name = "court_house_id", nullable = false)
    private String exhibitHearingCode;
    @Column(name = "active", nullable = false)
    private String active;
    @Column(name = "created_on", nullable = false)
    private String createdOn;

    public CourtRoomSessionAllocation() {
        //For JPA
    }

    public SessionAllocationKey getSessionAllocationKey() {
        return sessionAllocationKey;
    }

    public void setSessionAllocationKey(SessionAllocationKey sessionAllocationKey) {
        this.sessionAllocationKey = sessionAllocationKey;
    }

    public Integer getMaxSlot() {
        return maxSlot;
    }

    public void setMaxSlot(Integer maxSlot) {
        this.maxSlot = maxSlot;
    }

    public Integer getMaxDurationMins() {
        return maxDurationMins;
    }

    public void setMaxDurationMins(Integer maxDurationMins) {
        this.maxDurationMins = maxDurationMins;
    }

    public String getExhibitHearingCode() {
        return exhibitHearingCode;
    }

    public void setExhibitHearingCode(String exhibitHearingCode) {
        this.exhibitHearingCode = exhibitHearingCode;
    }

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }
}
