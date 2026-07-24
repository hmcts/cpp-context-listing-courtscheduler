package uk.gov.moj.cpp.courtscheduler.integration.performance;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.integration.AbstractIT;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

/**
 * Performance integration test for the multi-day Crown {@code GET /hearingslots} path.
 *
 * <p>Reproduces the shape of the curl in the "hearingSlots multi-day Crown — performance
 * analysis &amp; fix plan" report:
 * jurisdiction=CROWN, panel=ADULT,YOUTH, courtSession=AD, duration=720 (daysNeeded=2),
 * over a 31-day window — with enough seeded rows that the discovery + re-hydrate path
 * introduced for fix #4, and the single-COUNT refactor from fix #1, do measurably less
 * work than the original "double-fetch every row" implementation.
 *
 * <p>Seed shape (all in a single ouCode so the request matches them all):
 * <ul>
 *   <li>20 "candidate" rooms — each with one CROWN AD session per weekday across the
 *       extended window. Half ADULT, half YOUTH so the IN clause has both values. These
 *       rooms have consecutive availability and therefore survive the discovery filter.</li>
 *   <li>10 "gapped" rooms — only every third weekday has a session, so the discovery
 *       check filters them out. Their rows never reach the expensive
 *       {@code LEFT JOIN allocated_listings + SUM(CASE WHEN)} aggregation under fix #4.</li>
 *   <li>~50 allocated_listings sprinkled across candidate rooms' first week, so the
 *       aggregation isn't a no-op.</li>
 * </ul>
 *
 * <p>Approximate row counts in the discovery window: ~560 court_schedule rows total,
 * ~480 of which get re-hydrated. Under the old code path the heavy aggregated query ran
 * twice over all ~560 rows (once unpaginated for count, once unpaginated again because
 * the multi-day branch forces pageSize=10000). Under the new code path the cheap COUNT
 * query runs once and the heavy aggregation runs once over ~480 rows.
 *
 * <p>This file lives under {@code integration/performance/} so it is excluded by the
 * default {@code listingcourtscheduler-integration-test} profile and runs only under
 * {@code performance-test}. Combine with {@code test-duration-tracking} to capture wall
 * time:
 *
 * <pre>
 * mvn -B verify -pl listingcourtscheduler-integration-test \
 *     -P performance-test,test-duration-tracking
 * </pre>
 */
class HearingSlotsMultidayCrownPerfIT extends AbstractIT {

    private static final String RELATIVE_URL = "/hearingslots";
    private static final String ACCEPT_TYPE = "application/vnd.courtscheduler.get.hearing.slots+json";

    // Seed shape — chosen so the discovery query has substantially less work to do under
    // the new code path than the old one, while keeping the test under the 60s timeout.
    private static final int NUM_CANDIDATE_ROOMS = 20;
    private static final int NUM_GAPPED_ROOMS = 10;
    private static final LocalDate WINDOW_START = LocalDate.of(2026, 5, 7);
    private static final LocalDate WINDOW_END = LocalDate.of(2026, 6, 7);
    // Mirror the service's weekendBuffer math: for daysNeeded=2, buffer = 2 * ((2/5)+1) = 2,
    // so the discovery window is end + (daysNeeded - 1) + weekendBuffer = end + 3 days.
    private static final LocalDate EXTENDED_END = WINDOW_END.plusDays(3);

    // ouCode chosen to be distinctive so the request's predicate matches only the rows we seed.
    // Must be <= 10 chars (court_schedule.oucode is VARCHAR(10)); follows the production pattern
    // of a leading letter, two-digit centre, two-letter mnemonic, two-digit suffix (e.g. C20CO00).
    private static final String OU_CODE = "C99PERF00";
    private static final String COURT_HOUSE_ID = "CH-PERF-CROWN";
    private static final String COURT_HOUSE_NAME = "Perf Crown Court Centre";
    private static final String BUSINESS_TYPE = "CRI";
    private static final String COURT_SESSION = "AD";
    private static final String DURATION_MINS = "720";  // 2-day hearing
    private static final int PER_DAY_DURATION = 360;

    @Test
    void multidayCrownSearchOverManyRoomsInExtendedWindow() throws Exception {
        // ---- seed ----
        final List<CourtSchedule> schedules = new ArrayList<>();
        final List<AllocatedListing> allocatedListings = new ArrayList<>();

        // Candidate rooms: weekday sessions across the full extended window
        for (int roomIx = 0; roomIx < NUM_CANDIDATE_ROOMS; roomIx++) {
            final String roomId = randomUUID().toString();
            final String panel = (roomIx % 2 == 0) ? "ADULT" : "YOUTH";
            for (LocalDate d = WINDOW_START; !d.isAfter(EXTENDED_END); d = d.plusDays(1)) {
                if (isWeekend(d)) {
                    continue;
                }
                final CourtSchedule cs = buildSchedule(roomId, roomIx + 1, panel, d);
                schedules.add(cs);

                // Sprinkle bookings across the first calendar week so the LEFT JOIN aggregate
                // in the original path (and the rehydrate aggregate in the new path) has
                // real work to do.
                if (!d.isAfter(WINDOW_START.plusDays(6)) && roomIx % 2 == 0) {
                    allocatedListings.add(buildAllocatedListing(cs, 60));
                }
            }
        }

        // Gapped rooms: sessions on every third weekday — no daysNeeded=2 run is possible,
        // so the discovery filter introduced by fix #4 prunes these rooms from the heavy
        // aggregation.
        for (int roomIx = 0; roomIx < NUM_GAPPED_ROOMS; roomIx++) {
            final String roomId = randomUUID().toString();
            int weekdayIx = 0;
            for (LocalDate d = WINDOW_START; !d.isAfter(EXTENDED_END); d = d.plusDays(1)) {
                if (isWeekend(d)) {
                    continue;
                }
                if (weekdayIx++ % 3 != 0) {
                    continue;
                }
                schedules.add(buildSchedule(roomId, NUM_CANDIDATE_ROOMS + roomIx + 1, "ADULT", d));
            }
        }

        databaseSeeder.insertCourtSchedulesBatch(schedules);
        if (!allocatedListings.isEmpty()) {
            databaseSeeder.insertAllocatedListingsBatch(allocatedListings);
        }

        // ---- request: mirror the curl from the perf report ----
        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("panel", "ADULT,YOUTH");
        queryParams.put("sessionStartDate", WINDOW_START.toString());
        queryParams.put("sessionEndDate", WINDOW_END.toString());
        queryParams.put("ouCode", OU_CODE);
        queryParams.put("courtSession", COURT_SESSION);
        queryParams.put("availableDurationMins", DURATION_MINS);
        queryParams.put("jurisdiction", "CROWN");
        queryParams.put("isSlotBased", "false");
        queryParams.put("status", "FINAL");
        queryParams.put("pageSize", "10");
        queryParams.put("pageNumber", "1");

        final RequestParams requestParams =
                getRequestParams(RELATIVE_URL, ACCEPT_TYPE, SYSTEM_USER_ID, queryParams);

        final ResponseData response = poll(requestParams).with()
                .timeout(60L, SECONDS)
                .pollInterval(100L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        // ---- assertions: correctness, not perf (perf is captured by test-duration-tracking) ----
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject body = stringToJsonObjectConverter.convert(response.getPayload());

        // We seeded NUM_CANDIDATE_ROOMS rooms with full weekday availability. Across a
        // 31-day window with daysNeeded=2, each candidate room yields many valid start
        // dates (every weekday whose next business day also has a session and capacity).
        // The exact number depends on which weekdays are bookended; we just assert the
        // result is non-trivial, which proves the discovery + rehydrate path returns
        // real data.
        assertThat("expected the multi-day Crown search to return at least one valid start;"
                        + " if zero, the discovery or rehydrate step likely lost rows",
                body.getInt("results"), greaterThan(0));
    }

    // ---- helpers ----

    private static boolean isWeekend(final LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private CourtSchedule buildSchedule(final String roomId, final int roomNumber,
                                        final String panel, final LocalDate sessionDate) {
        final CourtSchedule cs = random(CourtSchedule.class);
        cs.setCourtScheduleId(randomUUID().toString());
        cs.setSlotBased(false);
        cs.setOuCode(OU_CODE);
        cs.setCourtRoomId(roomId);
        cs.setCourtRoomNumber(roomNumber);
        cs.setCourtHouseId(COURT_HOUSE_ID);
        cs.setCourtHouseName(COURT_HOUSE_NAME);
        cs.setCourtRoomName("Court Room " + roomNumber);
        cs.setOperationalUnit(OU_CODE);
        cs.setBusinessType(BUSINESS_TYPE);
        cs.setPanel(panel);
        cs.setCourtSession(COURT_SESSION);
        cs.setActive(true);
        cs.setSessionDate(sessionDate);
        cs.setMaxSlots(0);
        cs.setMaxDuration(PER_DAY_DURATION);
        cs.setAvailableSlots(0);
        cs.setAvailableDuration(PER_DAY_DURATION);
        cs.setSupportAdSplit(false);
        cs.setMaxAdMorningDuration(0);
        cs.setMaxAdAfternoonDuration(0);
        cs.setIsOverbookingAllowed(false);
        cs.setIsDraft(false);
        cs.setJurisdiction("CROWN");
        cs.setSessionStartTime(combineDateAndTime(sessionDate, "09:00"));
        cs.setSessionEndTime(combineDateAndTime(sessionDate, "17:00"));
        cs.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        return cs;
    }

    private AllocatedListing buildAllocatedListing(final CourtSchedule cs, final int durationMins) {
        final AllocatedListing al = RANDOM.nextObject(AllocatedListing.class);
        al.setId(randomUUID().toString());
        al.setCourtScheduleId(cs.getCourtScheduleId());
        al.setOucode(cs.getOuCode());
        al.setCourtRoomId(cs.getCourtRoomNumber());
        al.setRotaBusinessType(cs.getBusinessType());
        al.setDuration(durationMins);
        al.setHearingStartTime(combineDateAndTime(cs.getSessionDate(), "10:00"));
        al.setBookingId(randomUUID().toString());
        al.setHearingId(randomUUID().toString());
        return al;
    }
}
