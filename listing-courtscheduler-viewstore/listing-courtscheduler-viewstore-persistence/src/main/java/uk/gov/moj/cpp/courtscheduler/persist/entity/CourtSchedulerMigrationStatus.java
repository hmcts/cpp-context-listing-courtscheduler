package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Table;

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
    @Column(name = "updated_on", nullable = false)
    private String updatedOn;

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

    public String getUpdatedOn() {
        return updatedOn;
    }

    @PrePersist
    @PreUpdate
    public void prePersistOrUpdate() {
        this.updatedOn = LocalDateTime.now().toString();
    }

    public CourtSchedulerMigrationStatus setUpdatedOn(final String updatedOn) {
        this.updatedOn = updatedOn;
        return this;
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
