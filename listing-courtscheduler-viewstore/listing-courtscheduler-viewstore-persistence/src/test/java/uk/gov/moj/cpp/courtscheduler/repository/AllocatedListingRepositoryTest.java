package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;

import java.time.LocalDate;
import java.util.List;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;


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
    @Test
    public void shouldReturnTotalListedDurationForCourtscheduleId() {
        final String courtScheduleId = randomUUID().toString();
        AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setDuration(20);
        allocatedListing1.setCourtScheduleId(courtScheduleId);
        AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setDuration(10);
        allocatedListing2.setCourtScheduleId(courtScheduleId);
        allocatedListingRepository.save(allocatedListing1);
        allocatedListingRepository.save(allocatedListing2);
        final Integer totalAllocatedDuration = allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(courtScheduleId);
        assertThat(totalAllocatedDuration, is(30));
    }

    @Test
    public void shouldFindByCourtScheduleId() {
        String courtScheduleId = random(String.class);
        final AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setCourtScheduleId(courtScheduleId);
        final AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setCourtScheduleId(courtScheduleId);
        final AllocatedListing allocatedListing3 = random(AllocatedListing.class);

        allocatedListingRepository.save(allocatedListing1);
        allocatedListingRepository.save(allocatedListing2);
        allocatedListingRepository.save(allocatedListing3);
        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByCourtScheduleId(courtScheduleId);

        assertThat(allocatedListings, hasItems(allocatedListing1, allocatedListing2));
    }

    @Test
    public void shouldGetAllocatedListingsByCourtScheduleId() {
        final String hearingId = random(String.class);
        final String courtScheduleId1 = randomUUID().toString();
        final String courtScheduleId2 = randomUUID().toString();

        final AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setHearingId(hearingId);
        allocatedListing1.setCourtScheduleId(courtScheduleId1);
        allocatedListing1.setDuration(1);

        final AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setHearingId(hearingId);
        allocatedListing2.setCourtScheduleId(courtScheduleId1);
        allocatedListing2.setDuration(1);

        final AllocatedListing allocatedListing3 = random(AllocatedListing.class);
        allocatedListing3.setHearingId(hearingId);
        allocatedListing3.setCourtScheduleId(courtScheduleId2);
        allocatedListing3.setDuration(30);

        allocatedListingRepository.save(allocatedListing1);
        allocatedListingRepository.save(allocatedListing2);
        allocatedListingRepository.save(allocatedListing3);

        final List<AllocatedListingTotalBooked> allocatedListingTotalBookeds = allocatedListingRepository.getAllocatedListingsByCourtScheduleId(List.of(courtScheduleId1, courtScheduleId2));

        assertEquals(allocatedListingTotalBookeds.size(), 2);
        if (allocatedListingTotalBookeds.get(0).getCourtScheduleId().equals(courtScheduleId1)) {
            assertEquals(allocatedListingTotalBookeds.get(0).getCourtScheduleId(), courtScheduleId1);
            assertThat(allocatedListingTotalBookeds.get(0).getTotalBooked(), is(2));

            assertEquals(allocatedListingTotalBookeds.get(1).getCourtScheduleId(), courtScheduleId2);
            assertThat(allocatedListingTotalBookeds.get(1).getTotalBooked(), is(30));
        } else {
            assertEquals(allocatedListingTotalBookeds.get(0).getCourtScheduleId(), courtScheduleId2);
            assertThat(allocatedListingTotalBookeds.get(0).getTotalBooked(), is(30));

            assertEquals(allocatedListingTotalBookeds.get(1).getCourtScheduleId(), courtScheduleId1);
            assertThat(allocatedListingTotalBookeds.get(1).getTotalBooked(), is(2));
        }
    }
}