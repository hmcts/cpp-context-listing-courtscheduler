package uk.gov.moj.cpp.courtscheduler.repository;

import static org.junit.jupiter.api.Assertions.*;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(CdiTestRunner.class)
public class CourtMigrationRepositoryTest {

    @Inject
    private CourtMigrationRepository courtMigrationRepository;

    @Before
    public void setUp() {
        List<CourtSchedulerMigrationStatus> courtSchedulerMigrationStatusList = courtMigrationRepository.findAll();
        courtSchedulerMigrationStatusList.forEach(courtSchedulerMigrationStatus -> courtMigrationRepository.remove(courtSchedulerMigrationStatus));
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