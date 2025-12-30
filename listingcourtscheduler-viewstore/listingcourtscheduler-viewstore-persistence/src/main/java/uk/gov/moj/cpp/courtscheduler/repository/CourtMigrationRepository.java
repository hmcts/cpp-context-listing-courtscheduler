package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = CourtSchedulerMigrationStatus.class)
public interface CourtMigrationRepository extends EntityRepository<CourtSchedulerMigrationStatus, String> {

     CourtSchedulerMigrationStatus findByOuCode(final String ouCode);
     CourtSchedulerMigrationStatus findByCourtCentreId(final String courtCentreId);
}
