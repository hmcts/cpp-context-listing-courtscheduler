package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.sql.Timestamp;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
@Entity
@Table(name = "organisation_unit_hmi_status")
public class OrganisationUnitHMIStatus {

    @Id
    @Column(name = "oucode", nullable = false)
    private String oucode;
    @Column(name = "is_hmi_listing_enabled", nullable = false)
    private boolean hmiListingEnabled;
    @Column(name = "is_hmi_scheduling_enabled", nullable = false)
    private boolean hmiSchedulingEnabled;
    @Column(name = "is_hmi_pubhub_enabled", nullable = false)
    private boolean hmiPubHubEnabled;
    @Column(name = "updated_on", nullable = false)
    private Timestamp updatedOn;
    @Column(name = "court_centre_id", nullable = false)
    private String courtCentreId;
    @Column(name = "court_id", nullable = false)
    private String courtId;

    public OrganisationUnitHMIStatus() {
        //For JPA
    }

    public String getOucode() {
        return oucode;
    }

    public void setOucode(String oucode) {
        this.oucode = oucode;
    }

    public boolean isHmiListingEnabled() {
        return hmiListingEnabled;
    }

    public void setHmiListingEnabled(boolean hmiListingEnabled) {
        this.hmiListingEnabled = hmiListingEnabled;
    }

    public boolean isHmiSchedulingEnabled() {
        return hmiSchedulingEnabled;
    }

    public void setHmiSchedulingEnabled(boolean hmiSchedulingEnabled) {
        this.hmiSchedulingEnabled = hmiSchedulingEnabled;
    }

    public boolean isHmiPubHubEnabled() {
        return hmiPubHubEnabled;
    }

    public void setHmiPubHubEnabled(boolean hmiPubHubEnabled) {
        this.hmiPubHubEnabled = hmiPubHubEnabled;
    }

    public Timestamp getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(Timestamp updatedOn) {
        this.updatedOn = updatedOn;
    }

    public String getCourtCentreId() {
        return courtCentreId;
    }

    public void setCourtCentreId(String courtCentreId) {
        this.courtCentreId = courtCentreId;
    }

    public String getCourtId() {
        return courtId;
    }

    public void setCourtId(String courtId) {
        this.courtId = courtId;
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final OrganisationUnitHMIStatus that = (OrganisationUnitHMIStatus) o;
        return hmiListingEnabled == that.hmiListingEnabled
                && hmiSchedulingEnabled == that.hmiSchedulingEnabled
                && hmiPubHubEnabled == that.hmiPubHubEnabled && Objects.equals(oucode, that.oucode)
                && Objects.equals(updatedOn, that.updatedOn) && Objects.equals(courtCentreId, that.courtCentreId)
                && Objects.equals(courtId, that.courtId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oucode, hmiListingEnabled, hmiSchedulingEnabled, hmiPubHubEnabled, updatedOn, courtCentreId, courtId);
    }

    @Override
    public String toString() {
        return "OrganisationUnitHMIStatus{" +
                "oucode='" + oucode + '\'' +
                ", isHMIListingEnabled=" + hmiListingEnabled +
                ", isHMISchedulingEnabled=" + hmiSchedulingEnabled +
                ", isHMIPubHubEnabled=" + hmiPubHubEnabled +
                ", updatedOn=" + updatedOn +
                ", courtCentreId='" + courtCentreId + '\'' +
                ", courtId='" + courtId + '\'' +
                '}';
    }
}
