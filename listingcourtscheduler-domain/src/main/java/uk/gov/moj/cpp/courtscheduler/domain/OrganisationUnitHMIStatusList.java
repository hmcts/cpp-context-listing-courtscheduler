package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

@SuppressWarnings("squid:S2384")
public class OrganisationUnitHMIStatusList {

    private List<OrganisationUnitHMIStatus> organisationUnitHMIStatus;

    public OrganisationUnitHMIStatusList(final List<OrganisationUnitHMIStatus> organisationUnitHMIStatus) {
        this.organisationUnitHMIStatus = organisationUnitHMIStatus;
    }

    public List<OrganisationUnitHMIStatus> getOrganisationUnitHMIStatus() {
        return organisationUnitHMIStatus;
    }

    public void setOrganisationUnitHMIStatus(final List<OrganisationUnitHMIStatus> organisationUnitHMIStatus) {
        this.organisationUnitHMIStatus = organisationUnitHMIStatus;
    }
}
