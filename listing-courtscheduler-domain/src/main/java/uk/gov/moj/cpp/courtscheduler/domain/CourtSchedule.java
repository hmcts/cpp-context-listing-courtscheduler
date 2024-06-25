package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
    private Integer maxDuration;
    private Integer availableSlots;
    private Integer availableDuration;
    private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
    private List<SlotStartTime> slotStartTimes = new ArrayList<>();

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

        public CourtSchedule build() {
            return new CourtSchedule(this);
        }
    }
}
