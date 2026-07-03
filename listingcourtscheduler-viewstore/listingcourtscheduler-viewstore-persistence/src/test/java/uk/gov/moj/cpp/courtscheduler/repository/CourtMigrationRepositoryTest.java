package uk.gov.moj.cpp.courtscheduler.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class CourtMigrationRepositoryTest extends uk.gov.moj.cpp.courtscheduler.repository.AbstractRepositoryTest {

    @Autowired
    private CourtMigrationRepository courtMigrationRepository;

    @BeforeEach
    public void setUp() {
        List<CourtSchedulerMigrationStatus> courtSchedulerMigrationStatusList = courtMigrationRepository.findAll();
        courtSchedulerMigrationStatusList.forEach(courtSchedulerMigrationStatus -> courtMigrationRepository.delete(courtSchedulerMigrationStatus));
    }

    @Test
    public void shouldReturnMigrationEnabledByOuCode() {
        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("ouCode");
        courtSchedulerMigrationStatus.setCourtCentreId("courtCentreId");
        courtSchedulerMigrationStatus.setMigrated(true);
        courtMigrationRepository.save(courtSchedulerMigrationStatus);
        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus1 = courtMigrationRepository.findByOuCode("ouCode");
        assertTrue(courtSchedulerMigrationStatus1.isMigrated());
    }

    @Test
    public void shouldReturnMigrationEnabledByCourtCentreId() {
        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("ouCode");
        courtSchedulerMigrationStatus.setCourtCentreId("courtCentreId");
        courtSchedulerMigrationStatus.setMigrated(true);
        courtMigrationRepository.save(courtSchedulerMigrationStatus);
        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus1 = courtMigrationRepository.findByCourtCentreId("courtCentreId");
        assertTrue(courtSchedulerMigrationStatus1.isMigrated());
    }

    @Test
    public void shouldReturnMigrationDisabledByOuCode() {
        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("ouCode");
        courtSchedulerMigrationStatus.setCourtCentreId("courtCentreId");
        courtSchedulerMigrationStatus.setMigrated(false);
        courtMigrationRepository.save(courtSchedulerMigrationStatus);
        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus1 = courtMigrationRepository.findByOuCode("ouCode");
        assertFalse(courtSchedulerMigrationStatus1.isMigrated());
    }

    @Test
    public void shouldReturnMigrationDisabledByCourtCentreId() {
        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("ouCode");
        courtSchedulerMigrationStatus.setCourtCentreId("courtCentreId");
        courtSchedulerMigrationStatus.setMigrated(false);
        courtMigrationRepository.save(courtSchedulerMigrationStatus);
        CourtSchedulerMigrationStatus courtSchedulerMigrationStatus1 = courtMigrationRepository.findByCourtCentreId("courtCentreId");
        assertFalse(courtSchedulerMigrationStatus1.isMigrated());
    }

}
