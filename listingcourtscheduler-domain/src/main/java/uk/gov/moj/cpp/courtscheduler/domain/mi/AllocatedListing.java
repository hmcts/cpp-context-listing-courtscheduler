package uk.gov.moj.cpp.courtscheduler.domain.mi;

import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.util.Date;

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

    private String court_schedule_id;

    private String booking_id;


    private String hearing_id;

    private String oucode;

    private Integer court_room_id;

    private String rota_business_type;

    private Integer duration;

    private Date hearing_start_time;

    private Date updated_on;

    private Date created_on;

    private Boolean is_overbooking_exempt;

    public AllocatedListing() {
        //For JPA
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("court_schedule_id")
    public String getCourtScheduleId() {
        return court_schedule_id;
    }

    public void setCourtScheduleId(String courtScheduleId) {
        this.court_schedule_id = courtScheduleId;
    }

    @JsonProperty("booking_id")
    public String getBookingId() {
        return booking_id;
    }

    public void setBookingId(String bookingId) {
        this.booking_id = bookingId;
    }

    @JsonProperty("hearing_id")
    public String getHearingId() {
        return hearing_id;
    }

    public void setHearingId(String hearingId) {
        this.hearing_id = hearingId;
    }

    @JsonProperty("oucode")
    public String getOucode() {
        return oucode;
    }

    public void setOucode(String oucode) {
        this.oucode = oucode;
    }

    @JsonProperty("court_room_id")
    public Integer getCourtRoomId() {
        return court_room_id;
    }

    public void setCourtRoomId(Integer courtRoomId) {
        this.court_room_id = courtRoomId;
    }

    @JsonProperty("rota_business_type")
    public String getRotaBusinessType() {
        return rota_business_type;
    }

    public void setRotaBusinessType(String rotaBusinessType) {
        this.rota_business_type = rotaBusinessType;
    }

    @JsonProperty("duration")
    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
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

    @JsonProperty("hearing_start_time")
    public String getHearingStartTime() {
        return hearing_start_time == null ? null : DateUtils.toIsoString(hearing_start_time);
    }

    public void setHearingStartTime(Date hearingStartTime) {
        this.hearing_start_time = hearingStartTime;
    }

    @JsonProperty("is_overbooking_exempt")
    public Boolean getIs_overbooking_exempt() {
        return is_overbooking_exempt;
    }

    public void setIs_overbooking_exempt(Boolean is_overbooking_exempt) {
        this.is_overbooking_exempt = is_overbooking_exempt;
    }
}
