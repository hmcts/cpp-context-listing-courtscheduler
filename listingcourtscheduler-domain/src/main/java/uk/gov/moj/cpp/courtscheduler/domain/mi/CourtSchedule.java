package uk.gov.moj.cpp.courtscheduler.domain.mi;

import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@SuppressWarnings("squid:S2384")
@JsonInclude
@JsonPropertyOrder({
        "id",
        "court_listing_profile_id",
        "oucode",
        "court_room_number",
        "court_house_name",
        "court_room_name",
        "operational_unit",
        "rota_business_type",
        "court_session",
        "session_start",
        "panel",
        "max_slot",
        "max_duration_mins",
        "available_slot",
        "available_duration_mins",
        "active",
        "created_on",
        "updated_on",
        "court_room_id",
        "is_slot_based",
        "court_house_id"
})
public class CourtSchedule {

    private String id;
    private String courtListingProfileId;
    private String oucode;
    private String courtRoomId;
    private Integer courtRoomNumber;
    private String courtHouseId;// same as courtCentreId
    private String courtHouseName;
    private String courtRoomName;
    private String operationalUnit;
    private String rotaBusinessType;
    private String panel;
    private String courtSession;
    private Boolean slotBased;
    private Boolean active;
    private LocalDate sessionDate;
    private Integer maxSlot;
    private Integer availableSlot;
    private Integer maxDurationMins;
    private Integer availableDurationMins;
    private Instant createdOn;
    private Instant updatedOn;

    protected CourtSchedule(final CourtScheduleBuilder builder) {
        this.id = builder.courtScheduleId;
        this.courtListingProfileId = builder.listingProfileId;
        this.oucode = builder.ouCode;
        this.courtRoomId = builder.courtRoomId;
        this.courtRoomNumber = builder.courtRoomNumber;
        this.courtHouseName = builder.courtHouseName;
        this.courtHouseId = builder.courtHouseId;
        this.courtRoomName = builder.courtRoomName;
        this.operationalUnit = builder.operationalUnit;
        this.rotaBusinessType = builder.businessType;
        this.panel = builder.panel;
        this.courtSession = builder.courtSession;
        this.sessionDate = builder.sessionDate;
        this.maxSlot = builder.maxSlots;
        this.maxDurationMins = builder.maxDuration;
        this.availableSlot = builder.availableSlots;
        this.availableDurationMins = builder.availableDuration;
        this.slotBased = builder.slotBased;
        this.active = builder.active;
        this.createdOn = builder.createdOn;
        this.updatedOn = builder.updatedOn;
    }

    public CourtSchedule() {
    }

    @JsonProperty("operational_unit")
    public String getOperationalUnit() {
        return operationalUnit;
    }

    @JsonProperty("panel")
    public String getPanel() {
        return panel;
    }

    @JsonProperty("id")
    public String getCourtScheduleId() {
        return id;
    }

    @JsonProperty("court_listing_profile_id")
    public String getListingProfileId() {
        return courtListingProfileId;
    }

    @JsonProperty("session_start")
    public String getSessionDate() {
        return sessionDate == null ? null : sessionDate + "T00:00Z";
    }

    @JsonProperty("oucode")
    public String getOuCode() {
        return oucode;
    }

    @JsonProperty("court_house_name")
    public String getCourtHouseName() {
        return courtHouseName;
    }

    @JsonProperty("court_house_id")
    public String getCourtHouseId() {
        return courtHouseId;
    }

    @JsonProperty("court_room_id")
    public String getCourtRoomId() {
        return courtRoomId;
    }

    @JsonProperty("court_room_number")
    public Integer getCourtRoomNumber() {
        return courtRoomNumber;
    }

    @JsonProperty("court_room_name")
    public String getCourtRoomName() {
        return courtRoomName;
    }

    @JsonProperty("rota_business_type")
    public String getBusinessType() {
        return rotaBusinessType;
    }

    @JsonProperty("court_session")
    public String getCourtSession() {
        return courtSession;
    }

    @JsonProperty("available_slot")
    public Integer getAvailableSlots() {
        return availableSlot;
    }

    @JsonProperty("available_duration_mins")
    public Integer getAvailableDuration() {
        return availableDurationMins;
    }

    @JsonProperty("max_slot")
    public Integer getMaxSlots() {
        return maxSlot;
    }

    @JsonProperty("max_duration_mins")
    public Integer getMaxDuration() {
        return maxDurationMins;
    }

    @JsonProperty("is_slot_based")
    public Boolean isSlotBased() {
        return slotBased;
    }

    @JsonProperty("active")
    public Boolean isActive() {
        return active;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.id = courtScheduleId;
    }

    public void setListingProfileId(final String listingProfileId) {
        this.courtListingProfileId = listingProfileId;
    }

    public void setOuCode(final String ouCode) {
        this.oucode = ouCode;
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
        this.rotaBusinessType = businessType;
    }

    public void setPanel(final String panel) {
        this.panel = panel;
    }

    public void setCourtSession(final String courtSession) {
        this.courtSession = courtSession;
    }

    public void setSlotBased(final Boolean slotBased) {
        this.slotBased = slotBased;
    }

    public void setSessionDate(final LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public void setMaxSlots(final Integer maxSlots) {
        this.maxSlot = maxSlots;
    }

    public void setMaxDuration(final Integer maxDuration) {
        this.maxDurationMins = maxDuration;
    }

    public void setAvailableSlots(final Integer availableSlots) {
        this.availableSlot = availableSlots;
    }

    public void setAvailableDuration(final Integer availableDuration) {
        this.availableDurationMins = availableDuration;
    }

    public void setActive(final Boolean active) {
        this.active = active;
    }

    @JsonProperty("created_on")
    public String getCreatedOn() {
        return createdOn == null ? null : DateUtils.toIsoStringMinutes(createdOn);
    }

    public void setCreatedOn(final Instant createdOn) {
        this.createdOn = createdOn;
    }

    @JsonProperty("updated_on")
    public String getUpdatedOn() {
        return updatedOn == null ? null : DateUtils.toIsoStringMinutes(updatedOn);
    }

    public void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Boolean hasHearingsBooked() {
        return slotBased ?
                maxSlot.compareTo(availableSlot) != 0 :
                maxDurationMins.compareTo(availableDurationMins) != 0;

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
        private Boolean slotBased;
        private Boolean active;
        private Instant createdOn;
        private Instant updatedOn;

        public static CourtScheduleBuilder courtSchedule() {
            return new CourtScheduleBuilder();
        }

        public Boolean isActive() {
            return active;
        }

        public Boolean isSlotBased() {
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

        public CourtScheduleBuilder withCourtSchedule(final CourtSchedule courtSchedule) {
            this.courtScheduleId = courtSchedule.id;
            this.sessionDate = courtSchedule.sessionDate;
            this.ouCode = courtSchedule.oucode;
            this.courtHouseName = courtSchedule.courtHouseName;
            this.courtHouseId = courtSchedule.courtHouseId;
            this.courtRoomId = courtSchedule.courtRoomId;
            this.courtRoomNumber = courtSchedule.courtRoomNumber;
            this.courtRoomName = courtSchedule.courtRoomName;
            this.businessType = courtSchedule.rotaBusinessType;
            this.courtSession = courtSchedule.courtSession;
            this.slotBased = courtSchedule.slotBased;
            this.maxSlots = courtSchedule.maxSlot;
            this.maxDuration = courtSchedule.maxDurationMins;
            this.listingProfileId = courtSchedule.courtListingProfileId;
            this.operationalUnit = courtSchedule.operationalUnit;
            this.panel = courtSchedule.panel;
            this.availableDuration = courtSchedule.availableDurationMins;
            this.availableSlots = courtSchedule.availableSlot;
            this.active = courtSchedule.active;
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

        public CourtScheduleBuilder withSlotBased(final Boolean slotBased) {
            this.slotBased = slotBased;
            return this;
        }

        public CourtScheduleBuilder withActive(final Boolean active) {
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


        public CourtScheduleBuilder withCreatedOn(final Instant createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public CourtScheduleBuilder withUpdatedOn(final Instant updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }


        public CourtSchedule build() {
            return new CourtSchedule(this);
        }
    }
}
