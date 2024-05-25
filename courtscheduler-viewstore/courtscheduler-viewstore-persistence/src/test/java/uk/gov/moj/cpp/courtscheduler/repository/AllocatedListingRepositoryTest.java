package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;

import javax.inject.Inject;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
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

    @Test
    public void shouldFindByHearingId() {
        String hearingId = random(String.class);
        final AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setHearingId(hearingId);
        final AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setHearingId(hearingId);
        final AllocatedListing allocatedListing3 = random(AllocatedListing.class);

        allocatedListingRepository.save(allocatedListing1);
        allocatedListingRepository.save(allocatedListing2);
        allocatedListingRepository.save(allocatedListing3);
        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByHearingId(hearingId);

        assertThat(allocatedListings, hasItems(allocatedListing1, allocatedListing2));
    }

    @Test
    public void shouldFindAllocatedListingsUpdatedBetweenDates() {
        AllocatedListing allocatedListing = random(AllocatedListing.class);
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        MiFilterCriteria miFilterCriteria = new MiFilterCriteria(fromDate, toDate);

        allocatedListingRepository.save(allocatedListing);

        List<uk.gov.moj.cpp.courtscheduler.domain.AllocatedListing> courtScheduleJudiciaryList = allocatedListingRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
        assertThat(courtScheduleJudiciaryList.isEmpty(), is(false));
    }
}