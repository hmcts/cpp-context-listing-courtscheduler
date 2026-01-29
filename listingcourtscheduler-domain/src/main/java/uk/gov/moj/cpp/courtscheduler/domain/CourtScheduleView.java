package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class CourtScheduleView {

    private String courtScheduleId;
    private String listingProfileId;
    private String ouCode;
    private Integer courtRoomNumber;
    private String courtHouseId;// same as courtCentreId
    private String courtHouseName;
    private String operationalUnit;
    private String businessType;
    private String businessDescription;
    private String courtRoomId;
    private String courtRoomName;
    private String panel;
    private String courtSession;
    private boolean slotBased;
    private boolean active;
    private LocalDate sessionDate;
    private Integer maxSlots;
    private Integer maxDuration;
    private Integer availableSlots;
    private Integer availableDuration;
    private Integer totalBooked;
    private boolean allDaySplit;
    private Integer maxDurationForMorning;
    private Integer maxDurationForAfternoon;
    private String minHearingTime;
    private String maxHearingTime;
    private Integer totalBookedForMorning;
    private Integer totalBookedForAfternoon;
    private Integer availableDurationForMorning;
    private Integer availableDurationForAfternoon;
    private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
    private List<SlotStartTime> slotStartTimes = new ArrayList<>();
    private String sessionStartTime;
    private String sessionEndTime;
    @JsonProperty("isOverbookingAllowed")
    private boolean overbookingAllowed;
    private Boolean isDraft;
    private String jurisdiction;

    protected CourtScheduleView(final CourtScheduleViewBuilder builder) {
        this.courtScheduleId = builder.courtScheduleId;
        this.listingProfileId = builder.listingProfileId;
        this.courtRoomId = builder.courtRoomId;
        this.courtRoomName = builder.courtRoomName;
        this.ouCode = builder.ouCode;
        this.courtRoomNumber = builder.courtRoomNumber;
        this.courtHouseName = builder.courtHouseName;
        this.courtHouseId = builder.courtHouseId;
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
        this.totalBooked = builder.totalBooked;
        this.allDaySplit = builder.allDaySplit;
        this.maxDurationForMorning = builder.maxDurationForMorning;
        this.maxDurationForAfternoon = builder.maxDurationForAfternoon;
        this.totalBookedForMorning = builder.totalBookedForMorning;
        this.totalBookedForAfternoon = builder.totalBookedForAfternoon;
        this.availableDurationForMorning = builder.availableDurationForMorning;
        this.availableDurationForAfternoon = builder.availableDurationForAfternoon;
        this.minHearingTime = builder.minHearingTime;
        this.maxHearingTime = builder.maxHearingTime;
        this.sessionStartTime = builder.sessionStartTime;
        this.sessionEndTime = builder.sessionEndTime;
        this.overbookingAllowed = builder.isOverbookingAllowed;
        this.isDraft = builder.isDraft;
        this.jurisdiction = builder.jurisdictionType;
    }

    public CourtScheduleView() {
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

    public Integer getCourtRoomNumber() {
        return courtRoomNumber;
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

    public void setCourtRoomNumber(final Integer courtRoomNumber) {
        this.courtRoomNumber = courtRoomNumber;
    }

    public void setCourtHouseId(final String courtHouseId) {
        this.courtHouseId = courtHouseId;
    }

    public void setCourtHouseName(final String courtHouseName) {
        this.courtHouseName = courtHouseName;
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

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(String courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public String getCourtRoomName() {
        return courtRoomName;
    }

    public void setCourtRoomName(String courtRoomName) {
        this.courtRoomName = courtRoomName;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getTotalBooked() {
        return totalBooked;
    }

    public void setTotalBooked(final Integer totalBooked) {
        this.totalBooked = totalBooked;
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

    public Integer getTotalBookedForMorning() {
        return totalBookedForMorning;
    }

    public Integer getTotalBookedForAfternoon() {
        return totalBookedForAfternoon;
    }

    public Integer getAvailableDurationForMorning() {
        return availableDurationForMorning;
    }

    public Integer getAvailableDurationForAfternoon() {
        return availableDurationForAfternoon;
    }

    public String getMinHearingTime() {
        return minHearingTime;
    }

    public String getMaxHearingTime() {
        return maxHearingTime;
    }

    public void setMinHearingTime(final String minHearingTime) {
        this.minHearingTime = minHearingTime;
    }

    public void setMaxHearingTime(final String maxHearingTime) {
        this.maxHearingTime = maxHearingTime;
    }

    public String getSessionStartTime() {
        return sessionStartTime;
    }

    public void setSessionStartTime(final String sessionStartTime) {
        this.sessionStartTime = sessionStartTime;
    }

    public String getSessionEndTime() {
        return sessionEndTime;
    }

    public void setSessionEndTime(final String sessionEndTime) {
        this.sessionEndTime = sessionEndTime;
    }

    public boolean isOverbookingAllowed() {
        return overbookingAllowed;
    }

    public void setOverbookingAllowed(final boolean isOverbookingAllowed) {
        this.overbookingAllowed = isOverbookingAllowed;
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

    public static final class CourtScheduleViewBuilder {

        private String courtScheduleId;
        private String ouCode;
        private String listingProfileId;

        private String courtRoomId;
        private String courtRoomName;
        private Integer courtRoomNumber;
        private String courtHouseName;
        private String courtHouseId;// same as courtCentreId
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
        private Integer totalBooked;
        private boolean allDaySplit;
        private Integer maxDurationForMorning;
        private Integer maxDurationForAfternoon;
        private Integer totalBookedForMorning;
        private Integer totalBookedForAfternoon;
        private Integer availableDurationForMorning;
        private Integer availableDurationForAfternoon;
        private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
        private List<SlotStartTime> slotStartTimes = new ArrayList<>();
        private String minHearingTime;
        private String maxHearingTime;
        private String sessionStartTime;
        private String sessionEndTime;
        private boolean isOverbookingAllowed;
        private Boolean isDraft;
        private String jurisdictionType;

        public static CourtScheduleViewBuilder courtSchedule() {
            return new CourtScheduleViewBuilder();
        }


        public CourtScheduleViewBuilder withCourtScheduleId(final String courtScheduleId) {
            this.courtScheduleId = courtScheduleId;
            return this;
        }

        public CourtScheduleViewBuilder withPanel(final String panel) {
            this.panel = panel;
            return this;
        }

        public CourtScheduleViewBuilder withListingProfileId(final String listingProfileId) {
            this.listingProfileId = listingProfileId;
            return this;
        }

        public CourtScheduleViewBuilder withOuCode(final String ouCode) {
            this.ouCode = ouCode;
            return this;
        }

        public CourtScheduleViewBuilder withCourtRoomId(final String courtRoomId) {
            this.courtRoomId = courtRoomId;
            return this;
        }

        public CourtScheduleViewBuilder withCourtRoomName(final String courtRoomName) {
            this.courtRoomName = courtRoomName;
            return this;
        }

        public CourtScheduleViewBuilder withCourtHouseName(final String courtHouseName) {
            this.courtHouseName = courtHouseName;
            return this;
        }

        public CourtScheduleViewBuilder withCourtHouseId(final String courtHouseId) {
            this.courtHouseId = courtHouseId;
            return this;
        }

        public CourtScheduleViewBuilder withCourtRoomNumber(final Integer courtRoomNumber) {
            this.courtRoomNumber = courtRoomNumber;
            return this;
        }

        public CourtScheduleViewBuilder withOperationalUnit(final String operationalUnit) {
            this.operationalUnit = operationalUnit;
            return this;
        }

        public CourtScheduleViewBuilder withBusinessType(final String businessType) {
            this.businessType = businessType;
            return this;
        }

        public CourtScheduleViewBuilder withBusinessDescription(final String businessDescription) {
            this.businessDescription = businessDescription;
            return this;
        }


        public CourtScheduleViewBuilder withCourtSession(final String courtSession) {
            this.courtSession = courtSession;
            return this;
        }

        public CourtScheduleViewBuilder withSlotBased(final boolean slotBased) {
            this.slotBased = slotBased;
            return this;
        }

        public CourtScheduleViewBuilder withActive(final boolean active) {
            this.active = active;
            return this;
        }

        public CourtScheduleViewBuilder withSessionDate(final LocalDate sessionDate) {
            this.sessionDate = sessionDate;
            return this;
        }

        public CourtScheduleViewBuilder withAvailableSlots(final Integer availableSlot) {
            this.availableSlots = availableSlot;
            return this;
        }

        public CourtScheduleViewBuilder withAvailableDuration(final Integer availableDuration) {
            this.availableDuration = availableDuration;
            return this;
        }

        public CourtScheduleViewBuilder withMaxSlots(final Integer maxSlot) {
            this.maxSlots = maxSlot;
            return this;
        }

        public CourtScheduleViewBuilder withMaxDuration(final Integer maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        public CourtScheduleViewBuilder withJudiciaries(final List<CourtScheduleJudiciary> judiciaries) {
            this.judiciaries = judiciaries;
            return this;
        }

        public CourtScheduleViewBuilder addJudiciary(final CourtScheduleJudiciary courtScheduleJudiciary) {
            this.judiciaries.add(courtScheduleJudiciary);
            return this;
        }

        public CourtScheduleViewBuilder withSlotStartTimes(final List<SlotStartTime> slotStartTimes) {
            this.slotStartTimes = slotStartTimes;
            return this;
        }

        public CourtScheduleViewBuilder addSlotStartTime(final SlotStartTime slotStartTime) {
            this.slotStartTimes.add(slotStartTime);
            return this;
        }

        public CourtScheduleViewBuilder withTotalBooked(final Integer totalBooked) {
            this.totalBooked = totalBooked;
            return this;
        }

        public CourtScheduleViewBuilder withAllDaySplit(final boolean allDaySplit) {
            this.allDaySplit = allDaySplit;
            return this;
        }

        public CourtScheduleViewBuilder withMaxDurationForMorning(final Integer maxDurationForMorning) {
            this.maxDurationForMorning = maxDurationForMorning;
            return this;
        }

        public CourtScheduleViewBuilder withMaxDurationForAfternoon(final Integer maxDurationForAfternoon) {
            this.maxDurationForAfternoon = maxDurationForAfternoon;
            return this;
        }

        public CourtScheduleViewBuilder withTotalBookedForMorning(final Integer totalBookedForMorning) {
            this.totalBookedForMorning = totalBookedForMorning;
            return this;
        }

        public CourtScheduleViewBuilder withTotalBookedForAfternoon(final Integer totalBookedForAfternoon) {
            this.totalBookedForAfternoon = totalBookedForAfternoon;
            return this;
        }

        public CourtScheduleViewBuilder withAvailableDurationForMorning(final Integer availableDurationForMorning) {
            this.availableDurationForMorning = availableDurationForMorning;
            return this;
        }

        public CourtScheduleViewBuilder withAvailableDurationForAfternoon(final Integer availableDurationForAfternoon) {
            this.availableDurationForAfternoon = availableDurationForAfternoon;
            return this;
        }

        public CourtScheduleViewBuilder withMinHearingTime(final String minHearingTime) {
            this.minHearingTime = minHearingTime;
            return this;
        }

        public CourtScheduleViewBuilder withMaxHearingTime(final String maxHearingTime) {
            this.maxHearingTime = maxHearingTime;
            return this;
        }

        public CourtScheduleViewBuilder withSessionStartTime(final String sessionStartTime) {
            this.sessionStartTime = sessionStartTime;
            return this;
        }

        public CourtScheduleViewBuilder withSessionEndTime(final String sessionEndTime) {
            this.sessionEndTime = sessionEndTime;
            return this;
        }

        public CourtScheduleViewBuilder withIsOverbookingAllowed(final boolean isOverbookingAllowed) {
            this.isOverbookingAllowed = isOverbookingAllowed;
            return this;
        }

        public CourtScheduleViewBuilder withIsDraft(final Boolean isDraft) {
            this.isDraft = isDraft;
            return this;
        }

        public CourtScheduleViewBuilder withJurisdictionType(final String jurisdictionType) {
            this.jurisdictionType = jurisdictionType;
            return this;
        }

        public CourtScheduleView build() {
            return new CourtScheduleView(this);
        }
    }
}
