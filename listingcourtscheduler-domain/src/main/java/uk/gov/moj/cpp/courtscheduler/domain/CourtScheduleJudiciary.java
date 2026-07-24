package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    private Integer seqId;
    private String titleJudicialPrefix;
    private String titleJudicialPrefixWelsh;
    private String personId;
    private List<JudiciarySpecialismType> specialisms;
    private String requestedName;


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
        this.seqId = builder.seqId;
        this.titleJudicialPrefix = builder.titleJudicialPrefix;
        this.titleJudicialPrefixWelsh = builder.titleJudicialPrefixWelsh;
        this.personId = builder.personId;
        this.specialisms = builder.specialisms;
        this.requestedName = builder.requestedName;
    }

    @JsonProperty("id")
    public String getJudiciaryId() {
        return judiciaryId;
    }

    public String getRotaJudiciaryId() {
        return rotaJudiciaryId;
    }

    @JsonProperty("titlePrefix")
    public String getTitle() {
        return title;
    }

    public String getForenames() {
        return forenames;
    }

    public String getSurname() {
        return surname;
    }

    @JsonProperty("emailAddress")
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

    @JsonProperty("isBenchChairman")
    public Boolean getBenchChairman() {
        return isBenchChairman;
    }

    @JsonProperty("isDeputy")
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

    @JsonIgnore
    public Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(Date createdOn) {
        this.createdOn = createdOn;
    }

    @JsonIgnore
    public Date getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    public Integer getSeqId() {
        return seqId;
    }

    public void setSeqId(final Integer seqId) {
        this.seqId = seqId;
    }

    public String getTitleJudicialPrefix() {
        return titleJudicialPrefix;
    }

    public void setTitleJudicialPrefix(final String titleJudicialPrefix) {
        this.titleJudicialPrefix = titleJudicialPrefix;
    }

    public String getTitleJudicialPrefixWelsh() {
        return titleJudicialPrefixWelsh;
    }

    public void setTitleJudicialPrefixWelsh(final String titleJudicialPrefixWelsh) {
        this.titleJudicialPrefixWelsh = titleJudicialPrefixWelsh;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(final String personId) {
        this.personId = personId;
    }

    public List<JudiciarySpecialismType> getSpecialisms() {
        return specialisms;
    }

    public void setSpecialisms(final List<JudiciarySpecialismType> specialisms) {
        this.specialisms = specialisms;
    }

    public String getRequestedName() {
        return requestedName;
    }

    public void setRequestedName(final String requestedName) {
        this.requestedName = requestedName;
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

        private Integer seqId;
        private String titleJudicialPrefix;
        private String titleJudicialPrefixWelsh;
        private String personId;
        private List<JudiciarySpecialismType> specialisms;
        private String requestedName;

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

        public Builder withSeqId(final Integer seqId) {
            this.seqId = seqId;
            return this;
        }

        public Builder withTitleJudicialPrefix(final String titleJudicialPrefix) {
            this.titleJudicialPrefix = titleJudicialPrefix;
            return this;
        }

        public Builder withTitleJudicialPrefixWelsh(final String titleJudicialPrefixWelsh) {
            this.titleJudicialPrefixWelsh = titleJudicialPrefixWelsh;
            return this;
        }

        public Builder withPersonId(final String personId) {
            this.personId = personId;
            return this;
        }

        public Builder withSpecialisms(final List<JudiciarySpecialismType> specialisms) {
            this.specialisms = specialisms;
            return this;
        }

        public Builder withRequestedName(final String requestedName) {
            this.requestedName = requestedName;
            return this;
        }

        public CourtScheduleJudiciary build() {
            return new CourtScheduleJudiciary(this);
        }
    }
}
