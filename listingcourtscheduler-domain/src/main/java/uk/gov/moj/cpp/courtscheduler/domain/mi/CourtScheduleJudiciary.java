package uk.gov.moj.cpp.courtscheduler.domain.mi;

import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@SuppressWarnings({"pmd:BeanMembersShouldSerialize", "squid:S00121", "squid:S00122", "squid:S1067"})
@JsonInclude
@JsonPropertyOrder({
        "court_schedule_id",
        "court_listing_profile_id",
        "judiciary_id",
        "rota_judiciary_id",
        "title",
        "forenames",
        "surname",
        "email",
        "judiciary_type",
        "is_bench_chairman",
        "is_deputy",
        "position",
        "active",
        "created_on",
        "updated_on"})
public class CourtScheduleJudiciary {

    private String judiciary_id;

    private String rota_judiciary_id;

    private String title;

    private String forenames;

    private String surname;

    private String email;

    private String court_schedule_id;

    private String court_listing_profile_id;

    private String judiciary_type;

    private String position;

    private Boolean is_bench_chairman;

    private Boolean is_deputy;

    private Boolean active;
    private Date created_on;
    private Date updated_on;


    @SuppressWarnings("squid:S1186")
    public CourtScheduleJudiciary() {

    }

    public CourtScheduleJudiciary(final Builder builder) {
        this.rota_judiciary_id = builder.rotaJudiciaryId;
        this.forenames = builder.forenames;
        this.is_bench_chairman = builder.isBenchChairman;
        this.is_deputy = builder.isDeputy;
        this.judiciary_id = builder.judiciaryId;
        this.judiciary_type = builder.judiciaryType;
        this.surname = builder.surname;
        this.title = builder.title;
        this.email = builder.emailAddress;
        this.court_schedule_id = builder.courtScheduleId;
        this.court_listing_profile_id = builder.courtListingProfileId;
        this.position = builder.position;
        this.created_on = builder.createdOn;
        this.updated_on = builder.updatedOn;
        this.active = builder.active;
    }

    @JsonProperty("judiciary_id")
    public String getJudiciaryId() {
        return judiciary_id;
    }

    @JsonProperty("rota_judiciary_id")
    public String getRotaJudiciaryId() {
        return rota_judiciary_id;
    }

    @JsonProperty("title")
    public String getTitle() {
        return title;
    }

    @JsonProperty("forenames")
    public String getForenames() {
        return forenames;
    }

    @JsonProperty("surname")
    public String getSurname() {
        return surname;
    }

    @JsonProperty("email")
    public String getEmailAddress() {
        return email;
    }

    @JsonProperty("court_schedule_id")
    public String getCourtScheduleId() {
        return court_schedule_id;
    }

    @JsonProperty("court_listing_profile_id")
    public String getCourtListingProfileId() {
        return court_listing_profile_id;
    }

    @JsonProperty("judiciary_type")
    public String getJudiciaryType() {
        return judiciary_type;
    }

    @JsonProperty("position")
    public String getPosition() {
        return position;
    }

    @JsonProperty("is_bench_chairman")
    public Boolean getBenchChairman() {
        return is_bench_chairman;
    }

    @JsonProperty("is_deputy")
    public Boolean getDeputy() {
        return is_deputy;
    }

    @JsonProperty("active")
    public Boolean isActive() {
        return active;
    }

    public void setJudiciaryId(final String judiciaryId) {
        this.judiciary_id = judiciaryId;
    }

    public void setRotaJudiciaryId(final String rotaJudiciaryId) {
        this.rota_judiciary_id = rotaJudiciaryId;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setForenames(final String forenames) {
        this.forenames = forenames;
    }

    public void setSurname(final String surname) {
        this.surname = surname;
    }

    public void setEmailAddress(final String emailAddress) {
        this.email = emailAddress;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.court_schedule_id = courtScheduleId;
    }

    public void setCourtListingProfileId(final String courtListingProfileId) {
        this.court_listing_profile_id = courtListingProfileId;
    }

    public void setJudiciaryType(final String judiciaryType) {
        this.judiciary_type = judiciaryType;
    }

    public void setPosition(final String position) {
        this.position = position;
    }

    public void setBenchChairman(final Boolean benchChairman) {
        is_bench_chairman = benchChairman;
    }

    public void setDeputy(final Boolean deputy) {
        is_deputy = deputy;
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

    public static Builder judiciary() {
        return new Builder();
    }

    public static class Builder {

        private String judiciaryId;

        private String rotaJudiciaryId;

        private String title;

        private String forenames;

        private String surname;

        private String emailAddress;

        private String courtScheduleId;

        private String courtListingProfileId;

        private String judiciaryType;

        private String position;

        private Boolean isBenchChairman;

        private Boolean isDeputy;

        private Boolean active = false;

        private Date createdOn;
        private Date updatedOn;

        public Builder withActive(final Boolean active) {
            this.active = active;
            return this;
        }

        public Builder withJudiciaryId(final String judiciaryId) {
            this.judiciaryId = judiciaryId;
            return this;
        }

        public Builder withRotaJudiciaryId(final String rotaJudiciaryId) {
            this.rotaJudiciaryId = rotaJudiciaryId;
            return this;
        }

        public Builder withTitle(final String title) {
            this.title = title;
            return this;
        }

        public Builder withForenames(final String forenames) {
            this.forenames = forenames;
            return this;
        }

        public Builder withSurname(final String surname) {
            this.surname = surname;
            return this;
        }

        public Builder withEmailAddress(final String emailAddress) {
            this.emailAddress = emailAddress;
            return this;
        }

        public Builder withJudiciaryType(final String judiciaryType) {
            this.judiciaryType = judiciaryType;
            return this;
        }

        public Builder withCourtScheduleId(final String courtScheduleId) {
            this.courtScheduleId = courtScheduleId;
            return this;
        }

        public Builder withCourtListingProfileId(final String courtListingProfileId) {
            this.courtListingProfileId = courtListingProfileId;
            return this;
        }

        public Builder withPosition(final String position) {
            this.position = position;
            return this;
        }

        public Builder withIsBenchChairman(final Boolean isBenchChairman) {
            this.isBenchChairman = isBenchChairman;
            return this;
        }

        public Builder withIsDeputy(final Boolean isDeputy) {
            this.isDeputy = isDeputy;
            return this;
        }


        public Builder withCreatedOn(final Date createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public Builder withUpdatedOn(final Date updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public CourtScheduleJudiciary build() {
            return new CourtScheduleJudiciary(this);
        }
    }
}
