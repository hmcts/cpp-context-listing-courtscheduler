package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

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
}

