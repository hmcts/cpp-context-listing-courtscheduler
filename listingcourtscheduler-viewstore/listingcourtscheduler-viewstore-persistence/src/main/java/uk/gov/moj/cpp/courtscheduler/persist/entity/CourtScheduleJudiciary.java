package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "court_schedule_judiciary")
public class CourtScheduleJudiciary {
    @Id
    private CourtScheduleJudiciaryKey id;
    @Column(name = "court_listing_profile_id")
    private String courtListingProfileId;
    @Column(name = "rota_judiciary_id")
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
    private Boolean benchChairman;
    @Column(name = "is_deputy", nullable = false)
    private Boolean deputy;
    @Column(name = "position")
    private String position;
    @Column(name = "active", nullable = false)
    private Boolean active = true;
    @CreationTimestamp
    @Column(name = "created_on", nullable = false)
    private Instant createdOn;

    @UpdateTimestamp
    @Column(name = "updated_on", nullable = false)
    private Instant updatedOn;


    public CourtScheduleJudiciaryKey getId() {
        return id;
    }

    public void setId(final CourtScheduleJudiciaryKey id) {
        this.id = id;
    }

    public String getCourtListingProfileId() {
        return courtListingProfileId;
    }

    public void setCourtListingProfileId(final String courtListingProfileId) {
        this.courtListingProfileId = courtListingProfileId;
    }

    public String getRotaJudiciaryId() {
        return rotaJudiciaryId;
    }

    public void setRotaJudiciaryId(final String rotaJudiciaryId) {
        this.rotaJudiciaryId = rotaJudiciaryId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getForenames() {
        return forenames;
    }

    public void setForenames(final String forenames) {
        this.forenames = forenames;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(final String surname) {
        this.surname = surname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getJudiciaryType() {
        return judiciaryType;
    }

    public void setJudiciaryType(final String judiciaryType) {
        this.judiciaryType = judiciaryType;
    }

    public Boolean isBenchChairman() {
        return benchChairman;
    }

    public void setBenchChairman(final Boolean benchChairman) {
        this.benchChairman = benchChairman;
    }

    public Boolean isDeputy() {
        return deputy;
    }

    public void setDeputy(final Boolean deputy) {
        this.deputy = deputy;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(final String position) {
        this.position = position;
    }

    public Boolean isActive() {
        return active;
    }

    public void setActive(final Boolean active) {
        this.active = active;
    }

    public Instant getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(final Instant createdOn) {
        this.createdOn = createdOn;
    }

    public Instant getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(final Instant updatedOn) {
        this.updatedOn = updatedOn;
    }

    @Override
    public boolean equals(final Object o) {
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
                ", isBenchChairman=" + benchChairman +
                ", isDeputy=" + deputy +
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
        private Instant createdOn;
        private Instant updatedOn;

        private CourtScheduleJudiciaryBuilder() {
        }

        public static CourtScheduleJudiciaryBuilder courtScheduleJudiciary() {
            return new CourtScheduleJudiciaryBuilder();
        }

        public CourtScheduleJudiciaryBuilder withId(final CourtScheduleJudiciaryKey id) {
            this.id = id;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withCourtListingProfileId(final String courtListingProfileId) {
            this.courtListingProfileId = courtListingProfileId;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withRotaJudiciaryId(final String rotaJudiciaryId) {
            this.rotaJudiciaryId = rotaJudiciaryId;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withTitle(final String title) {
            this.title = title;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withForenames(final String forenames) {
            this.forenames = forenames;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withSurname(final String surname) {
            this.surname = surname;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withEmail(final String email) {
            this.email = email;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withJudiciaryType(final String judiciaryType) {
            this.judiciaryType = judiciaryType;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withIsBenchChairman(final Boolean isBenchChairman) {
            this.isBenchChairman = isBenchChairman;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withIsDeputy(final Boolean isDeputy) {
            this.isDeputy = isDeputy;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withPosition(final String position) {
            this.position = position;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withActive(final Boolean active) {
            this.active = active;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withCreatedOn(final Instant createdOn) {
            this.createdOn = createdOn;
            return this;
        }

        public CourtScheduleJudiciaryBuilder withUpdatedOn(final Instant updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public CourtScheduleJudiciary build() {
            final CourtScheduleJudiciary courtScheduleJudiciary = new CourtScheduleJudiciary();
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
            courtScheduleJudiciary.deputy = this.isDeputy;
            courtScheduleJudiciary.benchChairman = this.isBenchChairman;
            return courtScheduleJudiciary;
        }
    }
}

