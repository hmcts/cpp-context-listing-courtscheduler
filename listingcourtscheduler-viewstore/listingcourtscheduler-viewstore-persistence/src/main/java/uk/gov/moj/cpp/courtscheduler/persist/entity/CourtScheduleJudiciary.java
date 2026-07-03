package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "court_schedule_judiciary")
public class CourtScheduleJudiciary {
    @Id
    private CourtScheduleJudiciaryKey id;
    @Column(name = "court_listing_profile_id", nullable = false)
    private String courtListingProfileId;
    @Column(name = "rota_judiciary_id", nullable = false)
    private String rotaJudiciaryId;
    @Column(name = "title", nullable = false)
    private String title;
    @Column(name = "forenames", nullable = false)
    private String forenames;
    @Column(name = "surname", nullable = false)
    private String surname;
    @Column(name = "email", nullable = false)
    private String email;
    @Column(name = "judiciary_type", nullable = false)
    private String judiciaryType;
    @Column(name = "is_bench_chairman", nullable = false)
    private Boolean isBenchChairman;
    @Column(name = "is_deputy", nullable = false)
    private Boolean isDeputy;
    @Column(name = "position", nullable = false)
    private String position;
    @Column(name = "active", nullable = false)
    private Boolean active = true;
    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_on", nullable = false)
    private java.util.Date createdOn;

    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_on", nullable = false)
    private java.util.Date updatedOn;

    public CourtScheduleJudiciary() {
        //For JPA
    }

    public CourtScheduleJudiciaryKey getId() {
        return id;
    }

    public void setId(CourtScheduleJudiciaryKey id) {
        this.id = id;
    }

    public String getCourtListingProfileId() {
        return courtListingProfileId;
    }

    public void setCourtListingProfileId(String courtListingProfileId) {
        this.courtListingProfileId = courtListingProfileId;
    }

    public String getRotaJudiciaryId() {
        return rotaJudiciaryId;
    }

    public void setRotaJudiciaryId(String rotaJudiciaryId) {
        this.rotaJudiciaryId = rotaJudiciaryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getForenames() {
        return forenames;
    }

    public void setForenames(String forenames) {
        this.forenames = forenames;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getJudiciaryType() {
        return judiciaryType;
    }

    public void setJudiciaryType(String judiciaryType) {
        this.judiciaryType = judiciaryType;
    }

    public Boolean getBenchChairman() {
        return isBenchChairman;
    }

    public void setBenchChairman(Boolean benchChairman) {
        isBenchChairman = benchChairman;
    }

    public Boolean getDeputy() {
        return isDeputy;
    }

    public void setDeputy(Boolean deputy) {
        isDeputy = deputy;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public java.util.Date getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(java.util.Date createdOn) {
        this.createdOn = createdOn;
    }

    public java.util.Date getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(java.util.Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final CourtScheduleJudiciary that = (CourtScheduleJudiciary) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CourtScheduleJudiciary{" +
                "id=" + id +
                ", courtListingProfileId='" + courtListingProfileId + '\'' +
                ", rotaJudiciaryId='" + rotaJudiciaryId + '\'' +
                ", title='" + title + '\'' +
                ", forenames='" + forenames + '\'' +
                ", surname='" + surname + '\'' +
                ", email='" + email + '\'' +
                ", judiciaryType='" + judiciaryType + '\'' +
                ", isBenchChairman=" + isBenchChairman +
                ", isDeputy=" + isDeputy +
                ", position='" + position + '\'' +
                ", active=" + active +
                ", createdOn=" + createdOn +
                ", updatedOn=" + updatedOn +
                '}';
    }

    public static final class CourtScheduleJudiciaryBuilder {
        private CourtScheduleJudiciaryKey id;
        private String courtListingProfileId;
        private String rotaJudiciaryId;
        private String title;
        private String forenames;
        private String surname;
        private String email;
        private String judiciaryType;
        private Boolean isBenchChairman;
        private Boolean isDeputy;
        private String position;
        private Boolean active;
        private Date createdOn;
        private Date updatedOn;

        private CourtScheduleJudiciaryBuilder() {
        }

        public static CourtScheduleJudiciaryBuilder courtScheduleJudiciary() {
            return new CourtScheduleJudiciaryBuilder();
        }

        public CourtScheduleJudiciaryBuilder withId(CourtScheduleJudiciaryKey id) {
            this.id = id;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withCourtListingProfileId(String courtListingProfileId) {
            this.courtListingProfileId = courtListingProfileId;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withRotaJudiciaryId(String rotaJudiciaryId) {
            this.rotaJudiciaryId = rotaJudiciaryId;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withTitle(String title) {
            this.title = title;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withForenames(String forenames) {
            this.forenames = forenames;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withSurname(String surname) {
            this.surname = surname;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withEmail(String email) {
            this.email = email;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withJudiciaryType(String judiciaryType) {
            this.judiciaryType = judiciaryType;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withIsBenchChairman(Boolean isBenchChairman) {
            this.isBenchChairman = isBenchChairman;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withIsDeputy(Boolean isDeputy) {
            this.isDeputy = isDeputy;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withPosition(String position) {
            this.position = position;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withActive(Boolean active) {
            this.active = active;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withCreatedOn(Date createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withUpdatedOn(Date updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public CourtScheduleJudiciary build() {
            CourtScheduleJudiciary courtScheduleJudiciary = new CourtScheduleJudiciary();
            courtScheduleJudiciary.setId(id);
            courtScheduleJudiciary.setCourtListingProfileId(courtListingProfileId);
            courtScheduleJudiciary.setRotaJudiciaryId(rotaJudiciaryId);
            courtScheduleJudiciary.setTitle(title);
            courtScheduleJudiciary.setForenames(forenames);
            courtScheduleJudiciary.setSurname(surname);
            courtScheduleJudiciary.setEmail(email);
            courtScheduleJudiciary.setJudiciaryType(judiciaryType);
            courtScheduleJudiciary.setPosition(position);
            courtScheduleJudiciary.setActive(active);
            courtScheduleJudiciary.setCreatedOn(createdOn);
            courtScheduleJudiciary.setUpdatedOn(updatedOn);
            courtScheduleJudiciary.isDeputy = this.isDeputy;
            courtScheduleJudiciary.isBenchChairman = this.isBenchChairman;
            return courtScheduleJudiciary;
        }
    }
}

