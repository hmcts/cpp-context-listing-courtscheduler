package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class CourtScheduleDeleteResponse {

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
    private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
    private List<SlotStartTime> slotStartTimes = new ArrayList<>();
    private Date createdOn;
    private Date updatedOn;
    private String sessionStartTime;
    private String sessionEndTime;
    @JsonProperty("isOverbookingAllowed")
    private boolean overbookingAllowed;
    private String minHearingTime;
    private String maxHearingTime;

    protected CourtScheduleDeleteResponse(final CourtScheduleDeleteResponseBuilder builder) {
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
        this.createdOn = builder.createdOn;
        this.updatedOn = builder.updatedOn;
        this.totalBooked = builder.totalBooked;
        this.sessionStartTime = builder.sessionStartTime;
        this.sessionEndTime = builder.sessionEndTime;
        this.overbookingAllowed = builder.overbookingAllowed;
        this.minHearingTime = builder.minHearingTime;
        this.maxHearingTime = builder.maxHearingTime;
    }

    public CourtScheduleDeleteResponse() {
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


    public Date getCreatedOn() {
        return createdOn;
    }

    public Date getUpdatedOn() {
        return updatedOn;
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

    public void setCreatedOn(final Date createdOn) {
        this.createdOn = createdOn;
    }

    public void setUpdatedOn(final Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Integer getTotalBooked() {
        return totalBooked;
    }

    public void setTotalBooked(final Integer totalBooked) {
        this.totalBooked = totalBooked;
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

    public void setOverbookingAllowed(final boolean overbookingAllowed) {
        this.overbookingAllowed = overbookingAllowed;
    }

    public String getMinHearingTime() {
        return minHearingTime;
    }

    public void setMinHearingTime(final String minHearingTime) {
        this.minHearingTime = minHearingTime;
    }

    public static final class CourtScheduleDeleteResponseBuilder {

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
        private Integer totalBooked = 0;
        private String courtSession;
        private boolean slotBased;
        private boolean active;
        private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
        private List<SlotStartTime> slotStartTimes = new ArrayList<>();
        private Date createdOn;
        private Date updatedOn;
        private String sessionStartTime;
        private String sessionEndTime;
        @JsonProperty("isOverbookingAllowed")
        private boolean overbookingAllowed;
        private String minHearingTime;
        private String maxHearingTime;

        public static CourtScheduleDeleteResponseBuilder courtSchedule() {
            return new CourtScheduleDeleteResponseBuilder();
        }

        public CourtScheduleDeleteResponseBuilder withCourtScheduleId(final String courtScheduleId) {
            this.courtScheduleId = courtScheduleId;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withPanel(final String panel) {
            this.panel = panel;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withListingProfileId(final String listingProfileId) {
            this.listingProfileId = listingProfileId;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withOuCode(final String ouCode) {
            this.ouCode = ouCode;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withCourtRoomId(final String courtRoomId) {
            this.courtRoomId = courtRoomId;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withCourtRoomName(final String courtRoomName) {
            this.courtRoomName = courtRoomName;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withCourtHouseName(final String courtHouseName) {
            this.courtHouseName = courtHouseName;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withCourtHouseId(final String courtHouseId) {
            this.courtHouseId = courtHouseId;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withCourtRoomNumber(final Integer courtRoomNumber) {
            this.courtRoomNumber = courtRoomNumber;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withOperationalUnit(final String operationalUnit) {
            this.operationalUnit = operationalUnit;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withBusinessType(final String businessType) {
            this.businessType = businessType;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withBusinessDescription(final String businessDescription) {
            this.businessDescription = businessDescription;
            return this;
        }


        public CourtScheduleDeleteResponseBuilder withCourtSession(final String courtSession) {
            this.courtSession = courtSession;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withSlotBased(final boolean slotBased) {
            this.slotBased = slotBased;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withActive(final boolean active) {
            this.active = active;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withSessionDate(final LocalDate sessionDate) {
            this.sessionDate = sessionDate;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withAvailableSlots(final Integer availableSlot) {
            this.availableSlots = availableSlot;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withAvailableDuration(final Integer availableDuration) {
            this.availableDuration = availableDuration;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withMaxSlots(final Integer maxSlot) {
            this.maxSlots = maxSlot;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withMaxDuration(final Integer maxDuration) {
            this.maxDuration = maxDuration;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withJudiciaries(final List<CourtScheduleJudiciary> judiciaries) {
            this.judiciaries = judiciaries;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder addJudiciary(final CourtScheduleJudiciary courtScheduleJudiciary) {
            this.judiciaries.add(courtScheduleJudiciary);
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withSlotStartTimes(final List<SlotStartTime> slotStartTimes) {
            this.slotStartTimes = slotStartTimes;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder addSlotStartTime(final SlotStartTime slotStartTime) {
            this.slotStartTimes.add(slotStartTime);
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withCreatedOn(final Date createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withUpdatedOn(final Date updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withTotalBooked(final Integer totalBooked){
            this.totalBooked = totalBooked;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withSessionStartTime(final String sessionStartTime) {
            this.sessionStartTime = sessionStartTime;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withSessionEndTime(final String sessionEndTime) {
            this.sessionEndTime = sessionEndTime;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withIsOverbookingAllowed(final boolean overbookingAllowed) {
            this.overbookingAllowed = overbookingAllowed;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withMinHearingTime(final String minHearingTime) {
            this.minHearingTime = minHearingTime;
            return this;
        }

        public CourtScheduleDeleteResponseBuilder withMaxHearingTime(final String maxHearingTime) {
            this.maxHearingTime = maxHearingTime;
            return this;
        }

        public CourtScheduleDeleteResponse build() {
            return new CourtScheduleDeleteResponse(this);
        }
    }
}
