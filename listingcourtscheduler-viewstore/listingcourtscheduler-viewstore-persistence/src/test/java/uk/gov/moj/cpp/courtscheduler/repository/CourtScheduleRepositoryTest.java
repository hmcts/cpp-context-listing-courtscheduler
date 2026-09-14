package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.LONDON_ZONE;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackRequest;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackSearchResult;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Repository tests for the ccsph2-era additions to {@link CourtScheduleRepository}
 * (multiday slot-candidate discovery, judiciary enrichment lookups, byIdList guards).
 *
 * <p>The original 1,700-line legacy test (preserved in git history, pre-Spring-Boot) was a
 * real-DB integration test driven by DeltaSpike's {@code @RunWith(CdiTestRunner.class)}; its
 * assertions drifted against the production schema and its bulk behaviour is re-asserted by
 * the integration-test module — that per-test revival remains tracked separately. The tests
 * below are the ones added on team/ccsph2 and were written against the current schema.</p>
 *
 * <p>FK note: changeset 033 enforces {@code court_schedule_judiciary.court_schedule_id}
 * → {@code court_schedule(id)}; unlike the branch these tests came from, every judiciary row
 * here persists its parent schedule first.</p>
 */
class CourtScheduleRepositoryTest extends AbstractRepositoryTest {

    @Autowired
    private CourtScheduleRepository courtScheduleRepository;

    @Autowired
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Autowired
    private AllocatedListingRepository allocatedListingRepository;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    // Behavioural coverage of getCourtSchedulesByIdList's aggregation semantics lives in the
    // integration tests (see CourtSchedulerIT). This unit test only covers the short-circuit
    // branches that don't reach the query.
    @Test
    public void getCourtSchedulesByIdListShouldReturnEmptyListForEmptyOrNullInput() {
        assertTrue(courtScheduleRepository.getCourtSchedulesByIdList(new ArrayList<>()).isEmpty());
        assertTrue(courtScheduleRepository.getCourtSchedulesByIdList(null).isEmpty());
    }

    // Tests for getMultidayHearingSlotCandidates short-circuit paths:
    //   (a) the discovery query returns no rows
    //   (b) discovery finds rows but no room has the required consecutive business days
    // The ID_QUERY_BATCH_SIZE partitioning in rehydrateMultidayCandidatesWithSlotStartTimes and
    // getCountBasedAllocatedListing is covered by the end-to-end integration test suite.

    @Test
    public void getMultidayHearingSlotCandidatesShouldReturnEmptyWhenNoMatchingSchedulesExist() {
        // No schedules in DB → discovery returns empty rows → short-circuits before rehydration
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "ADULT",
                "2026-06-01",
                "2026-06-05",
                null, null, null,
                "10", "1",
                null, null, null, null, null, null,
                false, null, null, null);

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                courtScheduleRepository.getMultidayHearingSlotCandidates(requestParam, 2);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getMultidayHearingSlotCandidatesShouldReturnEmptyWhenSchedulesHaveNoConsecutiveBusinessDays() {
        // Three schedules in same room on Mon/Wed/Fri — no two are consecutive business days.
        // Discovery finds all three rows; grouping and consecutive-day check produces no candidates
        // → candidateIds is empty → short-circuits before rehydration.
        final LocalDate monday    = LocalDate.of(2026, 6, 1);
        final LocalDate wednesday = LocalDate.of(2026, 6, 3);
        final LocalDate friday    = LocalDate.of(2026, 6, 5);
        final String ouCode = "B99MC00";

        for (LocalDate date : List.of(monday, wednesday, friday)) {
            courtScheduleRepository.save(createCourtSchedule(ouCode, "ADULT", date, "CR01", "TRF"));
        }

        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "ADULT",
                monday.toString(),
                friday.toString(),
                null, null, ouCode,
                "10", "1",
                null, null, null, null, null, null,
                false, null, null, null);

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                courtScheduleRepository.getMultidayHearingSlotCandidates(requestParam, 2);

        assertTrue(result.isEmpty());
    }

    @Test
    public void getMultidayHearingSlotCandidatesShouldReturnEmptyWhenConsecutiveDaysAreInDifferentRooms() {
        // Mon in CR01 and Tue in CR02 — consecutive days exist but not within any single room.
        // Grouping by (room, businessType, ouCode) means neither room has 2 consecutive days
        // → candidateIds is empty → short-circuits before rehydration.
        final LocalDate monday  = LocalDate.of(2026, 6, 8);
        final LocalDate tuesday = LocalDate.of(2026, 6, 9);
        final String ouCode = "B99MC01";

        courtScheduleRepository.save(createCourtSchedule(ouCode, "ADULT", monday,  "CR01", "TRF"));
        courtScheduleRepository.save(createCourtSchedule(ouCode, "ADULT", tuesday, "CR02", "TRF"));

        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "ADULT",
                monday.toString(),
                tuesday.toString(),
                null, null, ouCode,
                "10", "1",
                null, null, null, null, null, null,
                false, null, null, null);

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                courtScheduleRepository.getMultidayHearingSlotCandidates(requestParam, 2);

        assertTrue(result.isEmpty());
    }

    // SPRDT-1276: the CROWN multiday search forces courtSession=AD / isSlotBased=false. These two
    // tests pin the repository half of that contract: the court_session predicate actually filters,
    // and businessType no longer suppresses the is_slot_based predicate.

    @Test
    public void getMultidayHearingSlotCandidatesShouldExcludeAmSessionsWhenCourtSessionIsAd() {
        // Two rooms, both with consecutive Mon+Tue sessions. CR01 sits AM, CR02 sits AD.
        // A 2-day CROWN search must see CR02 only — before the fix an absent/AM court_session
        // let the AM room through, which is the AM session on the ticket's screenshot.
        final LocalDate monday  = LocalDate.of(2026, 6, 15);
        final LocalDate tuesday = LocalDate.of(2026, 6, 16);
        final String ouCode = "B99MC02";

        for (LocalDate date : List.of(monday, tuesday)) {
            courtScheduleRepository.save(createCourtSchedule(ouCode, "ADULT", date, "CR01", "TRF", "AM"));
            courtScheduleRepository.save(createCourtSchedule(ouCode, "ADULT", date, "CR02", "TRF", "AD"));
        }

        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "ADULT",
                monday.toString(),
                tuesday.toString(),
                null, null, ouCode,
                "10", "1",
                null, null, null, "AD", false, null,
                false, "720", null, "CROWN");

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                courtScheduleRepository.getMultidayHearingSlotCandidates(requestParam, 2);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(cs -> "CR02".equals(cs.getCourtRoomId())));
    }

    @Test
    public void getMultidayHearingSlotCandidatesShouldApplyBothBusinessTypeAndIsSlotBasedForCrown() {
        // Two rooms with the same businessType and the same consecutive days: CR01 duration-based,
        // CR02 slot-based. Supplying businessType used to suppress the is_slot_based predicate
        // (if/else), so both rooms came back. For a CROWN >360 search both predicates now apply
        // and CR02 must drop out.
        //
        // The rooms must differ: unique index court_act_business_date_session_idx_am keys on
        // (oucode, court_room_id, rota_business_type, session_start, court_session[AD->AM],
        // is_draft), so one room cannot hold two sessions differing only by is_slot_based.
        final LocalDate monday  = LocalDate.of(2026, 6, 22);
        final LocalDate tuesday = LocalDate.of(2026, 6, 23);
        final String ouCode = "B99MC03";

        for (LocalDate date : List.of(monday, tuesday)) {
            courtScheduleRepository.save(createCourtSchedule(ouCode, "ADULT", date, "CR01", "TRF", "AD"));
            final CourtSchedule slotBased = createCourtSchedule(ouCode, "ADULT", date, "CR02", "TRF", "AD");
            slotBased.setSlotBased(true);
            courtScheduleRepository.save(slotBased);
        }

        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "ADULT",
                monday.toString(),
                tuesday.toString(),
                null, null, ouCode,
                "10", "1",
                null, null, "TRF", "AD", false, null,
                false, "720", null, "CROWN");

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> result =
                courtScheduleRepository.getMultidayHearingSlotCandidates(requestParam, 2);

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule::isSlotBased));
        assertTrue(result.stream().allMatch(cs -> "CR01".equals(cs.getCourtRoomId())));
    }

    @Test
    public void getCourtSchedulesShouldKeepBusinessTypeSuppressingIsSlotBasedForMagistrates() {
        // MAGISTRATES regression guard for SPRDT-1276. The CROWN >360 carve-out must not reach
        // here: businessType is supplied, so the caller's isSlotBased=true stays IGNORED and the
        // duration-based row is still returned. If the carve-out ever loses its jurisdiction
        // gate, the is_slot_based=true predicate is appended and this returns 0.
        final LocalDate monday = LocalDate.of(2026, 7, 6);
        final String ouCode = "B99MG01";

        final CourtSchedule magsSchedule = createCourtSchedule(ouCode, "ADULT", monday, "CR01", "TRF", "AM");
        magsSchedule.setJurisdiction("MAGISTRATES");
        courtScheduleRepository.save(magsSchedule);

        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "ADULT",
                monday.toString(),
                monday.toString(),
                null, null, ouCode,
                "10", "1",
                null, null, "TRF", null, true, null,
                false, "60", null, "MAGISTRATES");

        assertEquals(1, courtScheduleRepository.getCourtSchedules(requestParam).getValue().size());
    }

    @Test
    public void getCourtSchedulesShouldKeepBusinessTypeSuppressingIsSlotBasedForMagistratesOverAFullDay() {
        // The threshold alone must not trigger the carve-out — a MAGISTRATES search for 720
        // minutes is still an ordinary search. Same expectation as the single-day MAGS case.
        final LocalDate monday = LocalDate.of(2026, 7, 13);
        final String ouCode = "B99MG02";

        final CourtSchedule magsSchedule = createCourtSchedule(ouCode, "ADULT", monday, "CR01", "TRF", "AM");
        magsSchedule.setJurisdiction("MAGISTRATES");
        courtScheduleRepository.save(magsSchedule);

        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "ADULT",
                monday.toString(),
                monday.toString(),
                null, null, ouCode,
                "10", "1",
                null, null, "TRF", null, true, null,
                false, "720", null, "MAGISTRATES");

        assertEquals(1, courtScheduleRepository.getCourtSchedules(requestParam).getValue().size());
    }

    @Test
    public void getCourtSchedulesShouldKeepBusinessTypeSuppressingIsSlotBasedForSingleDayCrown() {
        // CROWN at or below a full day is also outside the carve-out — 360 is not "> 360".
        final LocalDate monday = LocalDate.of(2026, 7, 20);
        final String ouCode = "B99CR01";

        courtScheduleRepository.save(createCourtSchedule(ouCode, "ADULT", monday, "CR01", "TRF", "AD"));

        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "ADULT",
                monday.toString(),
                monday.toString(),
                null, null, ouCode,
                "10", "1",
                null, null, "TRF", null, true, null,
                false, "360", null, "CROWN");

        assertEquals(1, courtScheduleRepository.getCourtSchedules(requestParam).getValue().size());
    }

    // -----------------------------------------------------------------------
    // Tests for getCourtScheduleJudiciariesByCourtScheduleIds
    // -----------------------------------------------------------------------

    @Test
    public void getCourtScheduleJudiciariesByCourtScheduleIdsShouldReturnEmptyForNullOrEmptyInput() {
        assertTrue(courtScheduleRepository.getCourtScheduleJudiciariesByCourtScheduleIds(null).isEmpty());
        assertTrue(courtScheduleRepository.getCourtScheduleJudiciariesByCourtScheduleIds(new ArrayList<>()).isEmpty());
    }

    @Test
    public void getCourtScheduleJudiciariesByCourtScheduleIdsShouldReturnOnlyActiveRecordsForGivenIds() {
        final String scheduleId1 = persistScheduleWithId(randomUUID().toString());
        final String scheduleId2 = persistScheduleWithId(randomUUID().toString());
        final String unqueriedId = persistScheduleWithId(randomUUID().toString());

        // Two active records for schedule 1, one inactive for schedule 1, one active for an unqueried schedule
        courtScheduleJudiciaryRepository.saveAndFlush(createJudiciary(scheduleId1, randomUUID().toString(), "PROFILE-A", true));
        courtScheduleJudiciaryRepository.saveAndFlush(createJudiciary(scheduleId1, randomUUID().toString(), "PROFILE-A", true));
        courtScheduleJudiciaryRepository.saveAndFlush(createJudiciary(scheduleId1, randomUUID().toString(), "PROFILE-A", false));
        courtScheduleJudiciaryRepository.saveAndFlush(createJudiciary(unqueriedId, randomUUID().toString(), "PROFILE-A", true));

        List<CourtScheduleJudiciary> result =
                courtScheduleRepository.getCourtScheduleJudiciariesByCourtScheduleIds(List.of(scheduleId1, scheduleId2));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(j -> scheduleId1.equals(j.getId().getCourtScheduleId())));
        assertTrue(result.stream().allMatch(CourtScheduleJudiciary::getActive));
    }

    // -----------------------------------------------------------------------
    // Tests for getCourtScheduleJudiciariesForProvisionalBooking
    // -----------------------------------------------------------------------

    @Test
    public void getCourtScheduleJudiciariesForProvisionalBookingShouldReturnEmptyForNullOrEmptyInput() {
        assertTrue(courtScheduleRepository.getCourtScheduleJudiciariesForProvisionalBooking(null).isEmpty());
        assertTrue(courtScheduleRepository.getCourtScheduleJudiciariesForProvisionalBooking(new ArrayList<>()).isEmpty());
    }

    @Test
    public void getCourtScheduleJudiciariesForProvisionalBookingShouldReturnRecordsMatchingBothScheduleAndProfileId() {
        final String s1 = persistScheduleWithId(randomUUID().toString());
        final String s2 = persistScheduleWithId(randomUUID().toString());
        final String p1 = randomUUID().toString();
        final String p2 = randomUUID().toString();

        // (s1,p1) — should be returned
        courtScheduleJudiciaryRepository.saveAndFlush(createJudiciary(s1, randomUUID().toString(), p1, true));
        // (s1,p2) — profile not in query list → excluded
        courtScheduleJudiciaryRepository.saveAndFlush(createJudiciary(s1, randomUUID().toString(), p2, true));
        // (s2,p1) — schedule not in query list → excluded
        courtScheduleJudiciaryRepository.saveAndFlush(createJudiciary(s2, randomUUID().toString(), p1, true));

        // Build a CourtSchedule entity whose IDs drive the query (does not need to be persisted)
        CourtSchedule cs = new CourtSchedule();
        cs.setCourtScheduleId(s1);
        cs.setListingProfileId(p1);

        List<CourtScheduleJudiciary> result =
                courtScheduleRepository.getCourtScheduleJudiciariesForProvisionalBooking(List.of(cs));

        assertEquals(1, result.size());
        assertEquals(s1, result.get(0).getId().getCourtScheduleId());
        assertEquals(p1, result.get(0).getCourtListingProfileId());
    }

    // -----------------------------------------------------------------------
    // Tests for releaseAllocatedListingsForDates / saveBookedSlots release flag
    // (change-court-room-for-multiday-hearing, team/ccsph2 PR #856)
    // -----------------------------------------------------------------------

    @Test
    public void releasesOnlyAllocationsOnRequestedDates() {
        final String hearingId = randomUUID().toString();
        final LocalDate day1 = LocalDate.of(2026, 7, 15);
        final LocalDate day2 = LocalDate.of(2026, 7, 16);
        final LocalDate day3 = LocalDate.of(2026, 7, 17);

        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-REL-DAY1", day1));
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-REL-DAY2", day2));
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-REL-DAY3", day3));

        allocatedListingRepository.saveAndFlush(allocatedListingFor("AL-REL-DAY1", "BK-REL-DAY1", "CS-REL-DAY1", hearingId, day1));
        allocatedListingRepository.saveAndFlush(allocatedListingFor("AL-REL-DAY2", "BK-REL-DAY2", "CS-REL-DAY2", hearingId, day2));
        allocatedListingRepository.saveAndFlush(allocatedListingFor("AL-REL-DAY3", "BK-REL-DAY3", "CS-REL-DAY3", hearingId, day3));

        courtScheduleRepository.releaseAllocatedListingsForDates(hearingId, List.of(day2, day3));

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();

        final List<AllocatedListing> remaining = allocatedListingRepository.findByHearingId(hearingId);
        assertEquals(1, remaining.size());
        assertEquals("CS-REL-DAY1", remaining.get(0).getCourtScheduleId());

        assertEquals(Integer.valueOf(9), courtScheduleRepository.findBy("CS-REL-DAY1").getAvailableSlots());
        assertEquals(Integer.valueOf(10), courtScheduleRepository.findBy("CS-REL-DAY2").getAvailableSlots());
        assertEquals(Integer.valueOf(10), courtScheduleRepository.findBy("CS-REL-DAY3").getAvailableSlots());
    }

    @Test
    public void noopWhenNoAllocationsMatchDates() {
        final String hearingId = randomUUID().toString();
        final LocalDate day1 = LocalDate.of(2026, 7, 15);
        final LocalDate day2 = LocalDate.of(2026, 7, 16);
        final LocalDate day3 = LocalDate.of(2026, 7, 17);
        final LocalDate unrelatedDate = LocalDate.of(2026, 7, 18);

        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-NOOP-DAY1", day1));
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-NOOP-DAY2", day2));
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-NOOP-DAY3", day3));

        allocatedListingRepository.saveAndFlush(allocatedListingFor("AL-NOOP-DAY1", "BK-NOOP-DAY1", "CS-NOOP-DAY1", hearingId, day1));
        allocatedListingRepository.saveAndFlush(allocatedListingFor("AL-NOOP-DAY2", "BK-NOOP-DAY2", "CS-NOOP-DAY2", hearingId, day2));
        allocatedListingRepository.saveAndFlush(allocatedListingFor("AL-NOOP-DAY3", "BK-NOOP-DAY3", "CS-NOOP-DAY3", hearingId, day3));

        courtScheduleRepository.releaseAllocatedListingsForDates(hearingId, List.of(unrelatedDate));

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();

        final List<AllocatedListing> remaining = allocatedListingRepository.findByHearingId(hearingId);
        assertEquals(3, remaining.size());

        assertEquals(Integer.valueOf(9), courtScheduleRepository.findBy("CS-NOOP-DAY1").getAvailableSlots());
        assertEquals(Integer.valueOf(9), courtScheduleRepository.findBy("CS-NOOP-DAY2").getAvailableSlots());
        assertEquals(Integer.valueOf(9), courtScheduleRepository.findBy("CS-NOOP-DAY3").getAvailableSlots());
    }

    @Test
    public void saveBookedSlotsSkipsHearingWideReleaseWhenReleaseExistingHearingAllocationsIsFalse() {
        // SPRDT: ChangeCourtRoomForMultidayHearing regression guard. Booking day2 via the no-release
        // variant (releaseExistingHearingAllocations=false) must NOT wipe out day1's allocation for
        // the SAME hearingId — that hearing-wide wipe was the production bug (day1 vanished after
        // changing only days 2-3 of a 3-day hearing).
        final String hearingId = randomUUID().toString();
        final LocalDate day1 = LocalDate.of(2026, 8, 10);
        final LocalDate day2 = LocalDate.of(2026, 8, 11);

        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-NORELEASE-DAY1", day1));
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-NORELEASE-DAY2", day2));

        allocatedListingRepository.saveAndFlush(
                allocatedListingFor("AL-NORELEASE-DAY1", "BK-NORELEASE-DAY1", "CS-NORELEASE-DAY1", hearingId, day1));

        final AllocatedSlot day2Slot = allocatedSlotForBooking(hearingId, "CS-NORELEASE-DAY2", day2);

        final Result result = courtScheduleRepository.saveBookedSlots(
                new ArrayList<>(List.of(day2Slot)), false, false, false);

        assertTrue(result.isSuccess());

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();

        final List<AllocatedListing> remaining = allocatedListingRepository.findByHearingId(hearingId);
        assertEquals(2, remaining.size());
        final List<String> remainingCourtScheduleIds = remaining.stream().map(AllocatedListing::getCourtScheduleId).toList();
        assertTrue(remainingCourtScheduleIds.contains("CS-NORELEASE-DAY1"));
        assertTrue(remainingCourtScheduleIds.contains("CS-NORELEASE-DAY2"));
    }

    @Test
    public void saveBookedSlotsStillReleasesHearingWideByDefault() {
        // Regression guard for every OTHER caller of the 3-arg saveBookedSlots (crown/mags
        // search-and-book, move-to-past, etc.): the historical hearing-wide release on booking
        // must be UNCHANGED when releaseExistingHearingAllocations is not explicitly suppressed.
        final String hearingId = randomUUID().toString();
        final LocalDate day1 = LocalDate.of(2026, 8, 20);
        final LocalDate day2 = LocalDate.of(2026, 8, 21);

        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-RELEASE-DAY1", day1));
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-RELEASE-DAY2", day2));

        allocatedListingRepository.saveAndFlush(
                allocatedListingFor("AL-RELEASE-DAY1", "BK-RELEASE-DAY1", "CS-RELEASE-DAY1", hearingId, day1));

        final AllocatedSlot day2Slot = allocatedSlotForBooking(hearingId, "CS-RELEASE-DAY2", day2);

        final Result result = courtScheduleRepository.saveBookedSlots(
                new ArrayList<>(List.of(day2Slot)), false, false);

        assertTrue(result.isSuccess());

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();

        final List<AllocatedListing> remaining = allocatedListingRepository.findByHearingId(hearingId);
        assertEquals(1, remaining.size());
        assertEquals("CS-RELEASE-DAY2", remaining.get(0).getCourtScheduleId());
    }

    // -----------------------------------------------------------------------
    // Tests for releasing the bookingId-keyed reservation on confirmation
    // (Task 7, reserve-a-slot-courtscheduler)
    // -----------------------------------------------------------------------

    @Test
    public void shouldReleaseTheReservationWhenTheBookingIsConfirmed() {
        // Reservations are stored as an ordinary allocated_listings row whose hearing_id column
        // holds the minted bookingId (see ReservationService) — releaseOldAllocatedListings("BK-RES")
        // must find and release it through the full three-step release, restoring capacity.
        final LocalDate sessionDate = LocalDate.of(2026, 9, 9);
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-RES", sessionDate));

        allocatedListingRepository.saveAndFlush(
                reservationListingFor("AL-RES", "BK-RES", "CS-RES", "BK-RES", sessionDate));

        courtScheduleRepository.releaseOldAllocatedListings("BK-RES");

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();

        assertTrue(allocatedListingRepository.findByHearingId("BK-RES").isEmpty());
        assertEquals(Integer.valueOf(10), courtScheduleRepository.findBy("CS-RES").getAvailableSlots());
    }

    @Test
    public void shouldTolerateReleasingABookingWithNoReservation() {
        // Legacy magistrates drafts have only a provisional_booking row and nothing in
        // allocated_listings to release. If this threw, every pre-go-live draft would fail at share.
        courtScheduleRepository.releaseOldAllocatedListings("BK-DOES-NOT-EXIST");
        // no exception — no-op
    }

    @Test
    public void confirmingABookedSlotReleasesItsBookingIdKeyedReservationWithoutTouchingTheRealHearingId() {
        // Highest-risk case for Task 7: persistHearingSlots releases slots.get(0).getBookingId()
        // (the minted bookingId from the reservation) AFTER the new allocation has already been
        // saved under the real hearingId. Since the minted bookingId and the real hearingId are
        // different keys, releasing the bookingId must NOT touch the just-created confirmed booking.
        final String hearingId = randomUUID().toString();
        final String bookingId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2026, 9, 9);

        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-CONFIRM", sessionDate));

        // The hold taken at slot-pick time — keyed on bookingId, not hearingId, and carrying the
        // non-null expires_at that makes it a reservation rather than a confirmed booking.
        allocatedListingRepository.saveAndFlush(
                reservationListingFor("AL-CONFIRM-RES", bookingId, "CS-CONFIRM", bookingId, sessionDate));

        final AllocatedSlot slot = allocatedSlotForBooking(hearingId, "CS-CONFIRM", sessionDate);
        slot.setBookingId(bookingId);

        final Result result = courtScheduleRepository.saveBookedSlots(new ArrayList<>(List.of(slot)), true, false);

        assertTrue(result.isSuccess());

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();

        // The bookingId-keyed reservation is gone...
        assertTrue(allocatedListingRepository.findByHearingId(bookingId).isEmpty());
        // ...while the confirmed booking under the real hearingId survives, untouched.
        final List<AllocatedListing> confirmed = allocatedListingRepository.findByHearingId(hearingId);
        assertEquals(1, confirmed.size());
        assertEquals("CS-CONFIRM", confirmed.get(0).getCourtScheduleId());
    }

    /**
     * CRITICAL 1 regression guard, at the persistence level. A reservation keys every one of its
     * rows on the SAME minted bookingId (as hearing_id), and saveBookedSlots opens with a
     * hearing-wide releaseOldAllocatedListings on that key. Reserving slot by slot therefore made
     * each call release the previous slots of the same booking: a three-session pick came out
     * holding one session, and the other two sessions' capacity was handed back.
     *
     * <p>One saveBookedSlots call carrying all three slots must leave three allocations standing
     * and each of the three sessions decremented by exactly one.
     */
    @Test
    public void oneMultiSlotReserveHoldsEverySessionAndDecrementsEachExactlyOnce() {
        final String bookingId = randomUUID().toString();
        final LocalDate day1 = LocalDate.of(2026, 10, 12);
        final LocalDate day2 = LocalDate.of(2026, 10, 13);
        final LocalDate day3 = LocalDate.of(2026, 10, 14);

        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-MULTI-1", day1));
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-MULTI-2", day2));
        courtScheduleRepository.saveAndFlush(slotBasedCourtSchedule("CS-MULTI-3", day3));

        // Exactly what ReservationService.reserveAll builds: every slot keyed on the bookingId,
        // every slot stamped with a non-null expires_at, all handed over in ONE call.
        final List<AllocatedSlot> reservedSlots = new ArrayList<>(List.of(
                reservedSlotFor(bookingId, "CS-MULTI-1", day1),
                reservedSlotFor(bookingId, "CS-MULTI-2", day2),
                reservedSlotFor(bookingId, "CS-MULTI-3", day3)));

        final Result result = courtScheduleRepository.saveBookedSlots(reservedSlots, false, false);

        assertTrue(result.isSuccess());

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();

        final List<AllocatedListing> held = allocatedListingRepository.findByHearingId(bookingId);
        assertEquals(3, held.size());
        held.forEach(row -> assertEquals(LocalDate.now(ZoneOffset.UTC), row.getExpiresAt()));

        // slotBasedCourtSchedule seeds availableSlots = 9; each session must now be at 8 — one
        // decrement, no releases. Before the fix CS-MULTI-1 and CS-MULTI-2 went back to 9.
        assertEquals(Integer.valueOf(8), courtScheduleRepository.findBy("CS-MULTI-1").getAvailableSlots());
        assertEquals(Integer.valueOf(8), courtScheduleRepository.findBy("CS-MULTI-2").getAvailableSlots());
        assertEquals(Integer.valueOf(8), courtScheduleRepository.findBy("CS-MULTI-3").getAvailableSlots());
    }

    // -----------------------------------------------------------------------
    // BUG-3 Task 1: release the reservation on list, not just on confirm.
    // Listing (courtscheduler.list.hearings-in-sessions -> SlotsUpdateService.listHearingSlots ->
    // updateListHearingSlots) never called releaseOldAllocatedListings on the bookingId-keyed hold,
    // so the session was decremented twice (once by the hold, once by the listed booking) until the
    // 01:00 purge reclaimed one. These tests exercise updateListHearingSlots directly.
    // -----------------------------------------------------------------------

    @Test
    public void shouldReleaseTheReservationAndDecrementTheSessionExactlyOnceWhenListing() {
        final LocalDate sessionDate = LocalDate.of(2026, 9, 11);
        final CourtSchedule session = slotBasedCourtSchedule("CS-BUG3-LIST", sessionDate);
        session.setMaxSlots(4);
        session.setAvailableSlots(3); // the reservation already took one, off a max of 4
        courtScheduleRepository.saveAndFlush(session);

        // The hold taken at slot-pick time: hearing_id = bookingId, non-null expires_at.
        allocatedListingRepository.saveAndFlush(
                reservationListingFor("AL-BUG3-RES", "BK-BUG3-1", "CS-BUG3-LIST", "BK-BUG3-1", sessionDate));

        courtScheduleRepository.updateListHearingSlots(
                requestedSlotsFor("HEARING-BUG3-1", "BK-BUG3-1", "CS-BUG3-LIST"));

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();

        // reserve -1, list -1, release +1 => net -1 from the original 4
        assertEquals(Integer.valueOf(3), courtScheduleRepository.findBy("CS-BUG3-LIST").getAvailableSlots());
        assertTrue(allocatedListingRepository.findByHearingId("BK-BUG3-1").isEmpty());
        final List<AllocatedListing> confirmed = allocatedListingRepository.findByHearingId("HEARING-BUG3-1");
        assertEquals(1, confirmed.size());
        assertNull(confirmed.get(0).getExpiresAt());
        assertEquals("BK-BUG3-1", confirmed.get(0).getBookingId());
    }

    @Test
    public void shouldListNormallyWhenNoBookingIdIsSupplied() {
        // Regression net: every pre-go-live caller (Crown fallback, search-and-book, rota) never
        // sends a bookingId. This must keep working unchanged.
        final LocalDate sessionDate = LocalDate.of(2026, 9, 11);
        final CourtSchedule session = slotBasedCourtSchedule("CS-BUG3-NOBOOKING", sessionDate);
        session.setMaxSlots(4);
        session.setAvailableSlots(4);
        courtScheduleRepository.saveAndFlush(session);

        courtScheduleRepository.updateListHearingSlots(
                requestedSlotsFor("HEARING-BUG3-2", null, "CS-BUG3-NOBOOKING"));

        em.flush();
        em.clear();

        assertEquals(Integer.valueOf(3), courtScheduleRepository.findBy("CS-BUG3-NOBOOKING").getAvailableSlots());
        assertNull(allocatedListingRepository.findByHearingId("HEARING-BUG3-2").get(0).getBookingId());
    }

    @Test
    public void shouldTolerateABookingIdWithNoReservation() {
        // A pre-go-live magistrates draft: a bookingId is present but nothing was ever reserved
        // under it (only a provisional_booking row exists). Must be a no-op, not an error.
        final LocalDate sessionDate = LocalDate.of(2026, 9, 11);
        final CourtSchedule session = slotBasedCourtSchedule("CS-BUG3-LEGACY", sessionDate);
        session.setMaxSlots(4);
        session.setAvailableSlots(4);
        courtScheduleRepository.saveAndFlush(session);

        courtScheduleRepository.updateListHearingSlots(
                requestedSlotsFor("HEARING-BUG3-3", "BK-BUG3-LEGACY", "CS-BUG3-LEGACY"));

        em.flush();
        em.clear();

        assertEquals(Integer.valueOf(3), courtScheduleRepository.findBy("CS-BUG3-LEGACY").getAvailableSlots());
        assertEquals(1, allocatedListingRepository.findByHearingId("HEARING-BUG3-3").size());
    }

    // -----------------------------------------------------------------------
    // reserve-a-slot-new15: a re-pick under the SAME bookingId must release the abandoned
    // session's capacity and leave only the new pick's row standing.
    // -----------------------------------------------------------------------

    /**
     * This is the test that matters for reserve-a-slot-new15: the two mock-based service tests
     * only prove the id is threaded through; this one proves the actual capacity arithmetic of a
     * re-pick against a real persistence context. A reservation's hearing_id is its bookingId, and
     * saveBookedSlots opens with a hearing-wide releaseOldAllocatedListings(hearing_id) — so
     * reserving the second session under the SAME bookingId as the first must release the first
     * session's row before taking the second, restoring the first session's capacity and leaving
     * only one allocated_listings row for the bookingId.
     */
    @Test
    public void shouldReleaseThePreviousPickWhenReservingAgainUnderTheSameBookingId() {
        final LocalDate sessionDate = LocalDate.of(2026, 9, 20);
        final CourtSchedule first = slotBasedCourtSchedule("CS-REPICK-1", sessionDate);
        final CourtSchedule second = slotBasedCourtSchedule("CS-REPICK-2", sessionDate);
        courtScheduleRepository.saveAndFlush(first);
        courtScheduleRepository.saveAndFlush(second);
        final String bookingId = randomUUID().toString();

        courtScheduleRepository.saveBookedSlots(
                new ArrayList<>(List.of(reservedSlotFor(bookingId, "CS-REPICK-1", sessionDate))), false, false);

        // @DataJpaTest runs in one rollback tx: flush pending persists/removes before
        // clearing, else clear() silently discards them (ccsph2 rig committed per call).
        em.flush();
        em.clear();
        assertEquals(Integer.valueOf(8), courtScheduleRepository.findBy("CS-REPICK-1").getAvailableSlots());

        courtScheduleRepository.saveBookedSlots(
                new ArrayList<>(List.of(reservedSlotFor(bookingId, "CS-REPICK-2", sessionDate))), false, false);

        em.flush();
        em.clear();

        assertEquals(Integer.valueOf(9), courtScheduleRepository.findBy("CS-REPICK-1").getAvailableSlots(),
                "the abandoned session must get its slot back");
        assertEquals(Integer.valueOf(8), courtScheduleRepository.findBy("CS-REPICK-2").getAvailableSlots(),
                "the newly picked session must be held");
        assertEquals(1, allocatedListingRepository.findByHearingId(bookingId).size(),
                "only the new pick's row survives");
    }

    /** Builds a {@link RequestedSlots} with one {@link HearingSlot} carrying one court schedule. */
    private RequestedSlots requestedSlotsFor(final String hearingId, final String bookingId,
                                              final String courtScheduleId) {
        final RequestedCourtSchedule requestedCourtSchedule = new RequestedCourtSchedule();
        requestedCourtSchedule.setCourtScheduleId(courtScheduleId);

        final HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setHearingId(hearingId);
        hearingSlot.setBookingId(bookingId);
        hearingSlot.setCourtScheduleIds(new ArrayList<>(List.of(requestedCourtSchedule)));

        final RequestedSlots requestedSlots = new RequestedSlots();
        requestedSlots.setHearingSlots(new ArrayList<>(List.of(hearingSlot)));
        return requestedSlots;
    }

    private static AllocatedSlot reservedSlotFor(final String bookingId, final String courtScheduleId,
                                                 final LocalDate sessionDate) {
        final AllocatedSlot slot = allocatedSlotForBooking(bookingId, courtScheduleId, sessionDate);
        slot.setSource("RESERVED_UNCONFIRMED");
        slot.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        return slot;
    }

    private static AllocatedSlot allocatedSlotForBooking(final String hearingId, final String courtScheduleId,
                                                          final LocalDate sessionDate) {
        final AllocatedSlot slot = random(AllocatedSlot.class);
        slot.setHearingId(hearingId);
        slot.setCourtScheduleId(courtScheduleId);
        slot.setSessionDate(sessionDate.toString());
        slot.setHearingStartTime(SIMPLE_DATE_FORMAT.format(
                Date.from(sessionDate.atTime(10, 0).atZone(ZoneId.of("UTC")).toInstant())));
        slot.setSource("DEFAULT");
        return slot;
    }

    private CourtSchedule slotBasedCourtSchedule(final String courtScheduleId, final LocalDate sessionDate) {
        final CourtSchedule cs = random(CourtSchedule.class);
        cs.setCourtScheduleId(courtScheduleId);
        cs.setSessionDate(sessionDate);
        cs.setActive(true);
        cs.setSlotBased(true);
        cs.setMaxSlots(10);
        cs.setAvailableSlots(9);
        cs.setMaxDuration(0);
        cs.setAvailableDuration(0);
        return cs;
    }

    private AllocatedListing allocatedListingFor(final String id, final String bookingId, final String courtScheduleId,
                                                  final String hearingId, final LocalDate sessionDate) {
        final AllocatedListing allocatedListing = new AllocatedListing();
        allocatedListing.setId(id);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setCourtRoomId(1);
        allocatedListing.setHearingStartTime(Date.from(sessionDate.atTime(10, 0).atZone(ZoneId.of("UTC")).toInstant()));
        allocatedListing.setDuration(120);
        allocatedListing.setOucode("BA124");
        allocatedListing.setRotaBusinessType("BUSS");
        allocatedListing.setSource("DEFAULT");
        return allocatedListing;
    }

    /**
     * A reservation row: same shape as {@link #allocatedListingFor} but with the non-null
     * {@code expires_at} that is the actual discriminator between a reservation and a confirmed
     * booking. {@code source} is descriptive only and must never be relied on for the distinction,
     * so tests that seed a reservation set the expiry, not just the label.
     */
    private AllocatedListing reservationListingFor(final String id, final String bookingId, final String courtScheduleId,
                                                    final String hearingId, final LocalDate sessionDate) {
        final AllocatedListing reservation = allocatedListingFor(id, bookingId, courtScheduleId, hearingId, sessionDate);
        reservation.setSource("RESERVED_UNCONFIRMED");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        return reservation;
    }

    // -----------------------------------------------------------------------
    // Fixture helpers
    // -----------------------------------------------------------------------

    /** Persists a random schedule under the given id (FK parent for judiciary rows) and returns the id. */
    private String persistScheduleWithId(final String courtScheduleId) {
        final CourtSchedule cs = random(CourtSchedule.class);
        cs.setCourtScheduleId(courtScheduleId);
        courtScheduleRepository.save(cs);
        return courtScheduleId;
    }

    /**
     * SPRDT-1324: an auto-created AD session ends at the fixed all-day default (17:00 Europe/London)
     * whatever the requested hearing time is, rather than start + the session's 360-minute capacity.
     * A 12:30 request used to produce an 18:30 end time, which put the session past the court day.
     * The date is in British Summer Time so the test also proves the end is a London wall-clock
     * time (16:00 UTC), not 17:00 UTC.
     */
    @Test
    public void createCrownFallbackSessionShouldEndAtFivePmRegardlessOfStartTime() {
        final String ouCode = random(String.class);
        final String courtCentreId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final LocalDate sessionDate = LocalDate.of(2026, 8, 28);

        // The template only supplies metadata to copy; it sits on an earlier date because an active
        // session for the same room, business type and date would collide with the created one on the
        // AM/PM uniqueness index.
        final CourtSchedule template = createCourtSchedule(ouCode, "ADULT", sessionDate.minusDays(7), courtRoomId, "LNG", "AD");
        template.setCourtHouseId(courtCentreId);
        courtScheduleRepository.save(template);

        final CrownFallbackRequest request = new CrownFallbackRequest()
                .setHearingId(randomUUID().toString())
                .setCourtCentreId(courtCentreId)
                .setCourtRoomId(courtRoomId)
                .setHearingDate(sessionDate)
                .setEarliestHearingTime(sessionDate + "T12:30:00Z")
                .setDurationInMinutes(10)
                .setSource("CROWN_FB_LIST");

        final Optional<CrownFallbackSearchResult> created = courtScheduleRepository.createCrownFallbackSession(request);

        assertTrue(created.isPresent());
        final LocalTime startTime = created.get().session().getSessionStartTime().toInstant()
                .atZone(ZoneOffset.UTC).toLocalTime();
        final Instant sessionEnd = created.get().session().getSessionEndTime().toInstant();
        assertEquals(LocalTime.of(12, 30), startTime);
        // 17:00 is a Europe/London wall-clock time, like every other session time in the viewstore
        // (DateUtils.combineDateAndTime, TimezoneUtils.calculateNationalBreakTime). 2026-08-28 is in
        // BST, so the stored instant must be 16:00 UTC — 17:00 UTC would show as 18:00 on the calendar.
        assertEquals(sessionDate.atTime(17, 0), sessionEnd.atZone(LONDON_ZONE).toLocalDateTime());
        assertEquals(LocalTime.of(16, 0), sessionEnd.atZone(ZoneOffset.UTC).toLocalTime());
    }

    private CourtSchedule createCourtSchedule(final String ouCode, final String panel, final LocalDate sessionDate,
                                              final String courtRoomId, final String businessType,
                                              final String courtSession) {
        final CourtSchedule schedule = createCourtSchedule(ouCode, panel, sessionDate, courtRoomId, businessType);
        schedule.setCourtSession(courtSession);
        return schedule;
    }

    private CourtSchedule createCourtSchedule(final String ouCode, final String panel, final LocalDate sessionDate,
                                              final String courtRoomId, final String businessType) {
        CourtSchedule schedule = new CourtSchedule();
        schedule.setCourtScheduleId(UUID.randomUUID().toString());
        schedule.setSlotBased(false);
        schedule.setOuCode(ouCode);
        schedule.setCourtRoomId(courtRoomId);
        schedule.setBusinessType(businessType);
        schedule.setSessionDate(sessionDate);
        schedule.setCourtSession("AM");
        schedule.setActive(true);
        schedule.setCourtRoomNumber(1);
        schedule.setCourtHouseName("Test Court House");
        schedule.setCourtRoomName("Test Court Room " + courtRoomId);
        schedule.setOperationalUnit(ouCode);
        schedule.setPanel(panel);
        schedule.setMaxSlots(10);
        schedule.setMaxDuration(240);
        schedule.setAvailableSlots(10);
        schedule.setAvailableDuration(240);
        schedule.setCourtHouseId("CH" + ouCode);

        // Boolean fields with defaults
        schedule.setSupportAdSplit(false);
        schedule.setIsOverbookingAllowed(false);
        schedule.setIsDraft(false);

        // Numeric fields with defaults
        schedule.setMaxAdMorningDuration(0);
        schedule.setMaxAdAfternoonDuration(0);

        schedule.setJurisdiction("CROWN");

        // Timestamp fields
        LocalDateTime now = LocalDateTime.now();
        schedule.setCreatedOn(Timestamp.valueOf(now));
        schedule.setUpdatedOn(Timestamp.valueOf(now));

        // Session time fields (with time zone)
        LocalDateTime startDateTime = LocalDateTime.of(sessionDate, LocalTime.of(9, 0));
        LocalDateTime endDateTime = LocalDateTime.of(sessionDate, LocalTime.of(13, 0));
        schedule.setSessionStartTime(Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setSessionEndTime(Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        schedule.setListingProfileId(random(String.class));

        return schedule;
    }

    private CourtScheduleJudiciary createJudiciary(String scheduleId, String judiciaryId, String listingProfileId, boolean active) {
        CourtScheduleJudiciary j = new CourtScheduleJudiciary();
        j.setId(new CourtScheduleJudiciaryKey(scheduleId, judiciaryId));
        j.setCourtListingProfileId(listingProfileId);
        j.setTitle("Mr");
        j.setForenames("Test");
        j.setSurname("Judge");
        j.setEmail("test@court.gov.uk");
        j.setJudiciaryType("JUDGE");
        j.setBenchChairman(false);
        j.setDeputy(false);
        j.setActive(active);
        return j;
    }
}
