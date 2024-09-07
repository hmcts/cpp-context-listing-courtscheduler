package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.persist.entity.BusinessType;

import java.util.List;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(CdiTestRunner.class)
public class BusinessTypeRepositoryTest {

    @Inject
    private BusinessTypeRepository businessTypeRepository;

    @After
    public void tearDown() {
        List<BusinessType> businessTypes = businessTypeRepository.findAll();
        businessTypes.forEach(businessType -> businessTypeRepository.remove(businessType));
    }

    @Test
    public void shouldSave() {
        final BusinessType businessType = random(BusinessType.class);

        businessTypeRepository.save(businessType);
        BusinessType by = businessTypeRepository.findBy(businessType.getId());

        assertThat(by, notNullValue());

    }
}
