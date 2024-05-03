package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;

import javax.inject.Inject;
import java.util.List;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;


@RunWith(CdiTestRunner.class)
public class ProvisionalBookingRepositoryTest {
    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;

    @After
    public void tearDown() {
        List<ProvisionalBooking> all = provisionalBookingRepository.findAll();
        all.forEach(provisionalBooking -> provisionalBookingRepository.remove(provisionalBooking));
    }

    @Test
    public void shouldSave() {
        final ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);

        provisionalBookingRepository.save(provisionalBooking);
        ProvisionalBooking by = provisionalBookingRepository.findBy(provisionalBooking.getProvisionalBookingKey());

        assertThat(by, notNullValue());

    }
}