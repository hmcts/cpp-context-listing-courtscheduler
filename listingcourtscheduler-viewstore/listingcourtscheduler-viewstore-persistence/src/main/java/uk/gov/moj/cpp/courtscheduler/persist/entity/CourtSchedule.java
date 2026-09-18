package uk.gov.moj.cpp.courtscheduler.persist.entity;

import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.ACTIVE;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.AVAILABLE_DURATION_MINS;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.AVAILABLE_SLOT;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.COURT_HOUSE_ID;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.COURT_HOUSE_NAME;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.COURT_ROOM_ID;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.COURT_ROOM_NAME;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.COURT_ROOM_NUMBER;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.COURT_SESSION;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.CREATED_ON;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.IS_DRAFT;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.IS_OVERBOOKING_ALLOWED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.IS_SLOT_BASED;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.JURISDICTION;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.MAX_AD_AFTERNOON_DURATION;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.MAX_AD_MORNING_DURATION;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.MAX_DURATION_MINS;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.MAX_SLOT;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.OPERATIONAL_UNIT;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.OU_CODE;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.PANEL;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.ROTA_BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.SESSION_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.SESSION_START;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.SESSION_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.SUPPORT_AD_SPLIT;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleColumnNames.UPDATED_ON;
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
                                @ColumnResult(name = COURT_LISTING_PROFILE_ID, type = String.class),
                                @ColumnResult(name = OU_CODE, type = String.class),
                                @ColumnResult(name = COURT_ROOM_ID, type = String.class),
                                @ColumnResult(name = COURT_ROOM_NUMBER, type = Integer.class),
                                @ColumnResult(name = COURT_HOUSE_ID, type = String.class),
                                @ColumnResult(name = COURT_HOUSE_NAME, type = String.class),
                                @ColumnResult(name = COURT_ROOM_NAME, type = String.class),
                                @ColumnResult(name = OPERATIONAL_UNIT, type = String.class),
                                @ColumnResult(name = ROTA_BUSINESS_TYPE, type = String.class),
                                @ColumnResult(name = PANEL, type = String.class),
                                @ColumnResult(name = COURT_SESSION, type = String.class),
                                @ColumnResult(name = ACTIVE, type = Boolean.class),
                                @ColumnResult(name = IS_SLOT_BASED, type = Boolean.class),
                                @ColumnResult(name = SESSION_START, type = LocalDate.class),
                                @ColumnResult(name = MAX_SLOT, type = Integer.class),
                                @ColumnResult(name = MAX_DURATION_MINS, type = Integer.class),
                                @ColumnResult(name = AVAILABLE_SLOT, type = Integer.class),
                                @ColumnResult(name = AVAILABLE_DURATION_MINS, type = Integer.class),
                                @ColumnResult(name = SUPPORT_AD_SPLIT, type = Boolean.class),
                                @ColumnResult(name = MAX_AD_MORNING_DURATION, type = Integer.class),
                                @ColumnResult(name = MAX_AD_AFTERNOON_DURATION, type = Integer.class),
                                @ColumnResult(name = IS_OVERBOOKING_ALLOWED, type = Boolean.class),
                                @ColumnResult(name = SESSION_START_TIME, type = Date.class),
                                @ColumnResult(name = SESSION_END_TIME, type = Date.class),
                                @ColumnResult(name = CREATED_ON, type = Timestamp.class),
                                @ColumnResult(name = UPDATED_ON, type = Timestamp.class),
                                @ColumnResult(name = "hasHearingsBooked", type = Boolean.class),
                                @ColumnResult(name = "totalbookedformorning", type = Integer.class),
                                @ColumnResult(name = "totalbookedforafternoon", type = Integer.class),
                                @ColumnResult(name = "totalbooked", type = Integer.class),
                                @ColumnResult(name = IS_DRAFT, type = Boolean.class),
                                @ColumnResult(name = JURISDICTION, type = String.class)
                        }
                )
        ),
        @SqlResultSetMapping(
                name = "CourtScheduleEntityMappingForSlots",
                classes = @ConstructorResult(
                        targetClass = CourtSchedule.class,
                        columns = {
                                @ColumnResult(name = "id", type = String.class),
                                @ColumnResult(name = COURT_LISTING_PROFILE_ID, type = String.class),
                                @ColumnResult(name = OU_CODE, type = String.class),
                                @ColumnResult(name = COURT_ROOM_ID, type = String.class),
                                @ColumnResult(name = COURT_ROOM_NUMBER, type = Integer.class),
                                @ColumnResult(name = COURT_HOUSE_ID, type = String.class),
                                @ColumnResult(name = COURT_HOUSE_NAME, type = String.class),
                                @ColumnResult(name = COURT_ROOM_NAME, type = String.class),
                                @ColumnResult(name = OPERATIONAL_UNIT, type = String.class),
                                @ColumnResult(name = ROTA_BUSINESS_TYPE, type = String.class),
                                @ColumnResult(name = PANEL, type = String.class),
                                @ColumnResult(name = COURT_SESSION, type = String.class),
                                @ColumnResult(name = ACTIVE, type = Boolean.class),
                                @ColumnResult(name = IS_SLOT_BASED, type = Boolean.class),
                                @ColumnResult(name = SESSION_START, type = LocalDate.class),
                                @ColumnResult(name = MAX_SLOT, type = Integer.class),
                                @ColumnResult(name = MAX_DURATION_MINS, type = Integer.class),
                                @ColumnResult(name = AVAILABLE_SLOT, type = Integer.class),
                                @ColumnResult(name = AVAILABLE_DURATION_MINS, type = Integer.class),
                                @ColumnResult(name = SUPPORT_AD_SPLIT, type = Boolean.class),
                                @ColumnResult(name = MAX_AD_MORNING_DURATION, type = Integer.class),
                                @ColumnResult(name = MAX_AD_AFTERNOON_DURATION, type = Integer.class),
                                @ColumnResult(name = IS_OVERBOOKING_ALLOWED, type = Boolean.class),
                                @ColumnResult(name = SESSION_START_TIME, type = Date.class),
                                @ColumnResult(name = SESSION_END_TIME, type = Date.class),
                                @ColumnResult(name = CREATED_ON, type = Timestamp.class),
                                @ColumnResult(name = UPDATED_ON, type = Timestamp.class),
                                @ColumnResult(name = "national_break_time", type = Date.class),
                                @ColumnResult(name = "totalbookedformorning", type = Integer.class),
                                @ColumnResult(name = "totalbookedforafternoon", type = Integer.class),
                                @ColumnResult(name = "totalbooked", type = Integer.class),
                                @ColumnResult(name = IS_DRAFT, type = Boolean.class),
                                @ColumnResult(name = JURISDICTION, type = String.class)
                        }
                )
        ),
        @SqlResultSetMapping(
                name = "CourtScheduleEntityMappingForView",
                classes = @ConstructorResult(
                        targetClass = CourtSchedule.class,
                        columns = {
                                @ColumnResult(name = "id", type = String.class), // Replace with actual column types
                                @ColumnResult(name = COURT_LISTING_PROFILE_ID, type = String.class),
                                @ColumnResult(name = OU_CODE, type = String.class),
                                @ColumnResult(name = COURT_ROOM_ID, type = String.class),
                                @ColumnResult(name = COURT_ROOM_NUMBER, type = Integer.class),
                                @ColumnResult(name = COURT_HOUSE_ID, type = String.class),
                                @ColumnResult(name = COURT_HOUSE_NAME, type = String.class),
                                @ColumnResult(name = COURT_ROOM_NAME, type = String.class),
                                @ColumnResult(name = OPERATIONAL_UNIT, type = String.class),
                                @ColumnResult(name = ROTA_BUSINESS_TYPE, type = String.class),
                                @ColumnResult(name = PANEL, type = String.class),
                                @ColumnResult(name = COURT_SESSION, type = String.class),
                                @ColumnResult(name = ACTIVE, type = Boolean.class),
                                @ColumnResult(name = IS_SLOT_BASED, type = Boolean.class),
                                @ColumnResult(name = SESSION_START, type = LocalDate.class),
                                @ColumnResult(name = MAX_SLOT, type = Integer.class),
                                @ColumnResult(name = MAX_DURATION_MINS, type = Integer.class),
                                @ColumnResult(name = AVAILABLE_SLOT, type = Integer.class),
                                @ColumnResult(name = AVAILABLE_DURATION_MINS, type = Integer.class),
                                @ColumnResult(name = "hasHearingsBooked", type = Boolean.class),
                                @ColumnResult(name = CREATED_ON, type = Timestamp.class),
                                @ColumnResult(name = UPDATED_ON, type = Timestamp.class),
                                @ColumnResult(name = SUPPORT_AD_SPLIT, type = Boolean.class),
                                @ColumnResult(name = MAX_AD_MORNING_DURATION, type = Integer.class),
                                @ColumnResult(name = MAX_AD_AFTERNOON_DURATION, type = Integer.class),
                                @ColumnResult(name = SESSION_START_TIME, type = Date.class),
                                @ColumnResult(name = SESSION_END_TIME, type = Date.class),
                                @ColumnResult(name = IS_OVERBOOKING_ALLOWED, type = Boolean.class),
                                @ColumnResult(name = "national_break_time" , type = Date.class),
                                @ColumnResult(name = IS_DRAFT, type = Boolean.class),
                                @ColumnResult(name = JURISDICTION, type = String.class),
                        }
                )
        )
})

public class CourtSchedule {

    @Id
    @Column(name = "id", nullable = false)
    private String courtScheduleId;

    @Column(name = COURT_LISTING_PROFILE_ID, nullable = false)
    private String listingProfileId;
    @Column(name = OU_CODE, nullable = false)
    private String ouCode;
    @Column(name = COURT_ROOM_ID, nullable = false)
    private String courtRoomId;
    @Column(name = COURT_ROOM_NUMBER, nullable = false)
    private Integer courtRoomNumber;
    @Column(name = COURT_HOUSE_ID, nullable = false)
    private String courtHouseId;// same as courtCentreId
    @Column(name = COURT_HOUSE_NAME, nullable = false)
    private String courtHouseName;
    @Column(name = COURT_ROOM_NAME, nullable = false)
    private String courtRoomName;
    @Column(name = OPERATIONAL_UNIT, nullable = false)
    private String operationalUnit;
    @Column(name = ROTA_BUSINESS_TYPE, nullable = false)
    private String businessType;
    @Column(name = PANEL, nullable = false)
    private String panel;
    @Column(name = COURT_SESSION, nullable = false)
    private String courtSession;
    @Column(name = ACTIVE, nullable = false)
    private Boolean active;
    @Column(name = IS_SLOT_BASED, nullable = false)
    private Boolean slotBased;
    @Column(name = SESSION_START, nullable = false)
    private LocalDate sessionDate;
    @Column(name = MAX_SLOT, nullable = false)
    private Integer maxSlots;
    @Column(name = MAX_DURATION_MINS, nullable = false)
    private Integer maxDuration;
    @Column(name = AVAILABLE_SLOT, nullable = false)
    private Integer availableSlots;
    @Column(name = AVAILABLE_DURATION_MINS, nullable = false)
    private Integer availableDuration;
    @Transient
    @Column(name = "hasHearingsBooked", nullable = false)
    private Boolean hasHearingsBooked;

    @Column(name = SUPPORT_AD_SPLIT, nullable = false)
    private Boolean supportAdSplit;
    @Column(name = MAX_AD_MORNING_DURATION, nullable = false)
    private Integer maxAdMorningDuration;
    @Column(name = MAX_AD_AFTERNOON_DURATION, nullable = false)
    private Integer maxAdAfternoonDuration;

    @Column(name = SESSION_START_TIME, nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date sessionStartTime;
    @Column(name = SESSION_END_TIME, nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date sessionEndTime;

    @Column(name = IS_OVERBOOKING_ALLOWED, nullable = false)
    private Boolean isOverbookingAllowed;

    @Column(name = IS_DRAFT, nullable = false)
    private Boolean isDraft;

    @Column(name = JURISDICTION, nullable = false)
    private String jurisdiction;

    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = CREATED_ON, nullable = false)
    private Date createdOn;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = UPDATED_ON, nullable = false)
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
            final String id,
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
            final Boolean hasHearingsBooked,
            final Integer totalBookedMorning,
            final Integer totalBookedAfternoon,
            final Integer totalBooked,
            final Boolean isDraft,
            final String jurisdiction
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

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public String getListingProfileId() {
        return listingProfileId;
    }

    public void setListingProfileId(final String listingProfileId) {
        this.listingProfileId = listingProfileId;
    }

    public String getOuCode() {
        return ouCode;
    }

    public void setOuCode(final String ouCode) {
        this.ouCode = ouCode;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public Integer getCourtRoomNumber() {
        return courtRoomNumber;
    }

    public void setCourtRoomNumber(final Integer courtRoomNumber) {
        this.courtRoomNumber = courtRoomNumber;
    }

    public String getCourtHouseId() {
        return courtHouseId;
    }

    public void setCourtHouseId(final String courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public String getCourtHouseName() {
        return courtHouseName;
    }

    public void setCourtHouseName(final String courtHouseName) {
        this.courtHouseName = courtHouseName;
    }

    public String getCourtRoomName() {
        return courtRoomName;
    }

    public void setCourtRoomName(final String courtRoomName) {
        this.courtRoomName = courtRoomName;
    }

    public String getOperationalUnit() {
        return operationalUnit;
    }

    public void setOperationalUnit(final String operationalUnit) {
        this.operationalUnit = operationalUnit;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(final String businessType) {
        this.businessType = businessType;
    }

    public String getPanel() {
        return panel;
    }

    public void setPanel(final String panel) {
        this.panel = panel;
    }

    public String getCourtSession() {
        return courtSession;
    }

    public void setCourtSession(final String courtSession) {
        this.courtSession = courtSession;
    }

    public boolean isSlotBased() {
        return slotBased;
    }

    public void setSlotBased(final boolean slotBased) {
        this.slotBased = slotBased;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(final LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public Integer getMaxSlots() {
        return maxSlots;
    }

    public void setMaxSlots(final Integer maxSlots) {
        this.maxSlots = maxSlots;
    }

    public Integer getMaxDuration() {
        return maxDuration;
    }

    public void setMaxDuration(final Integer maxDuration) {
        this.maxDuration = maxDuration;
    }

    public Integer getAvailableSlots() {
        return availableSlots;
    }

    public void setAvailableSlots(final Integer availableSlots) {
        this.availableSlots = availableSlots;
    }

    public Integer getAvailableDuration() {
        return availableDuration;
    }

    public void setAvailableDuration(final Integer availableDuration) {
        this.availableDuration = availableDuration;
    }

    public java.util.Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(final java.util.Date createdOn) {
        this.createdOn = createdOn;
    }

    public java.util.Date getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(final java.util.Date updatedOn) {
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

    public void setSupportAdSplit(final Boolean supportAdSplit) {
        this.supportAdSplit = supportAdSplit;
    }

    public Integer getMaxAdMorningDuration() {
        return maxAdMorningDuration;
    }

    public void setMaxAdMorningDuration(final Integer maxAdMorningDuration) {
        this.maxAdMorningDuration = maxAdMorningDuration;
    }

    public Integer getMaxAdAfternoonDuration() {
        return maxAdAfternoonDuration;
    }

    public void setMaxAdAfternoonDuration(final Integer maxAdAfternoonDuration) {
        this.maxAdAfternoonDuration = maxAdAfternoonDuration;
    }

    public Date getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(final Date sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    public Date getSessionEndTime() {
        return sessionEndTime;
    }

    public void setSessionEndTime(final Date sessionEndTime) {
        this.sessionEndTime = sessionEndTime;
    }

    public Boolean getIsOverbookingAllowed() {
        return isOverbookingAllowed;
    }

    public void setIsOverbookingAllowed(final Boolean isOverbookingAllowed) {
        this.isOverbookingAllowed = isOverbookingAllowed;
    }

    public Integer getTotalBookedMorning() {
        return totalBookedMorning;
    }

    public void setTotalBookedMorning(final Integer totalBookedMorning) {
        this.totalBookedMorning = totalBookedMorning;
    }

    public Integer getTotalBookedAfternoon() {
        return totalBookedAfternoon;
    }

    public void setTotalBookedAfternoon(final Integer totalBookedAfternoon) {
        this.totalBookedAfternoon = totalBookedAfternoon;
    }

    public Integer getTotalBooked() {
        return totalBooked;
    }

    public void setTotalBooked(final Integer totalBooked) {
        this.totalBooked = totalBooked;
    }

    public Date getNationalBreakTime() {
        return nationalBreakTime;
    }

    public void setNationalBreakTime(final Date nationalBreakTime) {
        this.nationalBreakTime = nationalBreakTime;
    }

    public Boolean getIsDraft() {
        return isDraft;
    }

    public void setIsDraft(final Boolean isDraft) {
        this.isDraft = isDraft;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(final String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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
