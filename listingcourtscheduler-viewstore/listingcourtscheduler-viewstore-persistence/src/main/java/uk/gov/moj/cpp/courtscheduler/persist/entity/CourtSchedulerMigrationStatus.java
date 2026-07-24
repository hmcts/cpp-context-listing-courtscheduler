package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "courtscheduler_migration_status")
public class CourtSchedulerMigrationStatus {
    @Id
    @Column(name = "oucode", nullable = false)
    private String ouCode;
    @Column(name = "court_centre_id", nullable = false)
    private String courtCentreId;
    @Column(name = "migrated", nullable = false)
    private boolean migrated;
    @UpdateTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_on", nullable = false)
    private java.util.Date updatedOn;

    public CourtSchedulerMigrationStatus() {
        //For JPA
    }

    public String getOuCode() {
        return ouCode;
    }

    public CourtSchedulerMigrationStatus setOuCode(final String ouCode) {
        this.ouCode = ouCode;
        return this;
    }

    public String getCourtCentreId() {
        return courtCentreId;
    }

    public CourtSchedulerMigrationStatus setCourtCentreId(final String courtCentreId) {
        this.courtCentreId = courtCentreId;
        return this;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public CourtSchedulerMigrationStatus setMigrated(final boolean migrated) {
        this.migrated = migrated;
        return this;
    }

    public Date getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(final Date updatedOn) {
        this.updatedOn = updatedOn;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final CourtSchedulerMigrationStatus that)) return false;
        return isMigrated() == that.isMigrated() && Objects.equals(getOuCode(), that.getOuCode()) && Objects.equals(getCourtCentreId(), that.getCourtCentreId()) && Objects.equals(getUpdatedOn(), that.getUpdatedOn());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getOuCode(), getCourtCentreId(), isMigrated(), getUpdatedOn());
    }

    @Override
    public String toString() {
        return "CourtSchedulerMigrationStatus{" +
                "ouCode='" + ouCode + '\'' +
                ", courtCentreId='" + courtCentreId + '\'' +
                ", migrated=" + migrated +
                ", updatedOn='" + updatedOn + '\'' +
                '}';
    }
}
