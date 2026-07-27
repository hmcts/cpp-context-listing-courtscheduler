package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class CourtSchedule {

    private String courtScheduleId;
    private String listingProfileId;
    private String ouCode;
    private String courtRoomId;
    private Integer courtRoomNumber;
    private String courtHouseId;// same as courtCentreId
    private String courtHouseName;
    private String courtRoomName;
    private String operationalUnit;
    private String businessType;
    private String businessDescription;
    private String panel;
    private String courtSession;
    private boolean slotBased;
    private boolean active;
    private LocalDate sessionDate;
    private Integer maxSlots;
    private Integer availableSlots;
    private Integer maxDuration;
    private Integer availableDuration;
    private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
    private List<SlotStartTime> slotStartTimes = new ArrayList<>();

    private Date createdOn;
    private Date updatedOn;

    private boolean allDaySplit;
    private Integer maxDurationForMorning = 0;
    private Integer maxDurationForAfternoon = 0;
    private Integer totalBooked = 0;
    private Integer availableDurationForMorning = 0;
    private Integer availableDurationForAfternoon = 0;

    private Integer totalBookedForMorning = 0;
    private Integer totalBookedForAfternoon = 0;
    private boolean isOverbookingAllowed;

    private boolean isDraft;

    private String jurisdiction;

    private Date sessionStartTime;
    private Date sessionEndTime;
    private Date nationalBreakTime;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String minHearingTime;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String maxHearingTime;

    protected CourtSchedule(final CourtScheduleBuilder builder) {
        this.courtScheduleId = builder.courtScheduleId;
        this.listingProfileId = builder.listingProfileId;
        this.ouCode = builder.ouCode;
        this.courtRoomId = builder.courtRoomId;
        this.courtRoomNumber = builder.courtRoomNumber;
        this.courtHouseName = builder.courtHouseName;
        this.courtHouseId = builder.courtHouseId;
        this.courtRoomName = builder.courtRoomName;
        this.operationalUnit = builder.operationalUnit;
        this.businessType = builder.businessType;
        this.businessDescription = builder.businessDescription;
        this.panel = builder.panel;
        this.courtSession = builder.courtSession;
        this.sessionDate = builder.sessionDate;
        this.maxSlots = builder.maxSlots;
        this.maxDuration = builder.maxDuration;
        this.availableSlots = builder.availableSlots;
        this.availableDuration = builder.availableDuration;
        this.judiciaries = builder.judiciaries;
        this.slotStartTimes = builder.slotStartTimes;
        this.slotBased = builder.slotBased;
        this.active = builder.active;
        this.createdOn = builder.createdOn;
        this.updatedOn = builder.updatedOn;
        this.allDaySplit = builder.allDaySplit;
        this.maxDurationForMorning = builder.maxDurationForMorning;
        this.maxDurationForAfternoon = builder.maxDurationForAfternoon;
        this.totalBookedForMorning = builder.totalBookedForMorning;
        this.totalBookedForAfternoon = builder.totalBookedForAfternoon;
        this.availableDurationForMorning = builder.availableDurationForMorning;
        this.availableDurationForAfternoon = builder.availableDurationForAfternoon;
        this.totalBooked = builder.totalBooked;
        this.sessionStartTime = builder.sessionStartTime;
        this.sessionEndTime = builder.sessionEndTime;
        this.isOverbookingAllowed = builder.isOverbookingAllowed;
        this.nationalBreakTime = builder.nationalBreakTime;
        this.isDraft = builder.isDraft;
        this.minHearingTime = builder.minHearingTime;
        this.maxHearingTime = builder.maxHearingTime;

        this.jurisdiction = builder.jurisdiction;
    }

    public CourtSchedule() {
    }

    public String getOperationalUnit() {
        return operationalUnit;
    }

    public String getPanel() {
        return panel;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public String getListingProfileId() {
        return listingProfileId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public String getOuCode() {
        return ouCode;
    }

    public String getCourtHouseName() {
        return courtHouseName;
    }

    public String getCourtHouseId() {
        return courtHouseId;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public Integer getCourtRoomNumber() {
        return courtRoomNumber;
    }

    public String getCourtRoomName() {
        return courtRoomName;
    }

    public String getBusinessType() {
        return businessType;
    }

    public String getCourtSession() {
        return courtSession;
    }

    public Integer getAvailableSlots() {
        return availableSlots;
    }

    public Integer getAvailableDuration() {
        return availableDuration;
    }

    public Integer getMaxSlots() {
        return maxSlots;
    }

    public Integer getMaxDuration() {
        return maxDuration;
    }

    public List<CourtScheduleJudiciary> getJudiciaries() {
        return judiciaries;
    }

    public List<SlotStartTime> getSlotStartTimes() {
        return slotStartTimes;
    }

    public boolean isSlotBased() {
        return slotBased;
    }

    public boolean isActive() {
        return active;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public void setListingProfileId(final String listingProfileId) {
        this.listingProfileId = listingProfileId;
    }

    public void setOuCode(final String ouCode) {
        this.ouCode = ouCode;
    }

    public void setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public void setCourtRoomNumber(final Integer courtRoomNumber) {
        this.courtRoomNumber = courtRoomNumber;
    }

    public void setCourtHouseId(final String courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public void setCourtHouseName(final String courtHouseName) {
        this.courtHouseName = courtHouseName;
    }

    public void setCourtRoomName(final String courtRoomName) {
        this.courtRoomName = courtRoomName;
    }

    public void setOperationalUnit(final String operationalUnit) {
        this.operationalUnit = operationalUnit;
    }

    public void setBusinessType(final String businessType) {
        this.businessType = businessType;
    }

    public void setPanel(final String panel) {
        this.panel = panel;
    }

    public void setActive(final Boolean active) {
        this.active = active;
    }

    public void setCourtSession(final String courtSession) {
        this.courtSession = courtSession;
    }

    public void setSlotBased(final boolean slotBased) {
        this.slotBased = slotBased;
    }

    public void setSessionDate(final LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public void setMaxSlots(final Integer maxSlots) {
        this.maxSlots = maxSlots;
    }

    public void setMaxDuration(final Integer maxDuration) {
        this.maxDuration = maxDuration;
    }

    public void setAvailableSlots(final Integer availableSlots) {
        this.availableSlots = availableSlots;
    }

    public void setAvailableDuration(final Integer availableDuration) {
        this.availableDuration = availableDuration;
    }

    public void setJudiciaries(final List<CourtScheduleJudiciary> judiciaries) {
        this.judiciaries = judiciaries;
    }

    public void setSlotStartTimes(final List<SlotStartTime> slotStartTimes) {
        this.slotStartTimes = slotStartTimes;
    }

    public String getBusinessDescription() {
        return businessDescription;
    }

    public void setBusinessDescription(String businessDescription) {
        this.businessDescription = businessDescription;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public boolean isAllDaySplit() {
        return allDaySplit;
    }

    public void setAllDaySplit(final boolean allDaySplit) {
        this.allDaySplit = allDaySplit;
    }

    public Integer getMaxDurationForMorning() {
        return maxDurationForMorning;
    }

    public void setMaxDurationForMorning(final Integer maxDurationForMorning) {
        this.maxDurationForMorning = maxDurationForMorning;
    }

    public Integer getMaxDurationForAfternoon() {
        return maxDurationForAfternoon;
    }

    public void setMaxDurationForAfternoon(final Integer maxDurationForAfternoon) {
        this.maxDurationForAfternoon = maxDurationForAfternoon;
    }

    public Integer getTotalBookedForMorning() {
        return totalBookedForMorning;
    }

    public void setTotalBookedForMorning(final Integer totalBookedForMorning) {
        this.totalBookedForMorning = totalBookedForMorning;
    }

    public void setTotalBookedForAfternoon(final Integer totalBookedForAfternoon) {
        this.totalBookedForAfternoon = totalBookedForAfternoon;
    }

    public Integer getTotalBookedForAfternoon() {
        return totalBookedForAfternoon;
    }

    public Integer getAvailableDurationForMorning() {
        return availableDurationForMorning;
    }

    public void setAvailableDurationForMorning(final Integer availableDurationForMorning) {
        this.availableDurationForMorning = availableDurationForMorning;
    }

    public Integer getAvailableDurationForAfternoon() {
        return availableDurationForAfternoon;
    }

    public void setAvailableDurationForAfternoon(final Integer availableDurationForAfternoon) {
        this.availableDurationForAfternoon = availableDurationForAfternoon;
    }

    public Integer getTotalBooked() {
        return totalBooked;
    }

    public void setTotalBooked(final Integer totalBooked) {
        this.totalBooked = totalBooked;
    }

    public Date getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(Date sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    public void setSessionEndTime(Date sessionEndTime) {
        this.sessionEndTime = sessionEndTime;
    }

    public Date getSessionEndTime() {
        return sessionEndTime;
    }

    public boolean isOverbookingAllowed() {
        return isOverbookingAllowed;
    }

    public void setIsOverbookingAllowed(boolean isOverbookingAllowed) {
        this.isOverbookingAllowed = isOverbookingAllowed;
    }

    public Date getNationalBreakTime() {
        return nationalBreakTime;
    }

    public void setNationalBreakTime(Date nationalBreakTime) {
        this.nationalBreakTime = nationalBreakTime;
    }

    public boolean isDraft() {
        return isDraft;
    }

    public void setIsDraft(boolean isDraft) {
        this.isDraft = isDraft;
    }

    public String getMinHearingTime() {
        return minHearingTime;
    }

    public void setMinHearingTime(String minHearingTime) {
        this.minHearingTime = minHearingTime;
    }

    public String getMaxHearingTime() {
        return maxHearingTime;
    }

    public void setMaxHearingTime(String maxHearingTime) {
        this.maxHearingTime = maxHearingTime;
    }

    public String getJurisdiction() {
        return jurisdiction;
    }

    public void setJurisdiction(String jurisdiction) {
        this.jurisdiction = jurisdiction;
    }

    public static final class CourtScheduleBuilder {

        private String courtScheduleId;
        private String ouCode;
        private String listingProfileId;
        private String courtRoomId;
        private Integer courtRoomNumber;
        private String courtHouseName;
        private String courtHouseId;// same as courtCentreId
        private String courtRoomName;
        private String operationalUnit;
        private String businessType;
        private String businessDescription;
        private String panel;
        private LocalDate sessionDate;
        private Integer maxSlots = 0;
        private Integer maxDuration = 0;
        private Integer availableSlots = 0;
        private Integer availableDuration = 0;
        private String courtSession;
        private boolean slotBased;
        private boolean active;
        private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
        private List<SlotStartTime> slotStartTimes = new ArrayList<>();

        private Date createdOn;
        private Date updatedOn;

        private boolean allDaySplit;
        private Integer maxDurationForMorning = 0;
        private Integer maxDurationForAfternoon = 0;
        private Integer totalBooked = 0;
        private Integer totalBookedForMorning = 0;
        private Integer totalBookedForAfternoon = 0;
        private Integer availableDurationForMorning = 0;
        private Integer availableDurationForAfternoon = 0;
        private boolean isOverbookingAllowed;
        private boolean isDraft;
        private String jurisdiction;

        private Date sessionStartTime;
        private Date sessionEndTime;
        private Date nationalBreakTime;
        private String minHearingTime;
        private String maxHearingTime;

        public static CourtSchedule.CourtScheduleBuilder courtSchedule() {
            return new CourtSchedule.CourtScheduleBuilder();
        }

        public List<SlotStartTime> getSlotStartTimes() {
            return slotStartTimes;
        }

        public List<CourtScheduleJudiciary> getJudiciaries() {
            return judiciaries;
        }

        public boolean isActive() {
            return active;
        }

        public boolean isSlotBased() {
            return slotBased;
        }

        public String getCourtSession() {
            return courtSession;
        }

        public Integer getAvailableDuration() {
            return availableDuration;
        }

        public Integer getAvailableSlots() {
            return availableSlots;
        }

        public Integer getMaxDuration() {
            return maxDuration;
        }

        public Integer getMaxSlots() {
            return maxSlots;
        }

        public LocalDate getSessionDate() {
            return sessionDate;
        }

        public String getPanel() {
            return panel;
        }

        public String getBusinessType() {
            return businessType;
        }

        public String getBusinessDescription() {
            return businessDescription;
        }

        public String getOperationalUnit() {
            return operationalUnit;
        }

        public String getCourtRoomName() {
            return courtRoomName;
        }

        public String getCourtHouseId() {
            return courtHouseId;
        }

        public String getCourtHouseName() {
            return courtHouseName;
        }

        public Integer getCourtRoomNumber() {
            return courtRoomNumber;
        }

        public String getCourtRoomId() {
            return courtRoomId;
        }

        public String getListingProfileId() {
            return listingProfileId;
        }

        public String getOuCode() {
            return ouCode;
        }

        public String getCourtScheduleId() {
            return courtScheduleId;
        }

        public boolean isAllDaySplit() {
            return allDaySplit;
        }

        public Integer getMaxDurationForMorning() {
            return maxDurationForMorning;
        }

        public Integer getMaxDurationForAfternoon() {
            return maxDurationForAfternoon;
        }

        public Integer getTotalBooked() {
            return totalBooked;
        }

        public Integer getTotalBookedForMorning() {
            return totalBookedForMorning;
        }

        public Integer getTotalBookedForAfternoon() {
            return totalBookedForAfternoon;
        }

        public Date getSessionStartTime() {
            return sessionStartTime;
        }

        public Date getSessionEndTime() {
            return sessionEndTime;
        }

        public boolean isOverbookingAllowed() {
            return isOverbookingAllowed;
        }

        public boolean isDraft() {
            return isDraft;
        }

        public String getMinHearingTime() {
            return minHearingTime;
        }

        public String getMaxHearingTime() {
            return maxHearingTime;
        }

        public String getJurisdiction() {
            return jurisdiction;
        }

        public CourtScheduleBuilder withCourtSchedule(final CourtSchedule courtSchedule) {
            this.courtScheduleId = courtSchedule.courtScheduleId;
            this.sessionDate = courtSchedule.sessionDate;
            this.ouCode = courtSchedule.ouCode;
            this.courtHouseName = courtSchedule.courtHouseName;
            this.courtHouseId = courtSchedule.courtHouseId;
            this.courtRoomId = courtSchedule.courtRoomId;
            this.courtRoomNumber = courtSchedule.courtRoomNumber;
            this.courtRoomName = courtSchedule.courtRoomName;
            this.businessType = courtSchedule.businessType;
            this.courtSession = courtSchedule.courtSession;
            this.slotBased = courtSchedule.slotBased;
            this.maxSlots = courtSchedule.maxSlots;
            this.maxDuration = courtSchedule.maxDuration;
            this.listingProfileId = courtSchedule.listingProfileId;
            this.operationalUnit = courtSchedule.operationalUnit;
            this.panel = courtSchedule.panel;
            this.availableDuration = courtSchedule.availableDuration;
            this.availableSlots = courtSchedule.availableSlots;
            this.judiciaries = courtSchedule.judiciaries;
            this.slotStartTimes = courtSchedule.slotStartTimes;
            this.active = courtSchedule.active;
            this.createdOn = courtSchedule.createdOn;
            this.updatedOn = courtSchedule.updatedOn;
            this.allDaySplit = courtSchedule.allDaySplit;
            this.maxDurationForMorning = courtSchedule.maxDurationForMorning;
            this.maxDurationForAfternoon = courtSchedule.maxDurationForAfternoon;
            this.totalBooked = courtSchedule.totalBooked;
            this.sessionStartTime = courtSchedule.sessionStartTime;
            this.sessionEndTime = courtSchedule.sessionEndTime;
            this.totalBookedForMorning = courtSchedule.totalBookedForMorning;
            this.totalBookedForAfternoon = courtSchedule.totalBookedForAfternoon;
            this.availableDurationForMorning = courtSchedule.availableDurationForMorning;
            this.availableDurationForAfternoon = courtSchedule.availableDurationForAfternoon;
            this.isOverbookingAllowed = courtSchedule.isOverbookingAllowed;
            this.nationalBreakTime = courtSchedule.nationalBreakTime;
            this.isDraft = courtSchedule.isDraft;
            this.minHearingTime = courtSchedule.minHearingTime;
            this.maxHearingTime = courtSchedule.maxHearingTime;
            this.jurisdiction = courtSchedule.jurisdiction;
            return this;
        }

        public CourtScheduleBuilder withCourtScheduleId(final String courtScheduleId) {
            this.courtScheduleId = courtScheduleId;
            return this;
        }

        public CourtScheduleBuilder withPanel(final String panel) {
            this.panel = panel;
            return this;
        }

        public CourtScheduleBuilder withListingProfileId(final String listingProfileId) {
            this.listingProfileId = listingProfileId;
            return this;
        }

        public CourtScheduleBuilder withOuCode(final String ouCode) {
            this.ouCode = ouCode;
            return this;
        }

        public CourtScheduleBuilder withCourtHouseName(final String courtHouseName) {
            this.courtHouseName = courtHouseName;
            return this;
        }

        public CourtScheduleBuilder withCourtHouseId(final String courtHouseId) {
            this.courtHouseId = courtHouseId;
            return this;
        }

        public CourtScheduleBuilder withCourtRoomId(final String courtRoomId) {
            this.courtRoomId = courtRoomId;
            return this;
        }

        public CourtScheduleBuilder withCourtRoomNumber(final Integer courtRoomNumber) {
            this.courtRoomNumber = courtRoomNumber;
            return this;
        }

        public CourtScheduleBuilder withCourtRoomName(final String courtRoomName) {
            this.courtRoomName = courtRoomName;
            return this;
        }

        public CourtScheduleBuilder withOperationalUnit(final String operationalUnit) {
            this.operationalUnit = operationalUnit;
            return this;
        }

        public CourtScheduleBuilder withBusinessType(final String businessType) {
            this.businessType = businessType;
            return this;
        }

        public CourtScheduleBuilder withBusinessDescription(final String businessDescription) {
            this.businessDescription = businessDescription;
            return this;
        }

        public CourtScheduleBuilder withCourtSession(final String courtSession) {
            this.courtSession = courtSession;
            return this;
        }

        public CourtScheduleBuilder withSlotBased(final boolean slotBased) {
            this.slotBased = slotBased;
            return this;
        }

        public CourtScheduleBuilder withActive(final boolean active) {
            this.active = active;
            return this;
        }

        public CourtScheduleBuilder withSessionDate(final LocalDate sessionDate) {
            this.sessionDate = sessionDate;
            return this;
        }

        public CourtScheduleBuilder withAvailableSlots(final Integer availableSlot) {
            this.availableSlots = availableSlot;
            return this;
        }

        public CourtScheduleBuilder withAvailableDuration(final Integer availableDuration) {
            this.availableDuration = availableDuration;
            return this;
        }

        public CourtScheduleBuilder withMaxSlots(final Integer maxSlot) {
            this.maxSlots = maxSlot;
            return this;
        }

        public CourtScheduleBuilder withMaxDuration(final Integer maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        public CourtScheduleBuilder withJudiciaries(final List<CourtScheduleJudiciary> judiciaries) {
            this.judiciaries = judiciaries;
            return this;
        }

        public CourtScheduleBuilder addJudiciary(final CourtScheduleJudiciary courtScheduleJudiciary) {
            this.judiciaries.add(courtScheduleJudiciary);
            return this;
        }

        public CourtScheduleBuilder withSlotStartTimes(final List<SlotStartTime> slotStartTimes) {
            this.slotStartTimes = slotStartTimes;
            return this;
        }

        public CourtScheduleBuilder addSlotStartTime(final SlotStartTime slotStartTime) {
            this.slotStartTimes.add(slotStartTime);
            return this;
        }

        public CourtScheduleBuilder withCreatedOn(final Date createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public CourtScheduleBuilder withUpdatedOn(final Date updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public CourtScheduleBuilder withAllDaySplit(final boolean allDaySplit) {
            this.allDaySplit = allDaySplit;
            return this;
        }

        public CourtScheduleBuilder withMaxDurationForMorning(final Integer maxDurationForMorning) {
            this.maxDurationForMorning = maxDurationForMorning;
            return this;
        }

        public CourtScheduleBuilder withMaxDurationForAfternoon(final Integer maxDurationForAfternoon) {
            this.maxDurationForAfternoon = maxDurationForAfternoon;
            return this;
        }

        public CourtScheduleBuilder withTotalBookedForMorning(final Integer totalBookedForMorning) {
            this.totalBookedForMorning = totalBookedForMorning;
            return this;
        }

        public CourtScheduleBuilder withTotalBookedForAfternoon(final Integer totalBookedForAfternoon) {
            this.totalBookedForAfternoon = totalBookedForAfternoon;
            return this;
        }

        public CourtScheduleBuilder withAvailableDurationForMorning(final Integer availableDurationForMorning) {
            this.availableDurationForMorning = availableDurationForMorning;
            return this;
        }

        public CourtScheduleBuilder withAvailableDurationForAfternoon(final Integer availableDurationForAfternoon) {
            this.availableDurationForAfternoon = availableDurationForAfternoon;
            return this;
        }

        public CourtScheduleBuilder withTotalBooked(final Integer totalBooked) {
            this.totalBooked = totalBooked;
            return this;
        }

        public CourtScheduleBuilder withSessionStartTime(final Date sessionStartTime) {
            this.sessionStartTime = sessionStartTime;
            return this;
        }

        public CourtScheduleBuilder withSessionEndTime(final Date sessionEndTime) {
            this.sessionEndTime = sessionEndTime;
            return this;
        }

        public CourtScheduleBuilder withIsOverbookingAllowed(final boolean isOverbookingAllowed) {
            this.isOverbookingAllowed = isOverbookingAllowed;
            return this;
        }

        public CourtScheduleBuilder withNationalBreakTime(final Date nationalBreakTime) {
            this.nationalBreakTime = nationalBreakTime;
            return this;
        }

        public CourtScheduleBuilder withIsDraft(final boolean isDraft) {
            this.isDraft = isDraft;
            return this;
        }

        public CourtScheduleBuilder withMinHearingTime(final String minHearingTime) {
            this.minHearingTime = minHearingTime;
            return this;
        }

        public CourtScheduleBuilder withMaxHearingTime(final String maxHearingTime) {
            this.maxHearingTime = maxHearingTime;
            return this;
        }

        public CourtScheduleBuilder withJurisdiction(final String jurisdiction) {
            this.jurisdiction = jurisdiction;
            return this;
        }

        public CourtSchedule build() {
            return new CourtSchedule(this);
        }
    }
}
