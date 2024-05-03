package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.OrganisationUnitHMIStatus;

@Repository(forEntity = OrganisationUnitHMIStatus.class)
public interface OrganisationUnitHMIStatusRepository extends EntityRepository<OrganisationUnitHMIStatus, String> {
}
