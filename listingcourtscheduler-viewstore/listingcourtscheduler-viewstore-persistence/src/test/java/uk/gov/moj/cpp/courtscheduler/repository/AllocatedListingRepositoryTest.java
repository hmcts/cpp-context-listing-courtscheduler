package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.UTC_ZONE;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingTotalBooked;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.IdResponse;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;



class AllocatedListingRepositoryTest extends AbstractRepositoryTest {
    public static final LocalDate DEFAULT_SESSION_DATE = LocalDate.parse("2024-12-09");
    @Autowired
    private AllocatedListingRepository allocatedListingRepository;
    @Autowired
    private CourtScheduleRepository courtScheduleRepository;

    @AfterEach
    public void tearDown() {
        List<AllocatedListing> all = allocatedListingRepository.findAll();
        all.forEach(allocatedListing -> allocatedListingRepository.remove(allocatedListing));
    }

    @Test
    public void shouldSave() {
        final AllocatedListing allocatedListing = newAllocatedListingWithSavedSchedule();

        allocatedListingRepository.save(allocatedListing);
        AllocatedListing by = allocatedListingRepository.findBy(allocatedListing.getId());

        assertThat(by, notNullValue());

    }

    @Test
    public void shouldFindByHearingId() {
        String hearingId = random(String.class);
        final AllocatedListing allocatedListing1 = newAllocatedListingWithSavedSchedule();
        allocatedListing1.setHearingId(hearingId);
        final AllocatedListing allocatedListing2 = newAllocatedListingWithSavedSchedule();
        allocatedListing2.setHearingId(hearingId);
        final AllocatedListing allocatedListing3 = newAllocatedListingWithSavedSchedule();

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
        final String courtScheduleId = persistRandomCourtSchedule();
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
        String courtScheduleId = persistRandomCourtSchedule();
        final AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setCourtScheduleId(courtScheduleId);
        final AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setCourtScheduleId(courtScheduleId);
        final AllocatedListing allocatedListing3 = newAllocatedListingWithSavedSchedule();

        allocatedListingRepository.save(allocatedListing1);
        allocatedListingRepository.save(allocatedListing2);
        allocatedListingRepository.save(allocatedListing3);
        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByCourtScheduleId(courtScheduleId);

        assertThat(allocatedListings, hasItems(allocatedListing1, allocatedListing2));
    }

    @Test
    public void shouldGetAllocatedListingsByCourtScheduleId() {
        final String hearingId = random(String.class);
        final String courtScheduleId1 = persistRandomCourtSchedule();
        final String courtScheduleId2 = persistRandomCourtSchedule();

        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setPanel("ADULT");
        courtSchedule1.setCourtScheduleId(courtScheduleId1);
        courtSchedule1.setSlotBased(true);
        courtScheduleRepository.saveAndFlush(courtSchedule1);

        final CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setPanel("ADULT");
        courtSchedule2.setCourtScheduleId(courtScheduleId2);
        courtSchedule2.setSlotBased(true);
        courtScheduleRepository.saveAndFlush(courtSchedule2);

        // The schema added a unique index on (court_schedule_id, hearing_id) in
        // changeset 015; give each AllocatedListing its own hearingId so the
        // "two-rows-per-court-schedule" semantic the test asserts can still be set up.
        final String hearingIdA = random(String.class);
        final String hearingIdB = random(String.class);

        final AllocatedListing allocatedListing1 = newAllocatedListingWithSavedSchedule();
        allocatedListing1.setHearingId(hearingIdA);
        allocatedListing1.setCourtScheduleId(courtScheduleId1);
        allocatedListing1.setDuration(1);

        final AllocatedListing allocatedListing2 = newAllocatedListingWithSavedSchedule();
        allocatedListing2.setHearingId(hearingIdB);
        allocatedListing2.setCourtScheduleId(courtScheduleId1);
        allocatedListing2.setDuration(1);

        final AllocatedListing allocatedListing3 = newAllocatedListingWithSavedSchedule();
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
            assertThat(allocatedListingTotalBookeds.get(1).getTotalBooked(), is(1));
        } else {
            assertEquals(allocatedListingTotalBookeds.get(0).getCourtScheduleId(), courtScheduleId2);
            assertThat(allocatedListingTotalBookeds.get(0).getTotalBooked(), is(1));

            assertEquals(allocatedListingTotalBookeds.get(1).getCourtScheduleId(), courtScheduleId1);
            assertThat(allocatedListingTotalBookeds.get(1).getTotalBooked(), is(2));
        }
    }

    @Test
    public void shouldGetHearingIdsByReq() {
        final LocalDate today = LocalDate.now();
        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setPanel("ADULT");
        courtSchedule1.setCourtScheduleId("COURT-SCHEDULE-1");
        final LocalDate sessionDate = today.minusDays(3);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setCourtHouseName("HOUSE-1");
        courtSchedule1.setActive(true);
        courtScheduleRepository.saveAndFlush(courtSchedule1);

        final CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setPanel("ADULT");
        courtSchedule2.setCourtScheduleId("COURT-SCHEDULE-2");
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setCourtHouseName("HOUSE-2");
        courtSchedule2.setActive(true);
        courtScheduleRepository.saveAndFlush(courtSchedule2);

        final CourtSchedule courtSchedule3 = random(CourtSchedule.class);
        courtSchedule3.setPanel("ADULT");
        courtSchedule3.setCourtScheduleId("COURT-SCHEDULE-3");
        final LocalDate sessionDate1 = today.minusDays(1);
        courtSchedule3.setSessionDate(sessionDate1);
        courtSchedule3.setActive(true);
        courtScheduleRepository.saveAndFlush(courtSchedule3);

        List<String> expHearingIds = new ArrayList<>();
        final String hearingId1 = randomUUID().toString();
        expHearingIds.add(hearingId1);
        final LocalDateTime hearing1StartTime = sessionDate.atTime(14, 0);
        allocatedListingRepository.saveAndFlush(createAllocateListing("1", "BOOKING-1", "COURT-SCHEDULE-1", hearingId1, hearing1StartTime));
        final String hearingId2 = randomUUID().toString();
        expHearingIds.add(hearingId2);
        final LocalDateTime hearing2StartTime = sessionDate.atTime(16, 0);
        allocatedListingRepository.saveAndFlush(createAllocateListing("2", "BOOKING-2", "COURT-SCHEDULE-1", hearingId2, hearing2StartTime));

        final String hearingId3 = randomUUID().toString();
        expHearingIds.add(hearingId3);
        final LocalDateTime hearing3StartTime = sessionDate1.atTime(9, 0);
        allocatedListingRepository.saveAndFlush(createAllocateListing("3", "BOOKING-3", "COURT-SCHEDULE-2", hearingId3, hearing3StartTime));
        final String hearingId4 = randomUUID().toString();
        expHearingIds.add(hearingId4);
        final LocalDateTime hearing4StartTime = sessionDate1.atTime(11, 0);
        allocatedListingRepository.saveAndFlush(createAllocateListing("4", "BOOKING-4", "COURT-SCHEDULE-2", hearingId4, hearing4StartTime));

        final String hearingId5 = randomUUID().toString();
        expHearingIds.add(hearingId5);
        allocatedListingRepository.saveAndFlush(createAllocateListing("5", "BOOKING-5", "COURT-SCHEDULE-3", hearingId5));

        // Listing 6 originally pointed at "COURT-SCHEDULE-4" — a non-existent schedule
        // that the query was expected to filter out. With the FK added in changeset 033,
        // that orphan row is no longer insertable; drop it from setup since the test only
        // asserts on the five listings whose court_schedule is set up above.

        HearingSlotRequestParam hearingIdsRequest =
                new HearingSlotRequestParam("ADULT",
                        today.minusDays(6).toString(),
                        today.toString(),
                        null,
                        "",
                        "",
                        "10",
                        "1",
                        "",
                        "",
                        "",
                        "",
                        false,
                        null,
                        false,
                        null,
                        null,
                        null);
        Pair<Integer, Set<IdResponse>> hearingIdsResult = allocatedListingRepository.findHearingIdsBy(hearingIdsRequest);
        assertEquals(5, hearingIdsResult.getKey().longValue());
        List<IdResponse> actHearingIds = new ArrayList<>(hearingIdsResult.getValue());
        assertEquals(expHearingIds.get(0), actHearingIds.get(0).hearingId());
        assertEquals(expHearingIds.get(1), actHearingIds.get(1).hearingId());
        assertEquals(expHearingIds.get(2), actHearingIds.get(2).hearingId());
        assertEquals(expHearingIds.get(3), actHearingIds.get(3).hearingId());
        assertEquals(expHearingIds.get(4), actHearingIds.get(4).hearingId());

        final List<LocalDate> sessionDays = asList(sessionDate, sessionDate1, DEFAULT_SESSION_DATE);
        actHearingIds.forEach(each -> {
            assertThat(each.hearingDayPosition(), is(1L));
            assertThat(each.hearingDayCount(), is(1L));
            assertThat(sessionDays, hasItem(each.hearingDate()));
            assertThat(each.courtScheduleId(), startsWith("COURT-SCHEDULE-"));
        });


    }


    @Test
    public void shouldGetHearingIdsByReqWithMultiPanels() {
        final LocalDate today = LocalDate.now();
        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setPanel("ADULT");
        courtSchedule1.setCourtScheduleId("COURT-SCHEDULE-1");
        final LocalDate sessionDate = today.minusDays(3);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setCourtHouseName("HOUSE-1");
        courtSchedule1.setActive(true);
        courtScheduleRepository.saveAndFlush(courtSchedule1);

        final CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setPanel("YOUTH");
        courtSchedule2.setCourtScheduleId("COURT-SCHEDULE-2");
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setCourtHouseName("HOUSE-2");
        courtSchedule2.setActive(true);
        courtScheduleRepository.saveAndFlush(courtSchedule2);

        final String hearingId1 = randomUUID().toString();
        final LocalDateTime hearing1StartTime = sessionDate.atTime(14, 0);
        allocatedListingRepository.saveAndFlush(createAllocateListing("1", "BOOKING-1", "COURT-SCHEDULE-1", hearingId1, hearing1StartTime));

        final String hearingId2 = randomUUID().toString();
        final LocalDateTime hearing2StartTime = sessionDate.atTime(16, 0);
        allocatedListingRepository.saveAndFlush(createAllocateListing("2", "BOOKING-2", "COURT-SCHEDULE-2", hearingId2, hearing2StartTime));

        HearingSlotRequestParam hearingIdsRequest =
                new HearingSlotRequestParam("ADULT, YOUTH",
                        today.minusDays(6).toString(),
                        today.toString(),
                        null,
                        "",
                        "",
                        "10",
                        "1",
                        "",
                        "",
                        "",
                        "",
                        null,
                        null,
                        false,
                        null,
                        null,
                        null);
        Pair<Integer, Set<IdResponse>> hearingIdsResult = allocatedListingRepository.findHearingIdsBy(hearingIdsRequest);
        assertEquals(2, hearingIdsResult.getKey().longValue());
        List<IdResponse> actHearingIds = new ArrayList<>(hearingIdsResult.getValue());
        assertThat(actHearingIds.get(0).hearingId(), is(hearingId1));
        assertThat(actHearingIds.get(0).hearingDayCount(), is(1L));
        assertThat(actHearingIds.get(0).hearingDayPosition(), is(1L));
        assertThat(actHearingIds.get(0).hearingDate(), is(sessionDate));
        assertThat(actHearingIds.get(0).courtScheduleId(), is("COURT-SCHEDULE-1"));

        assertThat(actHearingIds.get(1).hearingId(), is(hearingId2));
        assertThat(actHearingIds.get(1).hearingDayCount(), is(1L));
        assertThat(actHearingIds.get(1).hearingDayPosition(), is(1L));
        assertThat(actHearingIds.get(1).hearingDate(), is(sessionDate));
        assertThat(actHearingIds.get(1).courtScheduleId(), is("COURT-SCHEDULE-2"));
    }

    private AllocatedListing createAllocateListing(String id,
                                                   String bookingId,
                                                   String courtScheduleId,
                                                   String hearingId) {
        return createAllocateListing(id,
                bookingId,
                courtScheduleId,
                hearingId,
                DEFAULT_SESSION_DATE.atTime(14, 0));
    }

    private AllocatedListing createAllocateListing(String id,
                                                   String bookingId,
                                                   String courtScheduleId,
                                                   String hearingId, LocalDateTime hearingStartTime) {
        AllocatedListing allocatedListing = new AllocatedListing();
        allocatedListing.setId(id);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setCourtRoomId(1);
        allocatedListing.setHearingStartTime(Date.from(hearingStartTime.atZone(UTC_ZONE).toInstant()));
        allocatedListing.setDuration(120);
        allocatedListing.setOucode("BA124");
        allocatedListing.setRotaBusinessType("BUSS");
        allocatedListing.setSource("DEFAULT");

        return allocatedListing;
    }

    @Test
    public void shouldUpdateSourceByHearingId() {
        final String hearingId = randomUUID().toString();
        final AllocatedListing listing1 = random(AllocatedListing.class);
        listing1.setHearingId(hearingId);
        listing1.setCourtScheduleId(persistRandomCourtSchedule());
        listing1.setSource("DEFAULT");
        final AllocatedListing listing2 = random(AllocatedListing.class);
        listing2.setHearingId(hearingId);
        // distinct schedule — allocated_listings has a unique (court_schedule_id, hearing_id) index
        listing2.setCourtScheduleId(persistRandomCourtSchedule());
        listing2.setSource("DEFAULT");
        final AllocatedListing otherHearingListing = random(AllocatedListing.class);
        otherHearingListing.setCourtScheduleId(persistRandomCourtSchedule());
        otherHearingListing.setSource("DEFAULT");

        allocatedListingRepository.save(listing1);
        allocatedListingRepository.save(listing2);
        allocatedListingRepository.save(otherHearingListing);

        final int updated = allocatedListingRepository.updateSourceByHearingId(hearingId, "CROWN_FB_LIST");

        assertThat(updated, is(2));
        final List<AllocatedListing> updatedRows = allocatedListingRepository.findByHearingId(hearingId);
        for (final AllocatedListing row : updatedRows) {
            assertThat(row.getSource(), is("CROWN_FB_LIST"));
        }
        final AllocatedListing untouched = allocatedListingRepository.findBy(otherHearingListing.getId());
        assertThat(untouched.getSource(), is("DEFAULT"));
    }

    @Test
    public void shouldReturnZeroAndNotUpdate_whenSourceIsNull() {
        final String hearingId = randomUUID().toString();
        final AllocatedListing listing = random(AllocatedListing.class);
        listing.setHearingId(hearingId);
        listing.setCourtScheduleId(persistRandomCourtSchedule());
        listing.setSource("DEFAULT");
        allocatedListingRepository.save(listing);

        final int updated = allocatedListingRepository.updateSourceByHearingId(hearingId, null);

        assertThat(updated, is(0));
        final AllocatedListing unchanged = allocatedListingRepository.findBy(listing.getId());
        assertThat(unchanged.getSource(), is("DEFAULT"));
    }

    @Test
    public void shouldReturnZero_whenHearingIdDoesNotMatch() {
        final int updated = allocatedListingRepository.updateSourceByHearingId(randomUUID().toString(), "CROWN_FB_UPDATE");
        assertThat(updated, is(0));
    }

    @Test
    public void shouldFindByHearingIdOrderBySessionDateAsc() {
        final String hearingId = randomUUID().toString();
        final LocalDate day1 = LocalDate.of(2026, 3, 2);
        final LocalDate day2 = LocalDate.of(2026, 3, 3);
        final LocalDate day3 = LocalDate.of(2026, 3, 4);

        seedScheduleAndAllocation("CS-EXTEND-DAY2", day2, hearingId, "AL-EXTEND-DAY2");
        seedScheduleAndAllocation("CS-EXTEND-DAY1", day1, hearingId, "AL-EXTEND-DAY1");
        seedScheduleAndAllocation("CS-EXTEND-DAY3", day3, hearingId, "AL-EXTEND-DAY3");

        final List<AllocatedListing> ordered = allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(hearingId);

        assertEquals(3, ordered.size());
        assertEquals("CS-EXTEND-DAY1", ordered.get(0).getCourtScheduleId());
        assertEquals("CS-EXTEND-DAY2", ordered.get(1).getCourtScheduleId());
        assertEquals("CS-EXTEND-DAY3", ordered.get(2).getCourtScheduleId());
    }

    @Test
    public void shouldDeleteByHearingIdAndSessionDateGreaterThan() {
        final String hearingId = randomUUID().toString();
        final LocalDate day1 = LocalDate.of(2026, 3, 2);
        final LocalDate day2 = LocalDate.of(2026, 3, 3);
        final LocalDate day3 = LocalDate.of(2026, 3, 4);

        seedScheduleAndAllocation("CS-DEL-DAY1", day1, hearingId, "AL-DEL-DAY1");
        seedScheduleAndAllocation("CS-DEL-DAY2", day2, hearingId, "AL-DEL-DAY2");
        seedScheduleAndAllocation("CS-DEL-DAY3", day3, hearingId, "AL-DEL-DAY3");

        final int deleted = allocatedListingRepository.deleteByHearingIdAndSessionDateGreaterThan(hearingId, day2);

        assertEquals(1, deleted);
        final List<AllocatedListing> remaining = allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(hearingId);
        assertEquals(2, remaining.size());
        assertEquals("CS-DEL-DAY1", remaining.get(0).getCourtScheduleId());
        assertEquals("CS-DEL-DAY2", remaining.get(1).getCourtScheduleId());
    }

    private void seedScheduleAndAllocation(final String courtScheduleId, final LocalDate sessionDate,
                                           final String hearingId, final String allocationId) {
        final CourtSchedule cs = random(CourtSchedule.class);
        cs.setCourtScheduleId(courtScheduleId);
        cs.setSessionDate(sessionDate);
        cs.setActive(true);
        courtScheduleRepository.saveAndFlush(cs);

        allocatedListingRepository.saveAndFlush(
                createAllocateListing(allocationId, "BK-" + allocationId, courtScheduleId, hearingId,
                        sessionDate.atTime(10, 0)));
    }

}