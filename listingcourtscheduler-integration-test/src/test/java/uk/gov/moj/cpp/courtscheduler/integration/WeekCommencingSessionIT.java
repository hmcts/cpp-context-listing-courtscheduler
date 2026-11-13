package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createObjectBuilder;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;
import static java.time.LocalDate.now;
import static java.time.temporal.TemporalAdjusters.next;
import static java.time.temporal.TemporalAdjusters.nextOrSame;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.DUPLICATE_SESSIONS;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_COURTROOM;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_MONDAY;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.io.StringReader;
import java.sql.SQLException;
import java.time.ZoneOffset;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

class WeekCommencingSessionIT extends AbstractIT {

    private static final String URL = "/session/weekCommencing";
    private static final String CONTENT_TYPE = "application/vnd.courtscheduler.create-week-commencing.sessions+json";
    private static final String GET_COURT_SCHEDULE_URL = "/courtschedule";
    private static final String GET_COURT_SCHEDULE_ACCEPT = "application/vnd.courtscheduler.get+json";
    private static final String COURT_CENTRE_ID = "785339c1-af71-3322-a55b-ba255e0db1c2";
    private static final String FIRST_LISTED_COURT_ROOM_ID = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
    private static final String DURATION_BASED_BUSINESS_TYPE = "TRL";
    private static final String OTHER_DURATION_BASED_BUSINESS_TYPE = "LGT";
    private static final String SLOT_BASED_BUSINESS_TYPE = "DVLA";
    private static final int CAPACITY_FIVE_DAYS = 1800;
    private static final int MINUTES_PER_DAY = 6 * 60;
    private static final int MAX_CAPACITY_IN_MINUTES = 999 * MINUTES_PER_DAY;

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
    void shouldReturnWeekCommencingSessionsWithTheFlagWhenGettingTheCourtSchedule() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final LocalDate ordinarySessionDate = startDate.with(next(TUESDAY));
        final String ordinarySessionId = seedOrdinaryDraftSession(ordinarySessionDate);

        final Map<String, Object> query = new HashMap<>();
        query.put("courtCentreId", COURT_CENTRE_ID);
        query.put("sessionStartDate", startDate.toString());
        query.put("sessionEndDate", endDate.toString());
        query.put("pageSize", "50");
        query.put("pageNumber", "1");
        try (Response response = getCommand(getRequestParams(GET_COURT_SCHEDULE_URL, GET_COURT_SCHEDULE_ACCEPT, USER_ID, query))) {
            final String body = response.readEntity(String.class);
            assertThat(body, response.getStatus(), is(OK.getStatusCode()));

            final List<JsonObject> sessions = Json.createReader(new StringReader(body)).readObject()
                    .getJsonArray("courtSchedules").getValuesAs(JsonObject.class).stream()
                    .flatMap(room -> room.getJsonArray("sessions").getValuesAs(JsonObject.class).stream())
                    .toList();
            final List<JsonObject> weekCommencing = sessions.stream()
                    .filter(session -> !session.getString("courtScheduleId").equals(ordinarySessionId)).toList();
            final JsonObject ordinary = sessions.stream()
                    .filter(session -> session.getString("courtScheduleId").equals(ordinarySessionId)).findFirst().orElseThrow();

            assertThat(weekCommencing.stream().map(session -> LocalDate.parse(session.getString("sessionDate"))).sorted().toList(),
                    is(mondaysBetween(startDate, endDate)));
            assertThat(weekCommencing.stream().map(session -> session.getBoolean("isWeekCommencing")).toList(), everyItem(is(true)));
            assertThat(weekCommencing.stream().map(session -> session.getBoolean("isDraft")).toList(), everyItem(is(true)));
            assertThat(ordinary.getBoolean("isWeekCommencing"), is(false));
            assertThat(ordinary.getBoolean("isDraft"), is(true));
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
    void shouldStoreTheMaximumCapacityOfNineHundredAndNinetyNineDaysOnEveryMondayOfAThreeYearRange() {
        final LocalDate threeYearsLater = startDate.plusYears(3);
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, threeYearsLater)
                .add("capacityInMinutes", MAX_CAPACITY_IN_MINUTES))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
            final List<CourtSchedule> rows = databaseReader.courtSchedules();
            assertThat(rows.stream().map(CourtSchedule::getSessionDate).toList(), is(mondaysBetween(startDate, threeYearsLater)));
            assertThat(rows.stream().map(CourtSchedule::getMaxDuration).toList(), everyItem(is(359_640)));
            assertThat(rows.stream().map(CourtSchedule::getAvailableDuration).toList(), everyItem(is(359_640)));
        }
    }

    @Test
    void shouldRejectCapacityAboveNineHundredAndNinetyNineDays() {
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate).add("capacityInMinutes", MAX_CAPACITY_IN_MINUTES + 1))) {
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
     * Two identical requests at the same time (e.g. a double click). There is no lock, as for ordinary
     * session create: the second either sees the first one's rows and gets the duplicate error, or
     * passes the check and its insert hits the unique index, so the batch save falls back to
     * updating the same rows. Either way only one row is stored per Monday.
     */
    @Test
    void shouldStoreOneRowPerMondayForTwoConcurrentIdenticalRequests() throws Exception {
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

            final List<Integer> statuses = List.of(first.get(60, SECONDS), second.get(60, SECONDS));
            assertThat(statuses, everyItem(oneOf(ACCEPTED.getStatusCode(), BAD_REQUEST.getStatusCode())));
            assertThat(statuses, hasItem(ACCEPTED.getStatusCode()));
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
                .add("capacityInMinutes", CAPACITY_FIVE_DAYS)
                .add("isOverbookingAllowed", true);
    }

    private String seedOrdinaryDraftSession(final LocalDate sessionDate) throws SQLException {
        final String id = UUID.randomUUID().toString();
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(id);
        session.setListingProfileId(UUID.randomUUID().toString());
        session.setOuCode("B01LY00");
        session.setCourtRoomId(FIRST_LISTED_COURT_ROOM_ID);
        session.setCourtRoomNumber(1);
        session.setCourtHouseId(COURT_CENTRE_ID);
        session.setCourtHouseName("Crown Court");
        session.setCourtRoomName("Courtroom 01");
        session.setOperationalUnit("B01LY00");
        session.setBusinessType(DURATION_BASED_BUSINESS_TYPE);
        session.setPanel("ADULT");
        session.setCourtSession("AD");
        session.setActive(true);
        session.setSlotBased(false);
        session.setSessionDate(sessionDate);
        session.setMaxSlots(0);
        session.setMaxDuration(360);
        session.setAvailableSlots(0);
        session.setAvailableDuration(360);
        session.setSupportAdSplit(false);
        session.setMaxAdMorningDuration(180);
        session.setMaxAdAfternoonDuration(180);
        session.setSessionStartTime(sessionDate.atTime(10, 0).toInstant(ZoneOffset.UTC));
        session.setSessionEndTime(sessionDate.atTime(17, 0).toInstant(ZoneOffset.UTC));
        session.setNationalBreakTime(sessionDate.atTime(13, 0).toInstant(ZoneOffset.UTC));
        session.setIsOverbookingAllowed(false);
        session.setIsDraft(true);
        session.setJurisdiction("CROWN");
        databaseSeeder.insertCourtSchedule(session);
        return id;
    }

    private static List<LocalDate> mondaysBetween(final LocalDate start, final LocalDate end) {
        final List<LocalDate> mondays = new ArrayList<>();
        for (LocalDate monday = start.with(nextOrSame(MONDAY)); !monday.isAfter(end); monday = monday.plusWeeks(1)) {
            mondays.add(monday);
        }
        return mondays;
    }
}
