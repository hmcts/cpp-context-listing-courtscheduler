package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Date;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

@SuppressWarnings({"pmd:BeanMembersShouldSerialize","squid:S00121","squid:S00122","squid:S1067"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CourtScheduleJudiciary {

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

    private boolean active;
    private Date createdOn;
    private Date updatedOn;


    @SuppressWarnings("squid:S1186")
    public CourtScheduleJudiciary(){

    }

    public CourtScheduleJudiciary(final Builder builder) {
        this.rotaJudiciaryId = builder.rotaJudiciaryId;
        this.forenames = builder.forenames;
        this.isBenchChairman = builder.isBenchChairman;
        this.isDeputy = builder.isDeputy;
        this.judiciaryId = builder.judiciaryId;
        this.judiciaryType = builder.judiciaryType;
        this.surname = builder.surname;
        this.title = builder.title;
        this.emailAddress = builder.emailAddress;
        this.courtScheduleId = builder.courtScheduleId;
        this.courtListingProfileId = builder.courtListingProfileId;
        this.position = builder.position;
        this.createdOn = builder.createdOn;
        this.updatedOn = builder.updatedOn;
        this.active = builder.active;
    }

    public String getJudiciaryId() {
        return judiciaryId;
    }

    public String getRotaJudiciaryId() {
        return rotaJudiciaryId;
    }

    public String getTitle() {
        return title;
    }

    public String getForenames() {
        return forenames;
    }

    public String getSurname() {
        return surname;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getCourtScheduleId() { return courtScheduleId; }

    public String getCourtListingProfileId() { return courtListingProfileId; }

    public String getJudiciaryType() {
        return judiciaryType;
    }

    public String getPosition() {
        return position;
    }

    public Boolean getBenchChairman() {
        return isBenchChairman;
    }

    public Boolean getDeputy() {
        return isDeputy;
    }
    public boolean isActive() {
        return active;
    }

    public void setJudiciaryId(final String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    public void setRotaJudiciaryId(final String rotaJudiciaryId) {
        this.rotaJudiciaryId = rotaJudiciaryId;
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
        this.emailAddress = emailAddress;
    }

    public void setCourtScheduleId(final String courtScheduleId) {
        this.courtScheduleId = courtScheduleId;
    }

    public void setCourtListingProfileId(final String courtListingProfileId) {
        this.courtListingProfileId = courtListingProfileId;
    }

    public void setJudiciaryType(final String judiciaryType) {
        this.judiciaryType = judiciaryType;
    }

    public void setPosition(final String position) {
        this.position = position;
    }

    public void setBenchChairman(final Boolean benchChairman) {
        isBenchChairman = benchChairman;
    }

    public void setDeputy(final Boolean deputy) {
        isDeputy = deputy;
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
    public static Builder judiciary() {
        return new CourtScheduleJudiciary.Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof CourtScheduleJudiciary)) return false;
        final CourtScheduleJudiciary courtScheduleJudiciary = (CourtScheduleJudiciary) o;
        return Objects.equals(judiciaryId, courtScheduleJudiciary.judiciaryId) &&
                Objects.equals(rotaJudiciaryId, courtScheduleJudiciary.rotaJudiciaryId) &&
                Objects.equals(title, courtScheduleJudiciary.title) &&
                Objects.equals(forenames, courtScheduleJudiciary.forenames) &&
                Objects.equals(surname, courtScheduleJudiciary.surname) &&
                Objects.equals(emailAddress, courtScheduleJudiciary.emailAddress) &&
                Objects.equals(courtListingProfileId, courtScheduleJudiciary.courtListingProfileId) &&
                Objects.equals(judiciaryType, courtScheduleJudiciary.judiciaryType) &&
                Objects.equals(position, courtScheduleJudiciary.position) &&
                Objects.equals(isBenchChairman, courtScheduleJudiciary.isBenchChairman) &&
                Objects.equals(isDeputy, courtScheduleJudiciary.isDeputy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(judiciaryId, rotaJudiciaryId, title, forenames, surname, emailAddress, courtListingProfileId, judiciaryType, position, isBenchChairman, isDeputy);
    }

    @Override
    public String toString() {
        return "CourtScheduleJudiciary{" +
                "judiciaryId=" + judiciaryId +
                ", rotaJudiciaryId='" + rotaJudiciaryId + '\'' +
                ", title='" + title + '\'' +
                ", forenames='" + forenames + '\'' +
                ", surname='" + surname + '\'' +
                ", emailAddress='" + emailAddress + '\'' +
                ", courtListingProfileId='" + courtListingProfileId + '\'' +
                ", judiciaryType='" + judiciaryType + '\'' +
                ", position='" + position + '\'' +
                ", isBenchChairman=" + isBenchChairman +
                ", isDeputy=" + isDeputy +
                '}';
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

        public Builder withActive(final boolean active) {
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
