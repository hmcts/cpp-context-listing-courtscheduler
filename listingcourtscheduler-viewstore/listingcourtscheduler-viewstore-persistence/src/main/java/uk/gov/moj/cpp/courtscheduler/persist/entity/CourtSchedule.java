package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.ColumnResult;
import javax.persistence.ConstructorResult;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.SqlResultSetMapping;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
@Entity
@Table(name = "court_schedule")
@SqlResultSetMapping(
        name = "CourtScheduleEntityMapping",
        classes = @ConstructorResult(
                targetClass = CourtSchedule.class,
                columns = {
                        @ColumnResult(name = "id", type = String.class), // Replace with actual column types
                        @ColumnResult(name = "court_listing_profile_id", type = String.class),
                        @ColumnResult(name = "oucode", type = String.class),
                        @ColumnResult(name = "court_room_id", type = String.class),
                        @ColumnResult(name = "court_room_number", type = Integer.class),
                        @ColumnResult(name = "court_house_id", type = String.class),
                        @ColumnResult(name = "court_house_name", type = String.class),
                        @ColumnResult(name = "court_room_name", type = String.class),
                        @ColumnResult(name = "operational_unit", type = String.class),
                        @ColumnResult(name = "rota_business_type", type = String.class),
                        @ColumnResult(name = "panel", type = String.class),
                        @ColumnResult(name = "court_session", type = String.class),
                        @ColumnResult(name = "active", type = Boolean.class),
                        @ColumnResult(name = "is_slot_based", type = Boolean.class),
                        @ColumnResult(name = "session_start", type = LocalDate.class),
                        @ColumnResult(name = "max_slot", type = Integer.class),
                        @ColumnResult(name = "max_duration_mins", type = Integer.class),
                        @ColumnResult(name = "available_slot", type = Integer.class),
                        @ColumnResult(name = "available_duration_mins", type = Integer.class),
                        @ColumnResult(name = "hasHearingsBooked", type = Boolean.class),
                        @ColumnResult(name = "created_on", type = Timestamp.class),
                        @ColumnResult(name = "updated_on", type = Timestamp.class)
                }
        )
)
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
    private Boolean active;
    @Column(name = "is_slot_based", nullable = false)
    private Boolean slotBased;
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
    @Transient
    @Column(name = "hasHearingsBooked", nullable = false)
    private Boolean hasHearingsBooked;


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

    //this constructor is used in the SqlResultSetMapping. columnn order is significant!
    public CourtSchedule(final String courtScheduleId,
                         final String listingProfileId,
                         final String ouCode,
                         final String courtRoomId,
                         final Integer courtRoomNumber,
                         final String courtHouseId,
                         final String courtHouseName,
                         final String courtRoomName,
                         final String operationalUnit,
                         final String businessType,
                         final String panel,
                         final String courtSession,
                         final Boolean active,
                         final Boolean slotBased,
                         final LocalDate sessionDate,
                         final Integer maxSlots,
                         final Integer maxDuration,
                         final Integer availableSlots,
                         final Integer availableDuration,
                         final Boolean hasHearingsBooked,
                         final Date createdOn,
                         final Date updatedOn) {
        this.courtRoomNumber = courtRoomNumber;
        this.courtScheduleId = courtScheduleId;
        this.listingProfileId = listingProfileId;
        this.ouCode = ouCode;
        this.courtRoomId = courtRoomId;
        this.courtHouseId = courtHouseId;
        this.courtHouseName = courtHouseName;
        this.courtRoomName = courtRoomName;
        this.operationalUnit = operationalUnit;
        this.businessType = businessType;
        this.panel = panel;
        this.courtSession = courtSession;
        this.active = active;
        this.slotBased = slotBased;
        this.sessionDate = sessionDate;
        this.maxSlots = maxSlots;
        this.maxDuration = maxDuration;
        this.availableSlots = availableSlots;
        this.availableDuration = availableDuration;
        this.hasHearingsBooked = hasHearingsBooked;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
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

    public Boolean isActive() {
        return active;
    }

    public void setActive(final Boolean active) {
        this.active = active;
    }

    public Boolean getHasHearingsBooked() {
        return hasHearingsBooked;
    }

    public CourtSchedule setHasHearingsBooked(final Boolean hasHearingsBooked) {
        this.hasHearingsBooked = hasHearingsBooked;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CourtSchedule that = (CourtSchedule) o;
        return isActive() == that.isActive()
                && isSlotBased() == that.isSlotBased()
                && Objects.equals(getCourtScheduleId(), that.getCourtScheduleId())
                && Objects.equals(getListingProfileId(), that.getListingProfileId())
                && Objects.equals(getOuCode(), that.getOuCode())
                && Objects.equals(getCourtRoomId(), that.getCourtRoomId())
                && Objects.equals(getCourtRoomNumber(), that.getCourtRoomNumber())
                && Objects.equals(getCourtHouseId(), that.getCourtHouseId())
                && Objects.equals(getCourtHouseName(), that.getCourtHouseName())
                && Objects.equals(getCourtRoomName(), that.getCourtRoomName())
                && Objects.equals(getOperationalUnit(), that.getOperationalUnit())
                && Objects.equals(getBusinessType(), that.getBusinessType())
                && Objects.equals(getPanel(), that.getPanel())
                && Objects.equals(getCourtSession(), that.getCourtSession())
                && Objects.equals(getSessionDate(), that.getSessionDate())
                && Objects.equals(getMaxSlots(), that.getMaxSlots())
                && Objects.equals(getMaxDuration(), that.getMaxDuration())
                && Objects.equals(getAvailableSlots(), that.getAvailableSlots())
                && Objects.equals(getAvailableDuration(), that.getAvailableDuration())
                && Objects.equals(getCreatedOn(), that.getCreatedOn())
                && Objects.equals(getUpdatedOn(), that.getUpdatedOn());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCourtScheduleId(), getListingProfileId(), getOuCode(),
                getCourtRoomId(), getCourtRoomNumber(), getCourtHouseId(), getCourtHouseName(),
                getCourtRoomName(), getOperationalUnit(), getBusinessType(), getPanel(), getCourtSession(),
                isActive(), isSlotBased(), getSessionDate(), getMaxSlots(), getMaxDuration(), getAvailableSlots(),
                getAvailableDuration(), getCreatedOn(), getUpdatedOn());
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
                ", active=" + active +
                ", slotBased=" + slotBased +
                ", sessionDate=" + sessionDate +
                ", maxSlots=" + maxSlots +
                ", maxDuration=" + maxDuration +
                ", availableSlots=" + availableSlots +
                ", availableDuration=" + availableDuration +
                ", createdOn=" + createdOn +
                ", updatedOn=" + updatedOn +
                '}';
    }
}
