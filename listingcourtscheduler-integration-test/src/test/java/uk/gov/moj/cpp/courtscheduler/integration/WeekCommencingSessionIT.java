package uk.gov.moj.cpp.courtscheduler.integration;

import static jakarta.json.Json.createArrayBuilder;
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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.DUPLICATE_SESSIONS;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.MAX_DURATION_LESS_THAN_TOTAL_BOOKED;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_COURTROOM;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_MONDAY;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_SESSION_NOT_WC;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
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
import java.util.stream.Stream;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WeekCommencingSessionIT extends AbstractIT {

    private static final String URL = "/session/weekCommencing";
    private static final String CONTENT_TYPE = "application/vnd.courtscheduler.create-week-commencing.sessions+json";
    private static final String UPDATE_CONTENT_TYPE = "application/vnd.courtscheduler.update-week-commencing.session+json";
    private static final int UPDATED_CAPACITY = 2160;
    private static final String COURT_SCHEDULE_URL = "/courtschedule";
    private static final String GET_COURT_SCHEDULE_ACCEPT = "application/vnd.courtscheduler.get+json";
    private static final String CAPACITY_IN_MINUTES = "capacityInMinutes";
    private static final String IS_OVERBOOKING_ALLOWED = "isOverbookingAllowed";
    private static final String CROWN = "CROWN";
    private static final String ALL_DAY = "AD";
    private static final String COURT_CENTRE_ID_FIELD = "courtCentreId";
    private static final String ADULT = "ADULT";
    private static final String COURT_SCHEDULE_ID = "courtScheduleId";
    private static final String IS_WEEK_COMMENCING = "isWeekCommencing";
    private static final String SESSION_DATE = "sessionDate";
    private static final String HEARING_SLOTS_URL = "/hearingslots";
    private static final String GET_HEARING_SLOTS_ACCEPT = "application/vnd.courtscheduler.get.hearing.slots+json";
    private static final String GET_HEARING_IDS_ACCEPT = "application/vnd.courtscheduler.get.hearing.ids+json";
    private static final String COURT_CENTRE_ID = "785339c1-af71-3322-a55b-ba255e0db1c2";
    private static final String COURT_CENTRE_OUCODE = "B40IM00";
    private static final String FIRST_LISTED_COURT_ROOM_ID = "3fc02c0f-f92e-31da-9686-d626ac8ccdc3";
    private static final String CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.create+json";
    private static final String VALIDATE_URL = "/validate";
    private static final String VALIDATE_CREATE_CONTENT_TYPE = "application/vnd.courtscheduler.validate.create+json";
    private static final String DURATION_BASED_BUSINESS_TYPE = "TRL";
    private static final String OTHER_DURATION_BASED_BUSINESS_TYPE = "LGT";
    private static final String SLOT_BASED_BUSINESS_TYPE = "DVLA";
    private static final String BUSINESS_TYPE = "businessType";
    private static final String COURT_ROOM_ID = "courtRoomId";
    private static final String IS_DRAFT = "isDraft";
    private static final int CAPACITY_FIVE_DAYS = 1800;
    private static final int MINUTES_PER_DAY = 6 * 60;
    private static final int MAX_CAPACITY_IN_MINUTES = 999 * MINUTES_PER_DAY;
    private static final String DELETE_URL = "/courtschedule/delete";
    private static final String DELETE_CONTENT_TYPE = "application/vnd.courtscheduler.delete+json";
    private static final String SESSIONS = "sessions";
    private static final String ERROR = "error";
    private static final String TOTAL_BOOKED = "totalBooked";
    private static final int BOOKING_DURATION_MINUTES = 60;
    private static final String SOME_SESSIONS_CANNOT_BE_REMOVED = "Some sessions could not be removed. Please check again.";

    private final LocalDate startDate = now().with(next(WEDNESDAY));
    private final LocalDate endDate = startDate.plusDays(20);
    private final LocalDate nextMonday = startDate.with(next(MONDAY));

    @Test
    void shouldCreateOneDraftWeekCommencingRowPerMondayInTheDefaultCourtRoom() {
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
            final List<CourtSchedule> rows = databaseReader.courtSchedules();
            assertThat(rows.stream().map(CourtSchedule::getSessionDate).toList(), is(mondaysBetween(startDate, endDate)));
            assertThat(rows.stream().map(CourtSchedule::isWeekCommencing).toList(), everyItem(is(true)));
            assertThat(rows.stream().map(CourtSchedule::isDraft).toList(), everyItem(is(true)));
            assertThat(rows.stream().map(CourtSchedule::getCourtSession).toList(), everyItem(is(ALL_DAY)));
            assertThat(rows.stream().map(CourtSchedule::getJurisdiction).toList(), everyItem(is(CROWN)));
            assertThat(rows.stream().map(CourtSchedule::getPanel).toList(), everyItem(is(ADULT)));
            assertThat(rows.stream().map(CourtSchedule::getCourtHouseId).toList(), everyItem(is(COURT_CENTRE_ID)));
            assertThat(rows.stream().map(CourtSchedule::getCourtRoomId).toList(), everyItem(is(FIRST_LISTED_COURT_ROOM_ID)));
            assertThat(rows.stream().map(CourtSchedule::getBusinessType).toList(), everyItem(is(DURATION_BASED_BUSINESS_TYPE)));
            assertThat(rows.stream().map(CourtSchedule::getMaxDuration).toList(), everyItem(is(CAPACITY_FIVE_DAYS)));
            assertThat(rows.stream().map(CourtSchedule::getAvailableDuration).toList(), everyItem(is(CAPACITY_FIVE_DAYS)));
            assertThat(rows.stream().map(CourtSchedule::isOverbookingAllowed).toList(), everyItem(is(true)));
        }
    }

    @Test
    void shouldReturnBothKindsOfSessionFromTheCourtScheduleWithoutTheParam() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final LocalDate tuesday = startDate.with(next(TUESDAY));
        final String ordinarySessionId = seedOrdinaryDraftSession(tuesday);

        final List<String> dateAndKind = courtScheduleSessions(null).stream()
                .map(session -> session.getString(SESSION_DATE) + " "
                        + (session.getBoolean(IS_WEEK_COMMENCING) ? "WC" : session.getString(COURT_SCHEDULE_ID)))
                .sorted().toList();

        final List<String> expected = Stream.concat(
                        mondaysBetween(startDate, endDate).stream().map(monday -> monday + " WC"),
                        Stream.of(tuesday + " " + ordinarySessionId))
                .sorted().toList();
        assertThat(dateAndKind, is(expected));
    }

    @Test
    void shouldReturnOnlyNonWeekCommencingSessionsFromTheCourtScheduleWhenTheParamIsFalse() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final String ordinarySessionId = seedOrdinaryDraftSession(startDate.with(next(TUESDAY)));

        final List<JsonObject> sessions = courtScheduleSessions(false);

        assertThat(sessions.stream().map(session -> session.getString(COURT_SCHEDULE_ID)).toList(), is(List.of(ordinarySessionId)));
        assertThat(sessions.getFirst().getBoolean(IS_WEEK_COMMENCING), is(false));
        assertThat(sessions.getFirst().getBoolean(IS_DRAFT), is(true));
    }

    @Test
    void shouldReturnOnlyWeekCommencingSessionsFromTheCourtScheduleWithTheParam() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final String ordinarySessionId = seedOrdinaryDraftSession(startDate.with(next(TUESDAY)));

        final List<JsonObject> sessions = courtScheduleSessions(true);

        assertThat(sessions.stream().map(session -> session.getString(COURT_SCHEDULE_ID)).toList(),
                everyItem(not(ordinarySessionId)));
        assertThat(sessions.stream().map(session -> LocalDate.parse(session.getString(SESSION_DATE))).sorted().toList(),
                is(mondaysBetween(startDate, endDate)));
        assertThat(sessions.stream().map(session -> session.getBoolean(IS_WEEK_COMMENCING)).toList(), everyItem(is(true)));
        assertThat(sessions.stream().map(session -> session.getBoolean(IS_DRAFT)).toList(), everyItem(is(true)));
    }

    @Test
    void shouldReturnOnlyNonWeekCommencingHearingSlotsWithoutTheParam() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final String ordinarySessionId = seedOrdinaryDraftSession(startDate.with(next(TUESDAY)));

        for (final Boolean isWeekCommencing : new Boolean[]{null, false}) {
            final JsonObject body = hearingSlots(isWeekCommencing);
            final List<JsonObject> slots = body.getJsonArray("hearingSlots").getValuesAs(JsonObject.class);

            assertThat(body.getInt("results"), is(1));
            assertThat(slots.stream().map(slot -> slot.getString(COURT_SCHEDULE_ID)).toList(), is(List.of(ordinarySessionId)));
            assertThat(slots.getFirst().getBoolean(IS_WEEK_COMMENCING), is(false));
        }
    }

    @Test
    void shouldReturnOnlyWeekCommencingHearingSlotsWithTheParam() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final String ordinarySessionId = seedOrdinaryDraftSession(startDate.with(next(TUESDAY)));

        final JsonObject body = hearingSlots(true);
        final List<JsonObject> slots = body.getJsonArray("hearingSlots").getValuesAs(JsonObject.class);

        assertThat(body.getInt("results"), is(mondaysBetween(startDate, endDate).size()));
        assertThat(slots.stream().map(slot -> slot.getString(COURT_SCHEDULE_ID)).toList(), everyItem(not(ordinarySessionId)));
        assertThat(slots.stream().map(slot -> LocalDate.parse(slot.getString(SESSION_DATE))).sorted().toList(),
                is(mondaysBetween(startDate, endDate)));
        assertThat(slots.stream().map(slot -> slot.getBoolean(IS_WEEK_COMMENCING)).toList(), everyItem(is(true)));
    }

    // A CROWN search longer than a day would normally need consecutive days; week commencing sessions are single
    // Mondays holding several days of capacity, so the search must still find them.
    @Test
    void shouldFindWeekCommencingHearingSlotsForAHearingLongerThanADay() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final Map<String, Object> query = hearingSlotsQuery(true);
        query.put("availableDurationMins", String.valueOf(3 * MINUTES_PER_DAY));

        try (Response response = getCommand(getRequestParams(HEARING_SLOTS_URL, GET_HEARING_SLOTS_ACCEPT, SYSTEM_USER_ID, query))) {
            final String body = response.readEntity(String.class);
            assertThat(body, response.getStatus(), is(OK.getStatusCode()));
            final List<JsonObject> slots = Json.createReader(new StringReader(body)).readObject()
                    .getJsonArray("hearingSlots").getValuesAs(JsonObject.class);
            assertThat(slots.stream().map(slot -> LocalDate.parse(slot.getString(SESSION_DATE))).sorted().toList(),
                    is(mondaysBetween(startDate, endDate)));
            assertThat(slots.stream().map(slot -> slot.getBoolean(IS_WEEK_COMMENCING)).toList(), everyItem(is(true)));
        }
    }

    @Test
    void hearingIdsShouldStillIncludeAHearingBookedIntoAWeekCommencingSession() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule weekCommencing = databaseReader.courtSchedules().getFirst();
        final String hearingId = UUID.randomUUID().toString();
        final AllocatedListing booking = new AllocatedListing();
        booking.setId(UUID.randomUUID().toString());
        booking.setBookingId(UUID.randomUUID().toString());
        booking.setCourtScheduleId(weekCommencing.getCourtScheduleId());
        booking.setHearingId(hearingId);
        booking.setCourtRoomId(1);
        booking.setHearingStartTime(weekCommencing.getSessionDate().atTime(10, 0).toInstant(ZoneOffset.UTC));
        booking.setDuration(MINUTES_PER_DAY);
        booking.setOucode(weekCommencing.getOuCode());
        booking.setRotaBusinessType(DURATION_BASED_BUSINESS_TYPE);
        databaseSeeder.insertAllocatedListing(booking);

        // hearing ids ignore the param: the same hearing comes back whatever is sent
        assertThat(hearingIds(null), is(List.of(hearingId)));
        assertThat(hearingIds(true), is(List.of(hearingId)));
        assertThat(hearingIds(false), is(List.of(hearingId)));
    }

    private List<String> hearingIds(final Boolean isWeekCommencing) {
        try (Response response = getCommand(getRequestParams(HEARING_SLOTS_URL, GET_HEARING_IDS_ACCEPT, SYSTEM_USER_ID,
                hearingSlotsQuery(isWeekCommencing)))) {
            final String body = response.readEntity(String.class);
            assertThat(body, response.getStatus(), is(OK.getStatusCode()));
            return Json.createReader(new StringReader(body)).readObject()
                    .getJsonArray("hearingIds").getValuesAs(JsonObject.class).stream()
                    .map(id -> id.getString("hearingId")).toList();
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
                .add(CAPACITY_IN_MINUTES, MAX_CAPACITY_IN_MINUTES))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
            final List<CourtSchedule> rows = databaseReader.courtSchedules();
            assertThat(rows.stream().map(CourtSchedule::getSessionDate).toList(), is(mondaysBetween(startDate, threeYearsLater)));
            assertThat(rows.stream().map(CourtSchedule::getMaxDuration).toList(), everyItem(is(359_640)));
            assertThat(rows.stream().map(CourtSchedule::getAvailableDuration).toList(), everyItem(is(359_640)));
        }
    }

    @Test
    void shouldRejectCapacityAboveNineHundredAndNinetyNineDays() {
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate).add(CAPACITY_IN_MINUTES, MAX_CAPACITY_IN_MINUTES + 1))) {
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
        try (Response response = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate).add(COURT_CENTRE_ID_FIELD, unknownCourtCentreId))) {
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldHoldAnAssignedADraftAndAWeekCommencingSessionForTheSameRoomBusinessTypeAndMonday(final boolean weekCommencingFirst) {
        if (weekCommencingFirst) {
            assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        }
        assertOrdinarySessionValidatedAndCreated(true);
        assertOrdinarySessionValidatedAndCreated(false);
        if (!weekCommencingFirst) {
            assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        }

        final List<CourtSchedule> rows = databaseReader.courtSchedules();
        final List<String> expected = mondaysBetween(startDate, endDate).stream()
                .flatMap(monday -> Stream.of(monday + " assigned", monday + " draft", monday + " draft week commencing"))
                .toList();
        assertThat(rows.stream().map(WeekCommencingSessionIT::dateAndKind).sorted().toList(), is(expected));
        assertThat(rows.stream().map(CourtSchedule::getCourtRoomId).toList(), everyItem(is(FIRST_LISTED_COURT_ROOM_ID)));
        assertThat(rows.stream().map(CourtSchedule::getBusinessType).toList(), everyItem(is(DURATION_BASED_BUSINESS_TYPE)));
        assertThat(rows.stream().map(CourtSchedule::getCourtSession).toList(), everyItem(is(ALL_DAY)));
        assertThat(rows.stream().map(CourtSchedule::isActive).toList(), everyItem(is(true)));

        for (final boolean isDraft : List.of(true, false)) {
            try (Response duplicate = validateOrdinarySession(isDraft)) {
                assertThat(duplicate.getStatus(), is(BAD_REQUEST.getStatusCode()));
                assertThat(duplicate.readEntity(String.class), containsString(DUPLICATE_SESSIONS));
            }
        }
        try (Response duplicate = create(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate))) {
            assertThat(duplicate.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(duplicate.readEntity(String.class), containsString(DUPLICATE_SESSIONS));
        }
        assertThat(databaseReader.courtSchedules().stream().map(WeekCommencingSessionIT::dateAndKind).sorted().toList(), is(expected));
    }

    private static String dateAndKind(final CourtSchedule row) {
        return row.getSessionDate() + (row.isDraft() ? " draft" : " assigned") + (row.isWeekCommencing() ? " week commencing" : "");
    }

    private void assertOrdinarySessionValidatedAndCreated(final boolean isDraft) {
        try (Response response = validateOrdinarySession(isDraft)) {
            final String body = response.readEntity(String.class);
            assertThat(body, response.getStatus(), is(OK.getStatusCode()));
            assertThat(body, is("{}"));
        }
        final String payload = createObjectBuilder()
                .add(SESSIONS, createArrayBuilder().add(ordinarySession(isDraft)))
                .add("repeatPattern", everyWeek())
                .build().toString();
        try (Response response = postCommand(COURT_SCHEDULE_URL, CREATE_CONTENT_TYPE, USER_ID, payload)) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
        }
    }

    private Response validateOrdinarySession(final boolean isDraft) {
        final String payload = createObjectBuilder()
                .add(SESSIONS, createArrayBuilder())
                .add("sessionToBeAdded", ordinarySession(isDraft))
                .add("repeatPattern", everyWeek())
                .build().toString();
        return postCommand(VALIDATE_URL, VALIDATE_CREATE_CONTENT_TYPE, USER_ID, payload);
    }

    private static JsonObjectBuilder ordinarySession(final boolean isDraft) {
        return createObjectBuilder()
                .add(COURT_CENTRE_ID_FIELD, COURT_CENTRE_ID)
                .add(COURT_ROOM_ID, FIRST_LISTED_COURT_ROOM_ID)
                .add("jurisdiction", CROWN)
                .add("sessionType", ALL_DAY)
                .add(BUSINESS_TYPE, DURATION_BASED_BUSINESS_TYPE)
                .add("duration", MINUTES_PER_DAY)
                .add("allDaySplit", false)
                .add("panel", ADULT)
                .add(IS_OVERBOOKING_ALLOWED, true)
                .add("repeatDays", createArrayBuilder().add("Monday"))
                .add(IS_DRAFT, isDraft);
    }

    private JsonObjectBuilder everyWeek() {
        return createObjectBuilder()
                .add("startDate", startDate.toString())
                .add("endDate", endDate.toString())
                .add("repeatFor", 1)
                .add("frequency", "EVERY_WEEK");
    }

    @Test
    void shouldUpdateCapacityAndAvailableDurationForAWeekCommencingSession() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule first = databaseReader.courtSchedules().getFirst();

        try (Response response = update(updateRequest(first.getCourtScheduleId(), DURATION_BASED_BUSINESS_TYPE, UPDATED_CAPACITY, true))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
        }

        final CourtSchedule updated = databaseReader.courtSchedules().stream()
                .filter(cs -> cs.getCourtScheduleId().equals(first.getCourtScheduleId()))
                .findFirst().orElseThrow();
        assertThat(updated.getMaxDuration(), is(UPDATED_CAPACITY));
        assertThat(updated.getAvailableDuration(), is(UPDATED_CAPACITY));
    }

    @Test
    void shouldUpdateBusinessTypeForAWeekCommencingSession() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule first = databaseReader.courtSchedules().getFirst();

        try (Response response = update(updateRequest(first.getCourtScheduleId(), OTHER_DURATION_BASED_BUSINESS_TYPE, CAPACITY_FIVE_DAYS, false))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
        }

        final CourtSchedule updated = databaseReader.courtSchedules().stream()
                .filter(cs -> cs.getCourtScheduleId().equals(first.getCourtScheduleId()))
                .findFirst().orElseThrow();
        assertThat(updated.getBusinessType(), is(OTHER_DURATION_BASED_BUSINESS_TYPE));
    }

    @Test
    void shouldUpdateOverbookingAllowedForAWeekCommencingSession() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule first = databaseReader.courtSchedules().getFirst();
        assertThat(first.isOverbookingAllowed(), is(true));

        try (Response response = update(updateRequest(first.getCourtScheduleId(), DURATION_BASED_BUSINESS_TYPE, CAPACITY_FIVE_DAYS, false))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
        }

        final CourtSchedule updated = databaseReader.courtSchedules().stream()
                .filter(cs -> cs.getCourtScheduleId().equals(first.getCourtScheduleId()))
                .findFirst().orElseThrow();
        assertThat(updated.isOverbookingAllowed(), is(false));
    }

    @Test
    void shouldPreserveImmutableFieldsOnUpdate() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule before = databaseReader.courtSchedules().getFirst();

        try (Response response = update(updateRequest(before.getCourtScheduleId(), OTHER_DURATION_BASED_BUSINESS_TYPE, UPDATED_CAPACITY, false))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
        }

        final CourtSchedule after = databaseReader.courtSchedules().stream()
                .filter(cs -> cs.getCourtScheduleId().equals(before.getCourtScheduleId()))
                .findFirst().orElseThrow();
        assertThat(after.getCourtRoomId(), is(before.getCourtRoomId()));
        assertThat(after.getCourtSession(), is(before.getCourtSession()));
        assertThat(after.getPanel(), is(before.getPanel()));
        assertThat(after.getJurisdiction(), is(before.getJurisdiction()));
        assertThat(after.getSessionDate(), is(before.getSessionDate()));
        assertThat(after.getCourtHouseId(), is(before.getCourtHouseId()));
        assertThat(after.isDraft(), is(before.isDraft()));
        assertThat(after.isWeekCommencing(), is(before.isWeekCommencing()));
    }

    @Test
    void shouldRecalculateAvailableDurationWithExistingBookings() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule first = databaseReader.courtSchedules().getFirst();
        seedBooking(first, MINUTES_PER_DAY);

        try (Response response = update(updateRequest(first.getCourtScheduleId(), DURATION_BASED_BUSINESS_TYPE, UPDATED_CAPACITY, true))) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
        }

        final CourtSchedule updated = databaseReader.courtSchedules().stream()
                .filter(cs -> cs.getCourtScheduleId().equals(first.getCourtScheduleId()))
                .findFirst().orElseThrow();
        assertThat(updated.getMaxDuration(), is(UPDATED_CAPACITY));
        assertThat(updated.getAvailableDuration(), is(UPDATED_CAPACITY - MINUTES_PER_DAY));
    }

    @Test
    void shouldRejectUpdateWhenCapacityLessThanTotalBooked() throws SQLException {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule first = databaseReader.courtSchedules().getFirst();
        seedBooking(first, MINUTES_PER_DAY);

        try (Response response = update(updateRequest(first.getCourtScheduleId(), DURATION_BASED_BUSINESS_TYPE, MINUTES_PER_DAY / 2, true))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(MAX_DURATION_LESS_THAN_TOTAL_BOOKED));
        }
    }

    @Test
    void shouldRejectUpdateOfNonWeekCommencingSession() throws SQLException {
        final String ordinarySessionId = seedOrdinaryDraftSession(startDate.with(next(TUESDAY)), COURT_CENTRE_OUCODE);

        try (Response response = update(updateRequest(ordinarySessionId, DURATION_BASED_BUSINESS_TYPE, UPDATED_CAPACITY, false))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(WEEK_COMMENCING_SESSION_NOT_WC));
        }
    }

    @Test
    void shouldRejectUpdateWithSlotBasedBusinessType() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule first = databaseReader.courtSchedules().getFirst();

        try (Response response = update(updateRequest(first.getCourtScheduleId(), SLOT_BASED_BUSINESS_TYPE, CAPACITY_FIVE_DAYS, false))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED));
        }
    }

    @Test
    void shouldDefaultOverbookingToFalseWhenUpdateRequestOmitsTheField() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule first = databaseReader.courtSchedules().getFirst();
        assertThat(first.isOverbookingAllowed(), is(true));

        final String payloadWithoutOverbooking = createObjectBuilder()
                .add(COURT_SCHEDULE_ID, first.getCourtScheduleId())
                .add(BUSINESS_TYPE, DURATION_BASED_BUSINESS_TYPE)
                .add(CAPACITY_IN_MINUTES, UPDATED_CAPACITY)
                .build().toString();
        try (Response response = putCommand(URL, UPDATE_CONTENT_TYPE, USER_ID, payloadWithoutOverbooking)) {
            assertThat(response.readEntity(String.class), response.getStatus(), is(ACCEPTED.getStatusCode()));
        }

        final CourtSchedule updated = databaseReader.courtSchedules().stream()
                .filter(cs -> cs.getCourtScheduleId().equals(first.getCourtScheduleId()))
                .findFirst().orElseThrow();
        assertThat(updated.isOverbookingAllowed(), is(false));
    }

    @Test
    void shouldRejectUpdateWhenChangingToABusinessTypeThatAlreadyHasAWeekCommencingSessionOnTheSameMonday() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        assertCreated(request(OTHER_DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule trlSession = databaseReader.courtSchedules().stream()
                .filter(cs -> DURATION_BASED_BUSINESS_TYPE.equals(cs.getBusinessType()))
                .findFirst().orElseThrow();

        try (Response response = update(updateRequest(trlSession.getCourtScheduleId(), OTHER_DURATION_BASED_BUSINESS_TYPE, CAPACITY_FIVE_DAYS, false))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(DUPLICATE_SESSIONS));
        }
    }

    @Test
    void shouldRejectUpdateForNonExistentSession() {
        try (Response response = update(updateRequest(UUID.randomUUID().toString(), DURATION_BASED_BUSINESS_TYPE, UPDATED_CAPACITY, false))) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(SESSION_NOT_FOUND));
        }
    }

    @Test
    void shouldRejectUpdateWithMissingRequiredField() {
        final String missingBusinessType = createObjectBuilder()
                .add(COURT_SCHEDULE_ID, UUID.randomUUID().toString())
                .add(CAPACITY_IN_MINUTES, CAPACITY_FIVE_DAYS)
                .add(IS_OVERBOOKING_ALLOWED, false)
                .build().toString();
        try (Response response = putCommand(URL, UPDATE_CONTENT_TYPE, USER_ID, missingBusinessType)) {
            assertThat(response.getStatus(), is(BAD_REQUEST.getStatusCode()));
            assertThat(response.readEntity(String.class), containsString(BUSINESS_TYPE));
        }
    }

    @Test
    void shouldRefuseAUserWithoutCreateCourtSchedulePermissionOnWeekCommencingUpdate() {
        assertCreated(request(DURATION_BASED_BUSINESS_TYPE, startDate, endDate));
        final CourtSchedule first = databaseReader.courtSchedules().getFirst();

        try (Response response = putCommand(URL, UPDATE_CONTENT_TYPE, IntegrationTestBase.DENIED_USER_ID,
                updateRequest(first.getCourtScheduleId(), DURATION_BASED_BUSINESS_TYPE, UPDATED_CAPACITY, false).build().toString())) {
            assertThat(response.getStatus(), is(FORBIDDEN.getStatusCode()));
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

    private Response update(final JsonObjectBuilder body) {
        return putCommand(URL, UPDATE_CONTENT_TYPE, USER_ID, body.build().toString());
    }

    private static JsonObjectBuilder updateRequest(final String courtScheduleId, final String businessType,
                                                   final int capacityInMinutes, final boolean isOverbookingAllowed) {
        return createObjectBuilder()
                .add(COURT_SCHEDULE_ID, courtScheduleId)
                .add(BUSINESS_TYPE, businessType)
                .add(CAPACITY_IN_MINUTES, capacityInMinutes)
                .add(IS_OVERBOOKING_ALLOWED, isOverbookingAllowed);
    }

    private static JsonObjectBuilder request(final String businessType, final LocalDate start, final LocalDate end) {
        return createObjectBuilder()
                .add(COURT_CENTRE_ID_FIELD, COURT_CENTRE_ID)
                .add(BUSINESS_TYPE, businessType)
                .add("startDate", start.toString())
                .add("endDate", end.toString())
                .add(CAPACITY_IN_MINUTES, CAPACITY_FIVE_DAYS)
                .add(IS_OVERBOOKING_ALLOWED, true);
    }

    private List<JsonObject> courtScheduleSessions(final Boolean isWeekCommencing) {
        final Map<String, Object> query = new HashMap<>();
        query.put(COURT_CENTRE_ID_FIELD, COURT_CENTRE_ID);
        query.put("sessionStartDate", startDate.toString());
        query.put("sessionEndDate", endDate.toString());
        query.put("pageSize", "50");
        query.put("pageNumber", "1");
        if (isWeekCommencing != null) {
            query.put(IS_WEEK_COMMENCING, isWeekCommencing.toString());
        }
        try (Response response = getCommand(getRequestParams(COURT_SCHEDULE_URL, GET_COURT_SCHEDULE_ACCEPT, USER_ID, query))) {
            final String body = response.readEntity(String.class);
            assertThat(body, response.getStatus(), is(OK.getStatusCode()));
            return Json.createReader(new StringReader(body)).readObject()
                    .getJsonArray("courtSchedules").getValuesAs(JsonObject.class).stream()
                    .flatMap(room -> room.getJsonArray(SESSIONS).getValuesAs(JsonObject.class).stream())
                    .toList();
        }
    }

    private JsonObject hearingSlots(final Boolean isWeekCommencing) {
        try (Response response = getCommand(getRequestParams(HEARING_SLOTS_URL, GET_HEARING_SLOTS_ACCEPT, SYSTEM_USER_ID,
                hearingSlotsQuery(isWeekCommencing)))) {
            final String body = response.readEntity(String.class);
            assertThat(body, response.getStatus(), is(OK.getStatusCode()));
            return Json.createReader(new StringReader(body)).readObject();
        }
    }

    private Map<String, Object> hearingSlotsQuery(final Boolean isWeekCommencing) {
        final Map<String, Object> query = new HashMap<>();
        query.put("panel", ADULT);
        query.put("sessionStartDate", startDate.toString());
        query.put("sessionEndDate", endDate.toString());
        query.put("ouCode", weekCommencingOuCode());
        query.put(COURT_ROOM_ID, FIRST_LISTED_COURT_ROOM_ID);
        query.put("jurisdiction", CROWN);
        query.put("showOverbookedSlots", "true");
        query.put("pageSize", "50");
        query.put("pageNumber", "1");
        if (isWeekCommencing != null) {
            query.put(IS_WEEK_COMMENCING, isWeekCommencing.toString());
        }
        return query;
    }

    // Hearing slots and hearing ids need an ou code. The week commencing rows take theirs from reference data,
    // and the seeded ordinary session uses the same one so both kinds match the same search.
    private String weekCommencingOuCode() {
        return databaseReader.courtSchedules().stream().filter(CourtSchedule::isWeekCommencing)
                .map(CourtSchedule::getOuCode).findFirst().orElseThrow();
    }

    private String seedOrdinaryDraftSession(final LocalDate sessionDate) throws SQLException {
        return seedOrdinaryDraftSession(sessionDate, weekCommencingOuCode());
    }

    private String seedOrdinaryDraftSession(final LocalDate sessionDate, final String ouCode) throws SQLException {
        final String id = UUID.randomUUID().toString();
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(id);
        session.setListingProfileId(UUID.randomUUID().toString());
        session.setOuCode(ouCode);
        session.setCourtRoomId(FIRST_LISTED_COURT_ROOM_ID);
        session.setCourtRoomNumber(1);
        session.setCourtHouseId(COURT_CENTRE_ID);
        session.setCourtHouseName("Crown Court");
        session.setCourtRoomName("Courtroom 01");
        session.setOperationalUnit("B01LY00");
        session.setBusinessType(DURATION_BASED_BUSINESS_TYPE);
        session.setPanel(ADULT);
        session.setCourtSession(ALL_DAY);
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
        session.setJurisdiction(CROWN);
        databaseSeeder.insertCourtSchedule(session);
        return id;
    }

    private void seedBooking(final CourtSchedule session, final int durationMinutes) throws SQLException {
        final AllocatedListing booking = new AllocatedListing();
        booking.setId(UUID.randomUUID().toString());
        booking.setBookingId(UUID.randomUUID().toString());
        booking.setCourtScheduleId(session.getCourtScheduleId());
        booking.setHearingId(UUID.randomUUID().toString());
        booking.setCourtRoomId(1);
        booking.setHearingStartTime(session.getSessionDate().atTime(10, 0).toInstant(ZoneOffset.UTC));
        booking.setDuration(durationMinutes);
        booking.setOucode(session.getOuCode());
        booking.setRotaBusinessType(DURATION_BASED_BUSINESS_TYPE);
        databaseSeeder.insertAllocatedListing(booking);
    }

    private static List<LocalDate> mondaysBetween(final LocalDate start, final LocalDate end) {
        final List<LocalDate> mondays = new ArrayList<>();
        for (LocalDate monday = start.with(nextOrSame(MONDAY)); !monday.isAfter(end); monday = monday.plusWeeks(1)) {
            mondays.add(monday);
        }
        return mondays;
    }

    // ---- delete tests ----

    @Test
    void shouldSplitWeekCommencingSessionsByWhetherTheyCanBeRemoved() throws SQLException {
        final String withHearings1Id = UUID.randomUUID().toString();
        final String withHearings2Id = UUID.randomUUID().toString();
        final String withoutHearingsId = UUID.randomUUID().toString();

        final CourtSchedule withHearings1 = weekCommencingSession(withHearings1Id, nextMonday.plusWeeks(1));
        final CourtSchedule withHearings2 = weekCommencingSession(withHearings2Id, nextMonday.plusWeeks(2));
        final CourtSchedule withoutHearings = weekCommencingSession(withoutHearingsId, nextMonday.plusWeeks(3));

        databaseSeeder.insertCourtSchedule(withHearings1);
        databaseSeeder.insertCourtSchedule(withHearings2);
        databaseSeeder.insertCourtSchedule(withoutHearings);

        seedBooking(withHearings1, BOOKING_DURATION_MINUTES);
        seedBooking(withHearings2, BOOKING_DURATION_MINUTES);

        final String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\", \"%s\"]}",
                withHearings1Id, withHearings2Id, withoutHearingsId);
        try (Response response = postCommand(DELETE_URL, DELETE_CONTENT_TYPE, USER_ID, deletePayload)) {
            assertThat(response.getStatus(), is(OK.getStatusCode()));

            try (JsonReader reader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
                final JsonObject body = reader.readObject();
                assertTrue(body.containsKey(ERROR), "Response must contain 'error' when some sessions cannot be removed");
                assertThat(body.getString(ERROR), is(SOME_SESSIONS_CANNOT_BE_REMOVED));
                assertThat(body.getJsonArray(SESSIONS).size(), is(2));
                final List<String> returnedIds = body.getJsonArray(SESSIONS).getValuesAs(JsonObject.class)
                        .stream().map(s -> s.getString(COURT_SCHEDULE_ID)).toList();
                assertTrue(returnedIds.contains(withHearings1Id), "Session with hearing must appear in the cannot-remove list");
                assertTrue(returnedIds.contains(withHearings2Id), "Session with hearing must appear in the cannot-remove list");
                assertFalse(returnedIds.contains(withoutHearingsId), "Session without hearing must not appear in the cannot-remove list");
            }
        }

        final List<CourtSchedule> remaining = databaseReader.courtSchedules();
        assertTrue(remaining.stream().anyMatch(cs -> cs.getCourtScheduleId().equals(withHearings1Id)),
                "Session with hearing must still exist");
        assertTrue(remaining.stream().anyMatch(cs -> cs.getCourtScheduleId().equals(withHearings2Id)),
                "Session with hearing must still exist");
        assertFalse(remaining.stream().anyMatch(cs -> cs.getCourtScheduleId().equals(withoutHearingsId)),
                "Session without hearing must be deleted");
    }

    @Test
    void shouldRemoveEmptyWeekCommencingSession() throws SQLException {
        final String id = UUID.randomUUID().toString();
        databaseSeeder.insertCourtSchedule(weekCommencingSession(id, nextMonday));

        final String deletePayload = String.format("{\"sessions\": [\"%s\"]}", id);
        try (Response response = postCommand(DELETE_URL, DELETE_CONTENT_TYPE, USER_ID, deletePayload)) {
            assertThat(response.getStatus(), is(OK.getStatusCode()));

            try (JsonReader reader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
                final JsonObject body = reader.readObject();
                assertFalse(body.containsKey(ERROR), "No error expected when week commencing session has no hearings");
                assertThat(body.getJsonArray(SESSIONS).size(), is(0));
            }
        }

        assertFalse(databaseReader.courtSchedules().stream()
                        .anyMatch(cs -> cs.getCourtScheduleId().equals(id)),
                "Empty week commencing session must be deleted from the database");
    }

    @Test
    void shouldNotRemoveWeekCommencingSessionWithHearings() throws SQLException {
        final String id = UUID.randomUUID().toString();
        final CourtSchedule session = weekCommencingSession(id, nextMonday);
        databaseSeeder.insertCourtSchedule(session);
        seedBooking(session, BOOKING_DURATION_MINUTES);

        final String deletePayload = String.format("{\"sessions\": [\"%s\"]}", id);
        try (Response response = postCommand(DELETE_URL, DELETE_CONTENT_TYPE, USER_ID, deletePayload)) {
            assertThat(response.getStatus(), is(OK.getStatusCode()));

            try (JsonReader reader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
                final JsonObject body = reader.readObject();
                assertTrue(body.containsKey(ERROR), "Error key must be present when week commencing session has a hearing");
                assertThat(body.getJsonArray(SESSIONS).size(), is(1));
                final JsonObject returned = body.getJsonArray(SESSIONS).getJsonObject(0);
                assertThat(returned.getString(COURT_SCHEDULE_ID), is(id));
                assertThat(returned.getInt(TOTAL_BOOKED), is(BOOKING_DURATION_MINUTES));
            }
        }

        assertTrue(databaseReader.courtSchedules().stream()
                        .anyMatch(cs -> cs.getCourtScheduleId().equals(id)),
                "Week commencing session with a hearing must not be deleted");
    }

    @Test
    void shouldOnlyDeleteWeekCommencingSessionsThatHaveNoHearingsAttached() throws SQLException {
        final String removable1Id = UUID.randomUUID().toString();
        final String removable2Id = UUID.randomUUID().toString();
        final String notRemovable1Id = UUID.randomUUID().toString();
        final String notRemovable2Id = UUID.randomUUID().toString();

        final CourtSchedule removable1 = weekCommencingSession(removable1Id, nextMonday.plusWeeks(1));
        final CourtSchedule removable2 = weekCommencingSession(removable2Id, nextMonday.plusWeeks(2));
        final CourtSchedule notRemovable1 = weekCommencingSession(notRemovable1Id, nextMonday.plusWeeks(3));
        final CourtSchedule notRemovable2 = weekCommencingSession(notRemovable2Id, nextMonday.plusWeeks(4));

        databaseSeeder.insertCourtSchedule(removable1);
        databaseSeeder.insertCourtSchedule(removable2);
        databaseSeeder.insertCourtSchedule(notRemovable1);
        databaseSeeder.insertCourtSchedule(notRemovable2);

        seedBooking(notRemovable1, BOOKING_DURATION_MINUTES);
        seedBooking(notRemovable2, BOOKING_DURATION_MINUTES);

        final String deletePayload = String.format("{\"sessions\": [\"%s\", \"%s\", \"%s\", \"%s\"]}",
                removable1Id, removable2Id, notRemovable1Id, notRemovable2Id);
        try (Response response = postCommand(DELETE_URL, DELETE_CONTENT_TYPE, USER_ID, deletePayload)) {
            assertThat(response.getStatus(), is(OK.getStatusCode()));

            try (JsonReader reader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
                final JsonObject body = reader.readObject();
                assertTrue(body.containsKey(ERROR), "Error key must be present when some sessions cannot be removed");
                assertThat(body.getJsonArray(SESSIONS).size(), is(2));
                final List<String> returnedIds = body.getJsonArray(SESSIONS).getValuesAs(JsonObject.class)
                        .stream().map(s -> s.getString(COURT_SCHEDULE_ID)).toList();
                assertTrue(returnedIds.contains(notRemovable1Id));
                assertTrue(returnedIds.contains(notRemovable2Id));
            }
        }

        final List<CourtSchedule> remaining = databaseReader.courtSchedules();
        assertFalse(remaining.stream().anyMatch(cs -> cs.getCourtScheduleId().equals(removable1Id)),
                "Week commencing session without hearings must be deleted");
        assertFalse(remaining.stream().anyMatch(cs -> cs.getCourtScheduleId().equals(removable2Id)),
                "Week commencing session without hearings must be deleted");
        assertTrue(remaining.stream().anyMatch(cs -> cs.getCourtScheduleId().equals(notRemovable1Id)),
                "Week commencing session with a hearing must remain");
        assertTrue(remaining.stream().anyMatch(cs -> cs.getCourtScheduleId().equals(notRemovable2Id)),
                "Week commencing session with a hearing must remain");
    }

    private CourtSchedule weekCommencingSession(final String id, final LocalDate sessionDate) {
        final CourtSchedule session = RANDOM.nextObject(CourtSchedule.class);
        session.setCourtScheduleId(id);
        session.setBusinessType(DURATION_BASED_BUSINESS_TYPE);
        session.setSlotBased(false);
        session.setMaxDuration(CAPACITY_FIVE_DAYS);
        session.setAvailableDuration(CAPACITY_FIVE_DAYS);
        session.setSupportAdSplit(false);
        session.setCourtSession(ALL_DAY);
        session.setSessionDate(sessionDate);
        session.setSessionStartTime(combineDateAndTime(sessionDate, "09:00").toInstant());
        session.setSessionEndTime(combineDateAndTime(sessionDate, "16:00").toInstant());
        session.setJurisdiction(CROWN);
        session.setIsDraft(true);
        session.setActive(true);
        session.setWeekCommencing(true);
        return session;
    }
}
