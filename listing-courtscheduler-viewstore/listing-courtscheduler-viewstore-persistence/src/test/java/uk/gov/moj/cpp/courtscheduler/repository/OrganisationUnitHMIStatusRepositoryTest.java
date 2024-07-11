package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.persist.entity.OrganisationUnitHMIStatus;

import java.util.List;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CdiTestRunner.class)
public class OrganisationUnitHMIStatusRepositoryTest {

    @Inject
    private OrganisationUnitHMIStatusRepository organisationUnitHMIStatusRepository;

    @After
    public void tearDown() {
        List<OrganisationUnitHMIStatus> all = organisationUnitHMIStatusRepository.findAll();
        all.forEach(organisationUnitHMIStatus -> organisationUnitHMIStatusRepository.remove(organisationUnitHMIStatus));
    }

    @Test
    public void shouldSave() {

        OrganisationUnitHMIStatus organisationUnitHMIStatus = random(OrganisationUnitHMIStatus.class);

        organisationUnitHMIStatusRepository.save(organisationUnitHMIStatus);

        // then
        OrganisationUnitHMIStatus by = organisationUnitHMIStatusRepository.findBy(organisationUnitHMIStatus.getOucode());

        assertThat(by, notNullValue());

    }
}