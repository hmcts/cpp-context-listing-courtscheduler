package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
@Entity
@Table(name = "court_schedule")
public class CourtSchedule {

    @Id
    @Column(name = "id", nullable = false)
    private String courtScheduleId;

    @Column(name = "court_listing_profile_id", nullable = false)
    private String listingProfileId;
    @Column(name = "oucode", nullable = false)
    private String ouCode;
    @Column(name = "court_room_id", nullable = false)
    private String courtRoomId;
    @Column(name = "court_room_number", nullable = false)
    private Integer courtRoomNumber;
    @Column(name = "court_house_id", nullable = false)
    private String courtHouseId;// same as courtCentreId
    @Column(name = "court_house_name", nullable = false)
    private String courtHouseName;
    @Column(name = "court_room_name", nullable = false)
    private String courtRoomName;
    @Column(name = "operational_unit", nullable = false)
    private String operationalUnit;
    @Column(name = "rota_business_type", nullable = false)
    private String businessType;
    @Column(name = "panel", nullable = false)
    private String panel;
    @Column(name = "court_session", nullable = false)
    private String courtSession;
    @Column(name = "active", nullable = false)
    private boolean active;
    @Column(name = "is_slot_based", nullable = false)
    private boolean slotBased;
    @Column(name = "session_start", nullable = false)
    private LocalDate sessionDate;
    @Column(name = "max_slot", nullable = false)
    private Integer maxSlots;
    @Column(name = "max_duration_mins", nullable = false)
    private Integer maxDuration;
    @Column(name = "available_slot", nullable = false)
    private Integer availableSlots;
    @Column(name = "available_duration_mins", nullable = false)
    private Integer availableDuration;


    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_on", nullable = false)
    private Date createdOn;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_on", nullable = false)
    private Date updatedOn;

    public CourtSchedule() {
        //For JPA
    }

    public CourtSchedule(String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getListingProfileId() {
        return listingProfileId;
    }

    public void setListingProfileId(String listingProfileId) {
        this.listingProfileId = listingProfileId;
    }

    public String getOuCode() {
        return ouCode;
    }

    public void setOuCode(String ouCode) {
        this.ouCode = ouCode;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(String courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public Integer getCourtRoomNumber() {
        return courtRoomNumber;
    }

    public void setCourtRoomNumber(Integer courtRoomNumber) {
        this.courtRoomNumber = courtRoomNumber;
    }

    public String getCourtHouseId() {
        return courtHouseId;
    }

    public void setCourtHouseId(String courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public String getCourtHouseName() {
        return courtHouseName;
    }

    public void setCourtHouseName(String courtHouseName) {
        this.courtHouseName = courtHouseName;
    }

    public String getCourtRoomName() {
        return courtRoomName;
    }

    public void setCourtRoomName(String courtRoomName) {
        this.courtRoomName = courtRoomName;
    }

    public String getOperationalUnit() {
        return operationalUnit;
    }

    public void setOperationalUnit(String operationalUnit) {
        this.operationalUnit = operationalUnit;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getPanel() {
        return panel;
    }

    public void setPanel(String panel) {
        this.panel = panel;
    }

    public String getCourtSession() {
        return courtSession;
    }

    public void setCourtSession(String courtSession) {
        this.courtSession = courtSession;
    }

    public boolean isSlotBased() {
        return slotBased;
    }

    public void setSlotBased(boolean slotBased) {
        this.slotBased = slotBased;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public Integer getMaxSlots() {
        return maxSlots;
    }

    public void setMaxSlots(Integer maxSlots) {
        this.maxSlots = maxSlots;
    }

    public Integer getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(Integer maxDuration) {
        this.maxDuration = maxDuration;
    }

    public Integer getAvailableSlots() {
        return availableSlots;
    }

    public void setAvailableSlots(Integer availableSlots) {
        this.availableSlots = availableSlots;
    }

    public Integer getAvailableDuration() {
        return availableDuration;
    }

    public void setAvailableDuration(Integer availableDuration) {
        this.availableDuration = availableDuration;
    }

    public java.util.Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(java.util.Date createdOn) {
        this.createdOn = createdOn;
    }

    public java.util.Date getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(java.util.Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CourtSchedule that = (CourtSchedule) o;
        return Objects.equals(courtScheduleId, that.courtScheduleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(courtScheduleId);
    }

    @Override
    public String toString() {
        return "CourtSchedule{" +
                "courtScheduleId='" + courtScheduleId + '\'' +
                ", listingProfileId='" + listingProfileId + '\'' +
                ", ouCode='" + ouCode + '\'' +
                ", courtRoomId='" + courtRoomId + '\'' +
                ", courtRoomNumber=" + courtRoomNumber +
                ", courtHouseId='" + courtHouseId + '\'' +
                ", courtHouseName='" + courtHouseName + '\'' +
                ", courtRoomName='" + courtRoomName + '\'' +
                ", operationalUnit='" + operationalUnit + '\'' +
                ", businessType='" + businessType + '\'' +
                ", panel='" + panel + '\'' +
                ", courtSession='" + courtSession + '\'' +
                ", slotBased=" + slotBased +
                ", sessionDate=" + sessionDate +
                ", maxSlots=" + maxSlots +
                ", maxDuration=" + maxDuration +
                ", availableSlots=" + availableSlots +
                ", availableDuration=" + availableDuration +
                ", createdOn=" + createdOn +
                ", updatedOn=" + updatedOn +
                ", active=" + active +
                '}';
    }
}
