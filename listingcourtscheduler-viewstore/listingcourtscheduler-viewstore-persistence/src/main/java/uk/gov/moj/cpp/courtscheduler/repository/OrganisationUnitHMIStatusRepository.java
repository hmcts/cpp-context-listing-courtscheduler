package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.OrganisationUnitHMIStatus;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = OrganisationUnitHMIStatus.class)
public interface OrganisationUnitHMIStatusRepository extends EntityRepository<OrganisationUnitHMIStatus, String> {
}
