package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;

import javax.inject.Inject;
import java.util.List;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;


@RunWith(CdiTestRunner.class)
public class AllocatedListingRepositoryTest {
    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    @After
    public void tearDown() {
        List<AllocatedListing> all = allocatedListingRepository.findAll();
        all.forEach(allocatedListing -> allocatedListingRepository.remove(allocatedListing));
    }

    @Test
    public void shouldSave() {
        final AllocatedListing allocatedListing = random(AllocatedListing.class);

        allocatedListingRepository.save(allocatedListing);
        AllocatedListing by = allocatedListingRepository.findBy(allocatedListing.getId());

        assertThat(by, notNullValue());

    }


}