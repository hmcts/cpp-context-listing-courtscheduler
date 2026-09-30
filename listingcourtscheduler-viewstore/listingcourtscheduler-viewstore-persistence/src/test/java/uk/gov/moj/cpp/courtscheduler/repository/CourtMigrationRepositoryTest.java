package uk.gov.moj.cpp.courtscheduler.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class CourtMigrationRepositoryTest extends uk.gov.moj.cpp.courtscheduler.repository.AbstractRepositoryTest {
    private static final String COURT_CENTRE_ID = "courtCentreId";
    private static final String OU_CODE = "ouCode";


    @Autowired
    private CourtMigrationRepository courtMigrationRepository;

    @BeforeEach
    public void setUp() {
        final List<CourtSchedulerMigrationStatus> courtSchedulerMigrationStatusList = courtMigrationRepository.findAll();
        courtSchedulerMigrationStatusList.forEach(courtSchedulerMigrationStatus -> courtMigrationRepository.delete(courtSchedulerMigrationStatus));
    }

    @Test
    void shouldReturnMigrationEnabledByOuCode() {
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode(OU_CODE);
        courtSchedulerMigrationStatus.setCourtCentreId(COURT_CENTRE_ID);
        courtSchedulerMigrationStatus.setMigrated(true);
        courtMigrationRepository.save(courtSchedulerMigrationStatus);
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus1 = courtMigrationRepository.findByOuCode(OU_CODE);
        assertTrue(courtSchedulerMigrationStatus1.isMigrated());
    }

    @Test
    void shouldReturnMigrationEnabledByCourtCentreId() {
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode(OU_CODE);
        courtSchedulerMigrationStatus.setCourtCentreId(COURT_CENTRE_ID);
        courtSchedulerMigrationStatus.setMigrated(true);
        courtMigrationRepository.save(courtSchedulerMigrationStatus);
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus1 = courtMigrationRepository.findByCourtCentreId(COURT_CENTRE_ID);
        assertTrue(courtSchedulerMigrationStatus1.isMigrated());
    }

    @Test
    void shouldReturnMigrationDisabledByOuCode() {
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode(OU_CODE);
        courtSchedulerMigrationStatus.setCourtCentreId(COURT_CENTRE_ID);
        courtSchedulerMigrationStatus.setMigrated(false);
        courtMigrationRepository.save(courtSchedulerMigrationStatus);
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus1 = courtMigrationRepository.findByOuCode(OU_CODE);
        assertFalse(courtSchedulerMigrationStatus1.isMigrated());
    }

    @Test
    void shouldReturnMigrationDisabledByCourtCentreId() {
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode(OU_CODE);
        courtSchedulerMigrationStatus.setCourtCentreId(COURT_CENTRE_ID);
        courtSchedulerMigrationStatus.setMigrated(false);
        courtMigrationRepository.save(courtSchedulerMigrationStatus);
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus1 = courtMigrationRepository.findByCourtCentreId(COURT_CENTRE_ID);
        assertFalse(courtSchedulerMigrationStatus1.isMigrated());
    }

}
