package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createObjectBuilder;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.time.LocalDate.now;
import static java.time.temporal.TemporalAdjusters.next;
import static java.time.temporal.TemporalAdjusters.nextOrSame;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.DUPLICATE_SESSIONS;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_COURTROOM;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_MONDAY;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

/**
 * SPRDT-1550: POST /session/weekCommencing creates one CROWN draft all-day row per Monday in the
 * range, in the court centre's default courtroom, and rejects a second week commencing session for
 * the same court centre, business type and Monday.
 *
 * <p>Uses the C01CR00 Crown court centre of referencedata.get.ou-courtrooms.json; the default room is
 * the first one listed (Courtroom 01). It has no organisation-unit stub, so sessions start at the
 * hardcoded all-day default.</p>
 */
class WeekCommencingSessionIT extends AbstractIT {

    private static final String URL = "/session/weekCommencing";
    private static final String CONTENT_TYPE = "application/vnd.courtscheduler.week-commencing.create+json";
    private static final String COURT_CENTRE_ID = "785339c1-af71-3322-a55b-ba255e0db1c2";
    private static final String FIRST_LISTED_COURT_ROOM_ID = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
    private static final String DURATION_BASED_BUSINESS_TYPE = "TRL";
    private static final String OTHER_DURATION_BASED_BUSINESS_TYPE = "LGT";
    private static final String SLOT_BASED_BUSINESS_TYPE = "DVLA";
    private static final int CAPACITY_FIVE_DAYS = 1800;

    private final LocalDate startDate = now().with(next(WEDNESDAY));
    private final LocalDate endDate = startDate.plusDays(20);

    @Test
    void shouldCreateOneDraftWeekCommencingRowPerMondayInTheDefaultCourtRoom() {
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
            final List<CourtSchedule> rows = databaseReader.courtSchedules();
            assertThat(rows.stream().map(CourtSchedule::getSessionDate).toList(), is(mondaysBetween(startDate, endDate)));
            assertThat(rows.stream().map(CourtSchedule::isWeekCommencing).toList(), everyItem(is(true)));
            assertThat(rows.stream().map(CourtSchedule::isDraft).toList(), everyItem(is(true)));
            assertThat(rows.stream().map(CourtSchedule::getCourtSession).toList(), everyItem(is("AD")));
            assertThat(rows.stream().map(CourtSchedule::getJurisdiction).toList(), everyItem(is("CROWN")));
            assertThat(rows.stream().map(CourtSchedule::getPanel).toList(), everyItem(is("ADULT")));
            assertThat(rows.stream().map(CourtSchedule::getCourtHouseId).toList(), everyItem(is(COURT_CENTRE_ID)));
            assertThat(rows.stream().map(CourtSchedule::getCourtRoomId).toList(), everyItem(is(FIRST_LISTED_COURT_ROOM_ID)));
            assertThat(rows.stream().map(CourtSchedule::getBusinessType).toList(), everyItem(is(DURATION_BASED_BUSINESS_TYPE)));
            assertThat(rows.stream().map(CourtSchedule::getMaxDuration).toList(), everyItem(is(CAPACITY_FIVE_DAYS)));
            assertThat(rows.stream().map(CourtSchedule::getAvailableDuration).toList(), everyItem(is(CAPACITY_FIVE_DAYS)));
            assertThat(rows.stream().map(CourtSchedule::isOverbookingAllowed).toList(), everyItem(is(true)));
        }
    }

    @Test
    void shouldRejectASecondWeekCommencingSessionForTheSameCentreBusinessTypeAndMonday() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final int rowsAfterFirst = databaseReader.courtSchedules().size();

        // overlaps only on the last Monday of the first request
        try (Response duplicate = create(request(DURATION_BASED_BUSINESS_TYPE, endDate.with(nextOrSame(MONDAY)).minusWeeks(1), endDate.plusWeeks(2)))) {
            assertThat(duplicate.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(duplicate.readEntity(String.class), containsString(DUPLICATE_SESSIONS));
            assertThat(databaseReader.courtSchedules(), hasSize(rowsAfterFirst));
        }
    }

    @Test
    void shouldAllowAWeekCommencingSessionForAnotherBusinessTypeOnTheSameMondays() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));

        try (Response response = create(request(OTHER_DURATION_BASED_BUSINESS_TYPE, startDate, endDate))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
            assertThat(databaseReader.courtSchedules(), hasSize(2 * mondaysBetween(startDate, endDate).size()));
        }
    }

    @Test
    void shouldRejectASlotBasedBusinessType() {
        try (Response response = create(request(SLOT_BASED_BUSINESS_TYPE, startDate, endDate))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED));
            assertThat(databaseReader.courtSchedules(), hasSize(0));
        }
    }

    @Test
    void shouldRejectCapacityAboveTwentyDays() {
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate).add("capacity", 7201))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(databaseReader.courtSchedules(), hasSize(0));
        }
    }

    @Test
    void shouldRejectAStartDateInThePast() {
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, now().minusDays(7), endDate))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(databaseReader.courtSchedules(), hasSize(0));
        }
    }

    @Test
    void shouldRefuseAUserWithoutCreateCourtSchedulePermission() {
        try (Response response = postCommand(URL, CONTENT_TYPE, IntegrationTestBase.DENIED_USER_ID,
                request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate).build().toString())) {
            assertThat(response.getStatus(), is(FORBIDDEN.getStatusCode()));
            assertThat(databaseReader.courtSchedules(), hasSize(0));
        }
    }

    @Test
    void shouldRejectADateRangeWithNoMonday() {
        final LocalDate tuesday = now().with(next(TUESDAY));
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, tuesday, tuesday.plusDays(5)))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(WEEK_COMMENCING_NO_MONDAY));
            assertThat(databaseReader.courtSchedules(), hasSize(0));
        }
    }

    @Test
    void shouldRejectACourtCentreWithNoCourtRoom() {
        final String unknownCourtCentreId = UUID.randomUUID().toString();
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate).add("courtCentreId", unknownCourtCentreId))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(WEEK_COMMENCING_NO_COURTROOM + unknownCourtCentreId));
            assertThat(databaseReader.courtSchedules(), hasSize(0));
        }
    }

    @Test
    void shouldRejectAMalformedDate() {
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate).add("startDate", "2027-13-45"))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(databaseReader.courtSchedules(), hasSize(0));
        }
    }

    /**
     * Two identical requests at the same time (e.g. a double click): whichever order they run in, the
     * advisory lock makes exactly one succeed and the other get the duplicate error, and only one row
     * is stored per Monday.
     */
    @Test
    void shouldCreateOnlyOneOfTwoConcurrentIdenticalRequests() throws Exception {
        final String body = request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate).build().toString();
        final CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            final Callable<Integer> post = () -> {
                go.await();
                try (Response response = postCommand(URL, CONTENT_TYPE, USER_ID, body)) {
                    return response.getStatus();
                }
            };
            final Future<Integer> first = pool.submit(post);
            final Future<Integer> second = pool.submit(post);
            go.countDown();

            assertThat(List.of(first.get(60, SECONDS), second.get(60, SECONDS)),
                    containsInAnyOrder(ACCEPTED.getStatusCode(), BAD_REQUEST.getStatusCode()));
            assertThat(databaseReader.courtSchedules().stream().map(CourtSchedule::getSessionDate).toList(),
                    is(mondaysBetween(startDate, endDate)));
        }
    }

    private void assertCreated(final JsonObjectBuilder body) {
        try (Response response = create(body)) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
        }
    }

    private Response create(final JsonObjectBuilder body) {
        return postCommand(URL, CONTENT_TYPE, USER_ID, body.build().toString());
    }

    private static JsonObjectBuilder request(final String businessType, final LocalDate start, final LocalDate end) {
        return createObjectBuilder()
                .add("courtCentreId", COURT_CENTRE_ID)
                .add("businessType", businessType)
                .add("startDate", start.toString())
                .add("endDate", end.toString())
                .add("capacity", CAPACITY_FIVE_DAYS)
                .add("isOverbookingAllowed", true);
    }

    private static List<LocalDate> mondaysBetween(final LocalDate start, final LocalDate end) {
        final List<LocalDate> mondays = new ArrayList<>();
        for (LocalDate monday = start.with(nextOrSame(MONDAY)); !monday.isAfter(end); monday = monday.plusWeeks(1)) {
            mondays.add(monday);
        }
        return mondays;
    }
}
