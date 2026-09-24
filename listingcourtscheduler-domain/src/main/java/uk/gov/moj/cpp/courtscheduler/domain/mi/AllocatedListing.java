package uk.gov.moj.cpp.courtscheduler.domain.mi;

import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude
@JsonPropertyOrder({
        "id",
        "court_schedule_id",
        "booking_id",
        "hearing_id",
        "oucode",
        "court_room_id",
        "rota_business_type",
        "duration",
        "created_on",
        "updated_on",
        "hearing_start_time",
        "is_overbooking_exempt"
})

public class AllocatedListing {

    private String id;

    private String courtScheduleId;

    private String bookingId;


    private String hearingId;

    private String oucode;

    private Integer courtRoomId;

    private String rotaBusinessType;

    private Integer duration;

    private Instant hearingStartTime;

    private Instant updatedOn;

    private Instant createdOn;

    private Boolean overbookingExempt;

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }

    @JsonProperty("court_schedule_id")
    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    @JsonProperty("booking_id")
    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(final String bookingId) {
        this.bookingId = bookingId;
    }

    @JsonProperty("hearing_id")
    public String getHearingId() {
        return hearingId;
    }

    public void setHearingId(final String hearingId) {
        this.hearingId = hearingId;
    }

    @JsonProperty("oucode")
    public String getOucode() {
        return oucode;
    }

    public void setOucode(final String oucode) {
        this.oucode = oucode;
    }

    @JsonProperty("court_room_id")
    public Integer getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(final Integer courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    @JsonProperty("rota_business_type")
    public String getRotaBusinessType() {
        return rotaBusinessType;
    }

    public void setRotaBusinessType(final String rotaBusinessType) {
        this.rotaBusinessType = rotaBusinessType;
    }

    @JsonProperty("duration")
    public Integer getDuration() {
        return duration;
    }

    public void setDuration(final Integer duration) {
        this.duration = duration;
    }

    @JsonProperty("created_on")
    public String getCreatedOn() {
        return createdOn == null ? null : DateUtils.toIsoString(createdOn);
    }

    public void setCreatedOn(final Instant createdOn) {
        this.createdOn = createdOn;
    }

    @JsonProperty("updated_on")
    public String getUpdatedOn() {
        return updatedOn == null ? null : DateUtils.toIsoString(updatedOn);
    }

    public void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    @JsonProperty("hearing_start_time")
    public String getHearingStartTime() {
        return hearingStartTime == null ? null : DateUtils.toIsoString(hearingStartTime);
    }

    public void setHearingStartTime(final Instant hearingStartTime) {
        this.hearingStartTime = hearingStartTime;
    }

    @JsonProperty("is_overbooking_exempt")
    public Boolean isOverbookingExempt() {
        return overbookingExempt;
    }

    public void setOverbookingExempt(final Boolean overbookingExempt) {
        this.overbookingExempt = overbookingExempt;
    }
}
