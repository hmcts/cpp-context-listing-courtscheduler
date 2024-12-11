package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.Collections.sort;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(CdiTestRunner.class)
public class AllocatedListingRepositoryTest {
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private CourtScheduleRepository courtScheduleRepository;

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

        assertEquals(2, allocatedListingTotalBookeds.size());
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

    @Test
    public void shouldGetHearingIdsByReq() throws Exception {

        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtScheduleId("COURT-SCHEDULE-1");
        courtSchedule1.setPanel("ADULT");
        courtSchedule1.setSessionDate(LocalDate.now());
        courtSchedule1.setOperationalUnit("BA124");
        courtSchedule1.setOuCode("BA124");
        courtSchedule1.setActive(true);
        courtScheduleRepository.saveAndFlush(courtSchedule1);

        final CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId("COURT-SCHEDULE-2");
        courtSchedule2.setPanel("ADULT");
        courtSchedule2.setSessionDate(LocalDate.now());
        courtSchedule2.setOperationalUnit("BA124");
        courtSchedule2.setOuCode("BA124");
        courtSchedule2.setActive(true);
        courtScheduleRepository.saveAndFlush(courtSchedule2);

        List<String> expHearingIds = new ArrayList<>();
        final String hearingId1 = randomUUID().toString();
        expHearingIds.add(hearingId1);
        allocatedListingRepository.saveAndFlush(createAllocateListing("1", "BOOKING-1", "COURT-SCHEDULE-1", hearingId1));
        final String hearingId2 = randomUUID().toString();
        expHearingIds.add(hearingId2);
        allocatedListingRepository.saveAndFlush(createAllocateListing("2", "BOOKING-2", "COURT-SCHEDULE-1", hearingId2));
        final String hearingId3 = randomUUID().toString();
        expHearingIds.add(hearingId3);
        allocatedListingRepository.saveAndFlush(createAllocateListing("3", "BOOKING-3", "COURT-SCHEDULE-2", hearingId3));
        final String hearingId4 = randomUUID().toString();
        expHearingIds.add(hearingId4);
        allocatedListingRepository.saveAndFlush(createAllocateListing("4", "BOOKING-4", "COURT-SCHEDULE-2", hearingId4));
        final String hearingId5 = randomUUID().toString();
        allocatedListingRepository.saveAndFlush(createAllocateListing("5", "BOOKING-5", "COURT-SCHEDULE-3", hearingId5));
        final String hearingId6 = randomUUID().toString();
        allocatedListingRepository.saveAndFlush(createAllocateListing("6", "BOOKING-6", "COURT-SCHEDULE-4", hearingId6));

        HearingSlotRequestParam hearingIdsRequest =
                new HearingSlotRequestParam("ADULT",
                        "2024-12-09",
                        "2024-12-15",
                        "",
                        "",
                        "10",
                        "1",
                        "",
                        "",
                        "",
                        "");
        Pair<Integer, List<String>> hearingIdsResult = allocatedListingRepository.findHearingIdsBy(hearingIdsRequest);
        assertEquals(4, hearingIdsResult.getKey().longValue());
        sort(expHearingIds);
        List<String> hearingIds = hearingIdsResult.getValue();
        assertEquals(expHearingIds.get(0), hearingIds.get(0));
        assertEquals(expHearingIds.get(1), hearingIds.get(1));
        assertEquals(expHearingIds.get(2), hearingIds.get(2));
        assertEquals(expHearingIds.get(3), hearingIds.get(3));
    }

    private AllocatedListing createAllocateListing(String id, String bookingId, String courtScheduleId, String hearingId) {
        AllocatedListing allocatedListing = new AllocatedListing();
        allocatedListing.setId(id);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setCourtRoomId(1);
        allocatedListing.setHearingStartTime(Date.from(LocalDate.parse("2024-12-09").atTime(14, 0).atZone(ZoneId.of("Europe/London")).toInstant()));
        allocatedListing.setDuration(120);
        allocatedListing.setOucode("BA124");
        allocatedListing.setRotaBusinessType("BUSS");

        return allocatedListing;
    }
}