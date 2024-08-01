package uk.gov.moj.cpp.courtscheduler.domain.mi;

import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
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
    private String court_listing_profile_id;
    private String oucode;
    private String court_room_id;
    private Integer court_room_number;
    private String court_house_id;// same as courtCentreId
    private String court_house_name;
    private String court_room_name;
    private String operational_unit;
    private String rota_business_type;
    private String panel;
    private String court_session;
    private Boolean is_slot_based;
    private Boolean active;
    private Date session_start;
    private Integer max_slot;
    private Integer available_slot;
    private Integer max_duration_mins;
    private Integer available_duration_mins;
    private Date created_on;
    private Date updated_on;

    protected CourtSchedule(final CourtScheduleBuilder builder) {
        this.id = builder.courtScheduleId;
        this.court_listing_profile_id = builder.listingProfileId;
        this.oucode = builder.ouCode;
        this.court_room_id = builder.courtRoomId;
        this.court_room_number = builder.courtRoomNumber;
        this.court_house_name = builder.courtHouseName;
        this.court_house_id = builder.courtHouseId;
        this.court_room_name = builder.courtRoomName;
        this.operational_unit = builder.operationalUnit;
        this.rota_business_type = builder.businessType;
        this.panel = builder.panel;
        this.court_session = builder.courtSession;
        this.session_start = builder.sessionDate;
        this.max_slot = builder.maxSlots;
        this.max_duration_mins = builder.maxDuration;
        this.available_slot = builder.availableSlots;
        this.available_duration_mins = builder.availableDuration;
        this.is_slot_based = builder.slotBased;
        this.active = builder.active;
        this.created_on = builder.createdOn;
        this.updated_on = builder.updatedOn;
    }

    public CourtSchedule() {
    }

    @JsonProperty("operational_unit")
    public String getOperationalUnit() {
        return operational_unit;
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
        return court_listing_profile_id;
    }

    @JsonProperty("session_start")
    public String getSessionDate() {
        return DateUtils.toIsoString(session_start);
    }

    @JsonProperty("oucode")
    public String getOuCode() {
        return oucode;
    }

    @JsonProperty("court_house_name")
    public String getCourtHouseName() {
        return court_house_name;
    }

    @JsonProperty("court_house_id")
    public String getCourtHouseId() {
        return court_house_id;
    }

    @JsonProperty("court_room_id")
    public String getCourtRoomId() {
        return court_room_id;
    }

    @JsonProperty("court_room_number")
    public Integer getCourtRoomNumber() {
        return court_room_number;
    }

    @JsonProperty("court_room_name")
    public String getCourtRoomName() {
        return court_room_name;
    }

    @JsonProperty("rota_business_type")
    public String getBusinessType() {
        return rota_business_type;
    }

    @JsonProperty("court_session")
    public String getCourtSession() {
        return court_session;
    }

    @JsonProperty("available_slot")
    public Integer getAvailableSlots() {
        return available_slot;
    }

    @JsonProperty("available_duration_mins")
    public Integer getAvailableDuration() {
        return available_duration_mins;
    }

    @JsonProperty("max_slot")
    public Integer getMaxSlots() {
        return max_slot;
    }

    @JsonProperty("max_duration_mins")
    public Integer getMaxDuration() {
        return max_duration_mins;
    }

    @JsonProperty("is_slot_based")
    public Boolean isSlotBased() {
        return is_slot_based;
    }

    @JsonProperty("active")
    public Boolean isActive() {
        return active;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.id = courtScheduleId;
    }

    public void setListingProfileId(final String listingProfileId) {
        this.court_listing_profile_id = listingProfileId;
    }

    public void setOuCode(final String ouCode) {
        this.oucode = ouCode;
    }

    public void setCourtRoomId(final String courtRoomId) {
        this.court_room_id = courtRoomId;
    }

    public void setCourtRoomNumber(final Integer courtRoomNumber) {
        this.court_room_number = courtRoomNumber;
    }

    public void setCourtHouseId(final String courtHouseId) {
        this.court_house_id = courtHouseId;
    }

    public void setCourtHouseName(final String courtHouseName) {
        this.court_house_name = courtHouseName;
    }

    public void setCourtRoomName(final String courtRoomName) {
        this.court_room_name = courtRoomName;
    }

    public void setOperationalUnit(final String operationalUnit) {
        this.operational_unit = operationalUnit;
    }

    public void setBusinessType(final String businessType) {
        this.rota_business_type = businessType;
    }

    public void setPanel(final String panel) {
        this.panel = panel;
    }

    public void setCourtSession(final String courtSession) {
        this.court_session = courtSession;
    }

    public void setSlotBased(final Boolean slotBased) {
        this.is_slot_based = slotBased;
    }

    public void setSessionDate(final Date sessionDate) {
        this.session_start = sessionDate;
    }

    public void setMaxSlots(final Integer maxSlots) {
        this.max_slot = maxSlots;
    }

    public void setMaxDuration(final Integer maxDuration) {
        this.max_duration_mins = maxDuration;
    }

    public void setAvailableSlots(final Integer availableSlots) {
        this.available_slot = availableSlots;
    }

    public void setAvailableDuration(final Integer availableDuration) {
        this.available_duration_mins = availableDuration;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @JsonProperty("created_on")
    public String getCreatedOn() {
        return created_on == null ? null : DateUtils.toIsoString(created_on);
    }

    public void setCreatedOn(Date createdOn) {
        this.created_on = createdOn;
    }

    @JsonProperty("updated_on")
    public String getUpdatedOn() {
        return updated_on == null ? null : DateUtils.toIsoString(updated_on);
    }

    public void setUpdatedOn(Date updatedOn) {
        this.updated_on = updatedOn;
    }

    public Boolean hasHearingsBooked() {
        return (is_slot_based) ?
                max_slot.compareTo(available_slot) != 0 :
                max_duration_mins.compareTo(available_duration_mins) != 0;

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
        private Date sessionDate;
        private Integer maxSlots = 0;
        private Integer maxDuration = 0;
        private Integer availableSlots = 0;
        private Integer availableDuration = 0;
        private String courtSession;
        private Boolean slotBased;
        private Boolean active;
        private Date createdOn;
        private Date updatedOn;

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

        public Date getSessionDate() {
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
            this.sessionDate = courtSchedule.session_start;
            this.ouCode = courtSchedule.oucode;
            this.courtHouseName = courtSchedule.court_house_name;
            this.courtHouseId = courtSchedule.court_house_id;
            this.courtRoomId = courtSchedule.court_room_id;
            this.courtRoomNumber = courtSchedule.court_room_number;
            this.courtRoomName = courtSchedule.court_room_name;
            this.businessType = courtSchedule.rota_business_type;
            this.courtSession = courtSchedule.court_session;
            this.slotBased = courtSchedule.is_slot_based;
            this.maxSlots = courtSchedule.max_slot;
            this.maxDuration = courtSchedule.max_duration_mins;
            this.listingProfileId = courtSchedule.court_listing_profile_id;
            this.operationalUnit = courtSchedule.operational_unit;
            this.panel = courtSchedule.panel;
            this.availableDuration = courtSchedule.available_duration_mins;
            this.availableSlots = courtSchedule.available_slot;
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

        public CourtScheduleBuilder withSessionDate(final Date sessionDate) {
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


        public CourtScheduleBuilder withCreatedOn(final Date createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public CourtScheduleBuilder withUpdatedOn(final Date updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }


        public CourtSchedule build() {
            return new CourtSchedule(this);
        }
    }
}
