package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.ColumnResult;
import jakarta.persistence.ConstructorResult;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.SqlResultSetMapping;
import jakarta.persistence.SqlResultSetMappings;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
@Entity
@Table(name = "court_schedule")
@SqlResultSetMappings({
        @SqlResultSetMapping(
                name = "CourtScheduleEntityMappingForAllFields",
                classes = @ConstructorResult(
                        targetClass = CourtSchedule.class,
                        columns = {
                                @ColumnResult(name = "id", type = String.class),
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
                                @ColumnResult(name = "support_ad_split", type = Boolean.class),
                                @ColumnResult(name = "max_ad_morning_duration", type = Integer.class),
                                @ColumnResult(name = "max_ad_afternoon_duration", type = Integer.class),
                                @ColumnResult(name = "is_overbooking_allowed", type = Boolean.class),
                                @ColumnResult(name = "session_start_time", type = Date.class),
                                @ColumnResult(name = "session_end_time", type = Date.class),
                                @ColumnResult(name = "created_on", type = Timestamp.class),
                                @ColumnResult(name = "updated_on", type = Timestamp.class),
                                @ColumnResult(name = "hasHearingsBooked", type = Boolean.class),
                                @ColumnResult(name = "totalbookedformorning", type = Integer.class),
                                @ColumnResult(name = "totalbookedforafternoon", type = Integer.class),
                                @ColumnResult(name = "totalbooked", type = Integer.class),
                                @ColumnResult(name = "is_draft", type = Boolean.class),
                                @ColumnResult(name = "jurisdiction", type = String.class)
                        }
                )
        ),
        @SqlResultSetMapping(
                name = "CourtScheduleEntityMappingForSlots",
                classes = @ConstructorResult(
                        targetClass = CourtSchedule.class,
                        columns = {
                                @ColumnResult(name = "id", type = String.class),
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
                                @ColumnResult(name = "support_ad_split", type = Boolean.class),
                                @ColumnResult(name = "max_ad_morning_duration", type = Integer.class),
                                @ColumnResult(name = "max_ad_afternoon_duration", type = Integer.class),
                                @ColumnResult(name = "is_overbooking_allowed", type = Boolean.class),
                                @ColumnResult(name = "session_start_time", type = Date.class),
                                @ColumnResult(name = "session_end_time", type = Date.class),
                                @ColumnResult(name = "created_on", type = Timestamp.class),
                                @ColumnResult(name = "updated_on", type = Timestamp.class),
                                @ColumnResult(name = "national_break_time", type = Date.class),
                                @ColumnResult(name = "totalbookedformorning", type = Integer.class),
                                @ColumnResult(name = "totalbookedforafternoon", type = Integer.class),
                                @ColumnResult(name = "totalbooked", type = Integer.class),
                                @ColumnResult(name = "is_draft", type = Boolean.class),
                                @ColumnResult(name = "jurisdiction", type = String.class)
                        }
                )
        ),
        @SqlResultSetMapping(
                name = "CourtScheduleEntityMappingForView",
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
                                @ColumnResult(name = "updated_on", type = Timestamp.class),
                                @ColumnResult(name = "support_ad_split", type = Boolean.class),
                                @ColumnResult(name = "max_ad_morning_duration", type = Integer.class),
                                @ColumnResult(name = "max_ad_afternoon_duration", type = Integer.class),
                                @ColumnResult(name = "session_start_time", type = Date.class),
                                @ColumnResult(name = "session_end_time", type = Date.class),
                                @ColumnResult(name = "is_overbooking_allowed", type = Boolean.class),
                                @ColumnResult(name = "national_break_time" , type = Date.class),
                                @ColumnResult(name = "is_draft", type = Boolean.class),
                                @ColumnResult(name = "jurisdiction", type = String.class),
                        }
                )
        )
})

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

    @Column(name = "support_ad_split", nullable = false)
    private Boolean supportAdSplit;
    @Column(name = "max_ad_morning_duration", nullable = false)
    private Integer maxAdMorningDuration;
    @Column(name = "max_ad_afternoon_duration", nullable = false)
    private Integer maxAdAfternoonDuration;

    @Column(name = "session_start_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date sessionStartTime;
    @Column(name = "session_end_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date sessionEndTime;

    @Column(name = "is_overbooking_allowed", nullable = false)
    private Boolean isOverbookingAllowed;

    @Column(name = "is_draft", nullable = false)
    private Boolean isDraft;

    @Column(name = "jurisdiction", nullable = false)
    private String jurisdiction;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_on", nullable = false)
    private Date createdOn;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_on", nullable = false)
    private Date updatedOn;

    @Transient
    private Integer totalBookedMorning;

    @Transient
    private Integer totalBookedAfternoon;

    @Transient
    private Integer totalBooked;

    @Column(name = "national_break_time", nullable = true)
    @Temporal(TemporalType.TIMESTAMP)
    private Date nationalBreakTime;

    public CourtSchedule() {
        //For JPA
    }

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
                         final Date updatedOn,
                         final Boolean supportAdSplit,
                         final Integer maxAdMorningDuration,
                         final Integer maxAdAfternoonDuration,
                         final Date sessionStartTime,
                         final Date sessionEndTime,
                         final Boolean isOverbookingAllowed,
                         final Date nationalBreakTime,
                         final Boolean isDraft,
                         final String jurisdiction) {
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
        this.supportAdSplit = supportAdSplit;
        this.maxAdMorningDuration = maxAdMorningDuration;
        this.maxAdAfternoonDuration = maxAdAfternoonDuration;
        this.sessionStartTime = sessionStartTime;
        this.sessionEndTime = sessionEndTime;
        this.isOverbookingAllowed = isOverbookingAllowed;
        this.nationalBreakTime = nationalBreakTime;
        this.isDraft = isDraft;
        this.jurisdiction = jurisdiction;
    }

    //this constructor is used in the SqlResultSetMapping. columnn order is significant!
    public CourtSchedule(final String id,
                         final String courtListingProfileId,
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
                         final Boolean supportAdSplit,
                         final Integer maxAdMorningDuration,
                         final Integer maxAdAfternoonDuration,
                         final Boolean isOverbookingAllowed,
                         final Date sessionStartTime,
                         final Date sessionEndTime,
                         final Date createdOn,
                         final Date updatedOn,
                         final Date nationalBreakTime,
                         final Integer totalBookedMorning,
                         final Integer totalBookedAfternoon,
                         final Integer totalBooked,
                         final Boolean isDraft,
                         final String jurisdiction) {
        this.courtScheduleId = id;
        this.listingProfileId = courtListingProfileId;
        this.ouCode = ouCode;
        this.courtRoomId = courtRoomId;
        this.courtRoomNumber = courtRoomNumber;
        this.courtHouseId = courtHouseId;
        this.courtHouseName = courtHouseName;
        this.courtRoomName = courtRoomName;
        this.operationalUnit = operationalUnit;
        this.businessType = businessType;
        this.panel = panel;
        this.courtSession = courtSession;
        this.slotBased = slotBased;
        this.active = active;
        this.sessionDate = sessionDate;
        this.maxSlots = maxSlots;
        this.availableSlots = availableSlots;
        this.maxDuration = maxDuration;
        this.availableDuration = availableDuration;
        this.maxAdMorningDuration = maxAdMorningDuration;
        this.maxAdAfternoonDuration = maxAdAfternoonDuration;
        this.isOverbookingAllowed = isOverbookingAllowed;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
        this.nationalBreakTime = nationalBreakTime;
        this.supportAdSplit = supportAdSplit;
        this.totalBookedMorning = totalBookedMorning;
        this.totalBookedAfternoon = totalBookedAfternoon;
        this.sessionStartTime = sessionStartTime;
        this.sessionEndTime = sessionEndTime;
        this.totalBooked = totalBooked;
        this.isDraft = isDraft;
        this.jurisdiction = jurisdiction;
    }

    public CourtSchedule(
            String id,
            String courtListingProfileId,
            String ouCode,
            String courtRoomId,
            Integer courtRoomNumber,
            String courtHouseId,
            String courtHouseName,
            String courtRoomName,
            String operationalUnit,
            String businessType,
            String panel,
            String courtSession,
            Boolean active,
            Boolean slotBased,
            LocalDate sessionDate,
            Integer maxSlots,
            Integer maxDuration,
            Integer availableSlots,
            Integer availableDuration,
            Boolean supportAdSplit,
            Integer maxAdMorningDuration,
            Integer maxAdAfternoonDuration,
            Boolean isOverbookingAllowed,
            Date sessionStartTime,
            Date sessionEndTime,
            Date createdOn,
            Date updatedOn,
            Boolean hasHearingsBooked,
            Integer totalBookedMorning,
            Integer totalBookedAfternoon,
            Integer totalBooked,
            Boolean isDraft,
            String jurisdiction
    ) {
        this.courtScheduleId = id;
        this.listingProfileId = courtListingProfileId;
        this.ouCode = ouCode;
        this.courtRoomId = courtRoomId;
        this.courtRoomNumber = courtRoomNumber;
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
        this.supportAdSplit = supportAdSplit;
        this.maxAdMorningDuration = maxAdMorningDuration;
        this.maxAdAfternoonDuration = maxAdAfternoonDuration;
        this.isOverbookingAllowed = isOverbookingAllowed;
        this.sessionStartTime = sessionStartTime;
        this.sessionEndTime = sessionEndTime;
        this.createdOn = createdOn;
        this.updatedOn = updatedOn;
        this.hasHearingsBooked = hasHearingsBooked;
        this.totalBookedMorning = totalBookedMorning;
        this.totalBookedAfternoon = totalBookedAfternoon;
        this.totalBooked = totalBooked;
        this.isDraft = isDraft;
        this.jurisdiction = jurisdiction;
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

    public Boolean getSupportAdSplit() {
        return supportAdSplit;
    }

    public void setSupportAdSplit(Boolean supportAdSplit) {
        this.supportAdSplit = supportAdSplit;
    }

    public Integer getMaxAdMorningDuration() {
        return maxAdMorningDuration;
    }

    public void setMaxAdMorningDuration(Integer maxAdMorningDuration) {
        this.maxAdMorningDuration = maxAdMorningDuration;
    }

    public Integer getMaxAdAfternoonDuration() {
        return maxAdAfternoonDuration;
    }

    public void setMaxAdAfternoonDuration(Integer maxAdAfternoonDuration) {
        this.maxAdAfternoonDuration = maxAdAfternoonDuration;
    }

    public Date getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(Date sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    public Date getSessionEndTime() {
        return sessionEndTime;
    }

    public void setSessionEndTime(Date sessionEndTime) {
        this.sessionEndTime = sessionEndTime;
    }

    public Boolean getIsOverbookingAllowed() {
        return isOverbookingAllowed;
    }

    public void setIsOverbookingAllowed(Boolean isOverbookingAllowed) {
        this.isOverbookingAllowed = isOverbookingAllowed;
    }

    public Integer getTotalBookedMorning() {
        return totalBookedMorning;
    }

    public void setTotalBookedMorning(Integer totalBookedMorning) {
        this.totalBookedMorning = totalBookedMorning;
    }

    public Integer getTotalBookedAfternoon() {
        return totalBookedAfternoon;
    }

    public void setTotalBookedAfternoon(Integer totalBookedAfternoon) {
        this.totalBookedAfternoon = totalBookedAfternoon;
    }

    public Integer getTotalBooked() {
        return totalBooked;
    }

    public void setTotalBooked(Integer totalBooked) {
        this.totalBooked = totalBooked;
    }

    public Date getNationalBreakTime() {
        return nationalBreakTime;
    }

    public void setNationalBreakTime(Date nationalBreakTime) {
        this.nationalBreakTime = nationalBreakTime;
    }

    public Boolean getIsDraft() {
        return isDraft;
    }

    public void setIsDraft(Boolean isDraft) {
        this.isDraft = isDraft;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
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
                && Objects.equals(getUpdatedOn(), that.getUpdatedOn())
                && Objects.equals(getSupportAdSplit(), that.getSupportAdSplit())
                && Objects.equals(getMaxAdMorningDuration(), that.getMaxAdMorningDuration())
                && Objects.equals(getMaxAdAfternoonDuration(), that.getMaxAdAfternoonDuration())
                && Objects.equals(getSessionStartTime(), that.getSessionStartTime())
                && Objects.equals(getSessionEndTime(), that.getSessionEndTime())
                && Objects.equals(getIsOverbookingAllowed(), that.getIsOverbookingAllowed())
                && Objects.equals(getTotalBookedMorning(), that.getTotalBookedMorning())
                && Objects.equals(getTotalBookedAfternoon(), that.getTotalBookedAfternoon())
                && Objects.equals(getTotalBooked(), that.getTotalBooked())
                && Objects.equals(getNationalBreakTime(), that.getNationalBreakTime())
                && Objects.equals(getIsDraft(), that.getIsDraft())
                && Objects.equals(getJurisdiction(), that.getJurisdiction());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCourtScheduleId(), getListingProfileId(), getOuCode(),
                getCourtRoomId(), getCourtRoomNumber(), getCourtHouseId(), getCourtHouseName(),
                getCourtRoomName(), getOperationalUnit(), getBusinessType(), getPanel(), getCourtSession(),
                isActive(), isSlotBased(), getSessionDate(), getMaxSlots(), getMaxDuration(), getAvailableSlots(),
                getAvailableDuration(), getCreatedOn(), getUpdatedOn(), getSupportAdSplit(), getMaxAdMorningDuration(), getMaxAdAfternoonDuration(),
                getSessionStartTime(), getSessionEndTime(), getIsOverbookingAllowed(), getTotalBookedMorning(), getTotalBookedAfternoon(), getTotalBooked(), getNationalBreakTime(), getIsDraft(), getJurisdiction());
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
                ", supportAdSplit=" + supportAdSplit +
                ", maxAdMorningDuration=" + maxAdMorningDuration +
                ", maxAdAfternoonDuration=" + maxAdAfternoonDuration +
                ", sessionStartTime=" + sessionStartTime +
                ", sessionEndTime=" + sessionEndTime +
                ", isOverbookingAllowed=" + isOverbookingAllowed +
                ", totalBookedMorning=" + totalBookedMorning +
                ", totalBookedAfternoon=" + totalBookedAfternoon +
                ", totalBooked=" + totalBooked +
                ", nationalBreakTime=" + nationalBreakTime +
                ", isDraft=" + isDraft +
                ", jurisdiction='" + jurisdiction + '\'' +
                '}';
    }
}
