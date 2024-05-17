package uk.gov.moj.cpp.courtscheduler.domain;

import java.sql.Timestamp;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class OrganisationUnitHMIStatus {

    private String oucode;
    private boolean isHMIListingEnabled;
    private boolean isHMISchedulingEnabled;
    private boolean isHMIPubHubEnabled;
    private Timestamp updatedOn;
    private String courtCentreId;
    private String courtId;

    public OrganisationUnitHMIStatus() {
    }

    public OrganisationUnitHMIStatus(final Builder builder) {
        this.oucode = builder.oucode;
        this.isHMIListingEnabled = builder.isHMIListingEnabled;
        this.isHMISchedulingEnabled = builder.isHMISchedulingEnabled;
        this.isHMIPubHubEnabled = builder.isHMIPubHubEnabled;
        this.updatedOn = builder.updatedOn;
        this.courtCentreId = builder.courtCentreId;
        this.courtId = builder.courtId;
    }

    public String getOucode() {
        return oucode;
    }

    public void setOucode(final String oucode) {
        this.oucode = oucode;
    }

    public boolean getIsHMIListingEnabled() {
        return isHMIListingEnabled;
    }

    public void setIsHMIListingEnabled(final boolean HMIListingEnabled) {
        isHMIListingEnabled = HMIListingEnabled;
    }

    public boolean getIsHMISchedulingEnabled() {
        return isHMISchedulingEnabled;
    }

    public void setIsHMISchedulingEnabled(final boolean HMISchedulingEnabled) {
        isHMISchedulingEnabled = HMISchedulingEnabled;
    }

    public boolean getIsHMIPubHubEnabled() {
        return isHMIPubHubEnabled;
    }

    public void setIsHMIPubHubEnabled(final boolean HMIPubHubEnabled) {
        isHMIPubHubEnabled = HMIPubHubEnabled;
    }

    public Timestamp getUpdatedOn() {
        return updatedOn;
    }

    public void setUpdatedOn(final Timestamp updatedOn) {
        this.updatedOn = updatedOn;
    }

    public String getCourtCentreId() {
        return courtCentreId;
    }

    public void setCourtCentreId(final String courtCentreId) {
        this.courtCentreId = courtCentreId;
    }

    public String getCourtId() {
        return courtId;
    }

    public void setCourtId(final String courtId) {
        this.courtId = courtId;
    }

    public static Builder organisationUnitHMIStatus() {
        return new OrganisationUnitHMIStatus.Builder();
    }

    public static class Builder {
        private String oucode;
        private boolean isHMIListingEnabled;
        private boolean isHMISchedulingEnabled;
        private boolean isHMIPubHubEnabled;
        private Timestamp updatedOn;
        private String courtCentreId;
        private String courtId;

        public Builder withOucode(final String oucode) {
            this.oucode = oucode;
            return this;
        }

        public Builder withIsHMIListingEnabled(final boolean isHMIListingEnabled) {
            this.isHMIListingEnabled = isHMIListingEnabled;
            return this;
        }

        public Builder withIsHMISchedulingEnabled(final boolean isHMISchedulingEnabled) {
            this.isHMISchedulingEnabled = isHMISchedulingEnabled;
            return this;
        }

        public Builder withIsHMIPubHubEnabled(final boolean isHMIPubHubEnabled) {
            this.isHMIPubHubEnabled = isHMIPubHubEnabled;
            return this;
        }

        public Builder withUpdatedOn(final Timestamp updatedOn) {
            this.updatedOn = updatedOn;
            return this;
        }

        public Builder withCourtCentreId(final String courtCentreId) {
            this.courtCentreId = courtCentreId;
            return this;
        }

        public Builder withCourtId(final String courtId) {
            this.courtId = courtId;
            return this;
        }

        public OrganisationUnitHMIStatus build() {
            return new OrganisationUnitHMIStatus(this);
        }
    }
}
