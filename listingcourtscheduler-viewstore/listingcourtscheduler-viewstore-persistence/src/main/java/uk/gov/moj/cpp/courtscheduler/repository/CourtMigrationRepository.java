package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CourtMigrationRepository extends JpaRepository<CourtSchedulerMigrationStatus, String> {

     CourtSchedulerMigrationStatus findByOuCode(String ouCode);
     CourtSchedulerMigrationStatus findByCourtCentreId(String courtCentreId);
}
