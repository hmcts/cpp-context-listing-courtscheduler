package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    private boolean hasHearingsBooked;
    private LocalDate sessionDate;
    private Integer maxSlots;
    private Integer maxDuration;
    private Integer availableSlots;
    private Integer availableDuration;
    private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
    private List<SlotStartTime> slotStartTimes = new ArrayList<>();

    private String createdOn;
    private String updatedOn;

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
        this.hasHearingsBooked = builder.hasHearingsBooked;
        this.createdOn = builder.createdOn;
        this.updatedOn = builder.updatedOn;
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

    public boolean isHasHearingsBooked() {
        return hasHearingsBooked;
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

    public void setHasHearingsBooked(boolean hasHearingsBooked) {
        this.hasHearingsBooked = hasHearingsBooked;
    }

    public String getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(String createdOn) {
        this.createdOn = createdOn;
    }

    public String getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(String updatedOn) {
        this.updatedOn = updatedOn;
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
        private boolean hasHearingsBooked;
        private List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
        private List<SlotStartTime> slotStartTimes = new ArrayList<>();

        private String createdOn;
        private String updatedOn;

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

        public CourtScheduleViewBuilder withHasHearingsBooked(final boolean hasHearingsBooked) {
            this.hasHearingsBooked = hasHearingsBooked;
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

        public CourtScheduleViewBuilder withCreatedOn(final String createdOn) {
            this.createdOn = createdOn;
            return this;
        }
        public CourtScheduleViewBuilder withUpdatedOn(final String updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }


        public CourtScheduleView build() {
            return new CourtScheduleView(this);
        }
    }
}
