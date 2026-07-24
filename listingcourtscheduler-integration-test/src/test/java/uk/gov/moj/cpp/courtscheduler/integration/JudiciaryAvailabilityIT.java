package uk.gov.moj.cpp.courtscheduler.integration;


import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.time.LocalDate;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

class JudiciaryAvailabilityIT extends AbstractIT {

    private static final String AVAILABILITY_RULES_ADD = "/judiciaries/availability-rules/add";
    private static final String AVAILABILITY_RULES_UPDATE = "/judiciaries/availability-rules/update";
    private static final String AVAILABILITY_RULES_DELETE = "/judiciaries/availability-rules/delete";
    private static final String AVAILABILITY_RULES_VALIDATE_ADD = "/judiciaries/availability-rules/validate-add";
    private static final String AVAILABILITY_RULES_VALIDATE_UPDATE = "/judiciaries/availability-rules/validate-update";
    private static final String AVAILABILITY_RULES_VALIDATE_DELETE = "/judiciaries/availability-rules/validate-delete";
    private static final String AVAILABILITY_RULES = "/judiciaries/availability-rules";
    private static final String JUDICIARIES_AVAILABILITY = "/judiciaries";
    private static final String JUDICIARIES_SEARCH_AVAILABLE = "/judiciaries";
    private static final String RULE_ID_AVAILABILITY_RULES = "/judiciaries/availability-rules/{ruleId}";
    private static final String RESPONSE_TYPE = "application/json";
    private static final String SEARCH_AVAILABLE_RESPONSE_TYPE = "application/vnd.courtscheduler.search.available.judiciaries+json";
    private static final String ADD_AVAILABILITY_RULE_CONTENT_TYPE = "application/json";
    private static final String UPDATE_AVAILABILITY_RULE_CONTENT_TYPE = "application/json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    /**
     * Helper method to get a future date (tomorrow by default).
     * Ensures dates are always in the future for validation.
     */
    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }

    /**
     * Helper method to get a future date that falls on a specific day of week.
     * Finds the next occurrence of the specified day from today.
     */
    private LocalDate futureDateOnDayOfWeek(java.time.DayOfWeek dayOfWeek, int weeksFromNow) {
        LocalDate baseDate = LocalDate.now().plusWeeks(weeksFromNow);
        int daysUntilTarget = (dayOfWeek.getValue() - baseDate.getDayOfWeek().getValue() + 7) % 7;
        if (daysUntilTarget == 0 && weeksFromNow == 0) {
            // If today is the target day, move to next week
            daysUntilTarget = 7;
        }
        return baseDate.plusDays(daysUntilTarget);
    }

    @Test
    void shouldSearchAvailableJudiciariesAndFilterBySessionTypeOnCourtScheduleIds() throws Exception {
        final String courtHouseId = randomUUID().toString();
        final String judiciaryIdAvailable = "11111111-1111-1111-1111-111111111111";
        final String judiciaryIdUnavailable = "22222222-2222-2222-2222-222222222222";
        final String availableRuleId = randomUUID().toString();
        final String unavailableRuleId = randomUUID().toString();
        final String courtScheduleId = randomUUID().toString();
        final LocalDate targetMonday = futureDateOnDayOfWeek(java.time.DayOfWeek.MONDAY, 0);
        final LocalDate rangeEnd = targetMonday.plusDays(7);

        uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil
                .stubGetReferenceDataJudiciaries("referencedata.judiciaries.search.available.it.json");

        try {
            databaseSeeder.insertJudiciaryAvailabilityRule(
                    availableRuleId,
                    judiciaryIdAvailable,
                    courtHouseId,
                    Collections.emptyList(),
                    targetMonday,
                    rangeEnd,
                    List.of(AvailabilityDayOfWeek.Monday)
            );
            databaseSeeder.updateJudiciaryAvailabilityRuleSessionType(availableRuleId, SessionType.AM.name());

            databaseSeeder.insertJudiciaryAvailabilityRule(
                    unavailableRuleId,
                    judiciaryIdUnavailable,
                    courtHouseId,
                    Collections.emptyList(),
                    targetMonday,
                    rangeEnd,
                    List.of(AvailabilityDayOfWeek.Monday)
            );
            databaseSeeder.updateJudiciaryAvailabilityRuleSessionType(unavailableRuleId, SessionType.PM.name());

            final CourtSchedule schedule = RANDOM.nextObject(CourtSchedule.class);
            schedule.setCourtScheduleId(courtScheduleId);
            schedule.setCourtHouseId(courtHouseId);
            schedule.setSessionDate(targetMonday);
            schedule.setCourtSession(SessionType.AM.name());
            schedule.setActive(true);
            databaseSeeder.insertCourtSchedule(schedule);

            final Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("search", "ain");
            queryParams.put("judiciaryGroup", "Recorder");
            queryParams.put("courtScheduleIds", courtScheduleId);

            final RequestParams requestParams = getRequestParams(JUDICIARIES_SEARCH_AVAILABLE, SEARCH_AVAILABLE_RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
            final ResponseData responseData = poll(requestParams)
                    .with()
                    .timeout(30L, SECONDS)
                    .pollInterval(50L, MILLISECONDS)
                    .pollDelay(0L, MILLISECONDS)
                    .until();

            assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
            final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
            final JsonArray judiciaries = jsonObject.getJsonArray("judiciaries");
            assertNotNull(judiciaries);
            assertFalse(judiciaries.isEmpty());
            assertTrue(containsJudiciaryObject(judiciaries, judiciaryIdAvailable));
            assertFalse(containsJudiciaryObject(judiciaries, judiciaryIdUnavailable));
        } finally {
            uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil
                    .stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");
        }
    }

    @Test
    void shouldSearchAvailableJudiciariesWhenIgnoreAvailabilityTrue() {
        uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil
                .stubGetReferenceDataJudiciaries("referencedata.judiciaries.search.available.it.json");

        try {
            final Map<String, Object> queryParams = new HashMap<>();
            queryParams.put("search", "ain");
            queryParams.put("judiciaryGroup", "Recorder");
            queryParams.put("ignoreAvailability", true);

            final RequestParams requestParams = getRequestParams(JUDICIARIES_SEARCH_AVAILABLE, SEARCH_AVAILABLE_RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
            final ResponseData responseData = poll(requestParams)
                    .with()
                    .timeout(30L, SECONDS)
                    .pollInterval(50L, MILLISECONDS)
                    .pollDelay(0L, MILLISECONDS)
                    .until();

            assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
            final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
            final JsonArray judiciaries = jsonObject.getJsonArray("judiciaries");
            assertNotNull(judiciaries);
            assertTrue(containsJudiciaryObject(judiciaries, "11111111-1111-1111-1111-111111111111"));
            assertTrue(containsJudiciaryObject(judiciaries, "22222222-2222-2222-2222-222222222222"));
        } finally {
            uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil
                    .stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");
        }
    }

    @Test
    void shouldReturnBadRequestWhenDatesAndCourtScheduleIdsProvided() {
        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("search", "ain");
        queryParams.put("judiciaryGroup", "Recorder");
        queryParams.put("dates", LocalDate.now().plusDays(1).format(DATE_FORMATTER));
        queryParams.put("courtHouseId", randomUUID().toString());
        queryParams.put("courtScheduleIds", randomUUID().toString());

        final RequestParams requestParams = getRequestParams(JUDICIARIES_SEARCH_AVAILABLE, SEARCH_AVAILABLE_RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldReturnBadRequestWhenCourtScheduleIdsHaveDifferentCourthouses() throws Exception {
        final String id1 = randomUUID().toString();
        final String id2 = randomUUID().toString();

        final CourtSchedule schedule1 = RANDOM.nextObject(CourtSchedule.class);
        schedule1.setCourtScheduleId(id1);
        schedule1.setCourtHouseId(randomUUID().toString());
        schedule1.setSessionDate(LocalDate.now().plusDays(1));
        schedule1.setCourtSession(SessionType.AM.name());
        schedule1.setActive(true);
        databaseSeeder.insertCourtSchedule(schedule1);

        final CourtSchedule schedule2 = RANDOM.nextObject(CourtSchedule.class);
        schedule2.setCourtScheduleId(id2);
        schedule2.setCourtHouseId(randomUUID().toString());
        schedule2.setSessionDate(LocalDate.now().plusDays(1));
        schedule2.setCourtSession(SessionType.AM.name());
        schedule2.setActive(true);
        databaseSeeder.insertCourtSchedule(schedule2);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("search", "ain");
        queryParams.put("judiciaryGroup", "Recorder");
        queryParams.put("courtScheduleIds", id1 + "," + id2);

        final RequestParams requestParams = getRequestParams(JUDICIARIES_SEARCH_AVAILABLE, SEARCH_AVAILABLE_RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldAddAvailabilityWeeklyOnAllWeekdays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Add availability rule: Weekly on all weekdays (Monday-Friday)
        final String requestPayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.Monday.name())
                        .add(AvailabilityDayOfWeek.Tuesday.name())
                        .add(AvailabilityDayOfWeek.Wednesday.name())
                        .add(AvailabilityDayOfWeek.Thursday.name())
                        .add(AvailabilityDayOfWeek.Friday.name()))
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_ADD, RESPONSE_TYPE, SYSTEM_USER_ID, requestPayload);
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify by finding availability - ensure query dates are within the rule's date range
        LocalDate queryStartDate = futureDateOnDayOfWeek(java.time.DayOfWeek.MONDAY, 0);
        // Ensure query dates are within the availability range
        if (queryStartDate.isBefore(startDate)) {
            queryStartDate = startDate;
        }
        LocalDate queryEndDate = futureDateOnDayOfWeek(java.time.DayOfWeek.FRIDAY, 0);
        // If Friday is before Monday, use Monday + 4 days instead
        if (queryEndDate.isBefore(queryStartDate)) {
            queryEndDate = queryStartDate.plusDays(4);
        }
        // Ensure query end date doesn't exceed rule end date
        if (queryEndDate.isAfter(endDate)) {
            queryEndDate = endDate;
        }

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        assertTrue(availableJudiciaries.size() > 0, "Should find at least one available judiciary");
        assertTrue(containsJudiciary(availableJudiciaries, judiciaryId), "Should contain the judiciary ID");
    }

    @Test
    void shouldAddAvailabilityWeeklyOnTuesdaysAndThursdays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Add availability rule: Weekly on Tuesdays and Thursdays
        final String requestPayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.Tuesday.name())
                        .add(AvailabilityDayOfWeek.Thursday.name()))
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_ADD, RESPONSE_TYPE, SYSTEM_USER_ID, requestPayload);
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify by finding availability - ensure query dates are within the rule's date range
        LocalDate queryStartDate = futureDateOnDayOfWeek(java.time.DayOfWeek.TUESDAY, 0);
        // Ensure query dates are within the availability range
        if (queryStartDate.isBefore(startDate)) {
            queryStartDate = startDate;
        }
        LocalDate queryEndDate = futureDateOnDayOfWeek(java.time.DayOfWeek.THURSDAY, 0);
        // If Thursday is before Tuesday, use Tuesday + 2 days instead
        if (queryEndDate.isBefore(queryStartDate)) {
            queryEndDate = queryStartDate.plusDays(2);
        }
        // Ensure query end date doesn't exceed rule end date
        if (queryEndDate.isAfter(endDate)) {
            queryEndDate = endDate;
        }

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        assertTrue(availableJudiciaries.size() > 0, "Should find at least one available judiciary");
        assertTrue(containsJudiciary(availableJudiciaries, judiciaryId), "Should contain the judiciary ID");
    }

    @Test
    void shouldAddUnavailabilityForStartDateEndDate() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Add availability for all weekdays with unavailability period in a single call
        final LocalDate unavailabilityStartDate = futureDate(10);
        final LocalDate unavailabilityEndDate = futureDate(15);

        final String payload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.Monday.name())
                        .add(AvailabilityDayOfWeek.Tuesday.name())
                        .add(AvailabilityDayOfWeek.Wednesday.name())
                        .add(AvailabilityDayOfWeek.Thursday.name())
                        .add(AvailabilityDayOfWeek.Friday.name()))
                .add("unavailabilities", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("startDate", unavailabilityStartDate.format(DATE_FORMATTER))
                                .add("endDate", unavailabilityEndDate.format(DATE_FORMATTER))
                                .add("reason", "ANNUAL_LEAVE")))
                .build()
                .toString();

        Response response = postCommand(AVAILABILITY_RULES_ADD, RESPONSE_TYPE, SYSTEM_USER_ID, payload);
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify: Query during unavailable period should not return this judiciary
        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", unavailabilityStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", unavailabilityEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        // Should not contain the judiciary during unavailable period
        assertTrue(!containsJudiciary(availableJudiciaries, judiciaryId), "Should not contain the judiciary during unavailable period");

        // Verify: Query for the rule and verify unavailabilities are returned in the response
        final Map<String, Object> ruleQueryParams = new HashMap<>();
        ruleQueryParams.put("startDate", startDate.format(DATE_FORMATTER));
        ruleQueryParams.put("endDate", endDate.format(DATE_FORMATTER));
        ruleQueryParams.put("courtHouseId", courtHouseId);

        final RequestParams ruleRequestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, ruleQueryParams);
        final ResponseData ruleResponseData = poll(ruleRequestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(ruleResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject ruleJsonObject = stringToJsonObjectConverter.convert(ruleResponseData.getPayload());
        final JsonArray rules = ruleJsonObject.getJsonArray("rules");
        assertTrue(rules.size() > 0, "Should find at least one rule");
        
        final JsonObject rule = rules.getJsonObject(0);
        assertThat(rule.getString("judiciaryId"), is(judiciaryId));
        
        // Verify unavailabilities are present in the response
        assertTrue(rule.containsKey("unavailabilities"), "Rule should contain unavailabilities array");
        final JsonArray unavailabilities = rule.getJsonArray("unavailabilities");
        assertTrue(unavailabilities.size() > 0, "Should contain at least one unavailability");
        
        // Verify the unavailability structure and content
        final JsonObject unavailability = unavailabilities.getJsonObject(0);
        assertTrue(unavailability.containsKey("startDate"), "Unavailability should have startDate");
        assertTrue(unavailability.containsKey("endDate"), "Unavailability should have endDate");
        assertTrue(unavailability.containsKey("reason"), "Unavailability should have reason");
        assertThat(unavailability.getString("startDate"), is(unavailabilityStartDate.format(DATE_FORMATTER)));
        assertThat(unavailability.getString("endDate"), is(unavailabilityEndDate.format(DATE_FORMATTER)));
        assertThat(unavailability.getString("reason"), is("ANNUAL_LEAVE"));
    }

    @Test
    void shouldAddUnavailabilityForWeeklyEachTuesday() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Add availability for all weekdays with unavailability for weekly Tuesdays in a single call
        final JsonArrayBuilder unavailabilitiesBuilder = Json.createArrayBuilder();
        LocalDate firstTuesday = futureDateOnDayOfWeek(java.time.DayOfWeek.TUESDAY, 0);
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", firstTuesday.format(DATE_FORMATTER))
                .add("endDate", firstTuesday.format(DATE_FORMATTER))
                .add("reason", "TRAINING"));
        LocalDate secondTuesday = firstTuesday.plusWeeks(1);
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", secondTuesday.format(DATE_FORMATTER))
                .add("endDate", secondTuesday.format(DATE_FORMATTER))
                .add("reason", "TRAINING"));
        LocalDate thirdTuesday = firstTuesday.plusWeeks(2);
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", thirdTuesday.format(DATE_FORMATTER))
                .add("endDate", thirdTuesday.format(DATE_FORMATTER))
                .add("reason", "TRAINING"));
        LocalDate fourthTuesday = firstTuesday.plusWeeks(3);
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", fourthTuesday.format(DATE_FORMATTER))
                .add("endDate", fourthTuesday.format(DATE_FORMATTER))
                .add("reason", "TRAINING"));

        final String payload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.Monday.name())
                        .add(AvailabilityDayOfWeek.Tuesday.name())
                        .add(AvailabilityDayOfWeek.Wednesday.name())
                        .add(AvailabilityDayOfWeek.Thursday.name())
                        .add(AvailabilityDayOfWeek.Friday.name()))
                .add("unavailabilities", unavailabilitiesBuilder)
                .build()
                .toString();

        Response response = postCommand(AVAILABILITY_RULES_ADD, RESPONSE_TYPE, SYSTEM_USER_ID, payload);
        assertThat(response.getStatus(), is(OK.getStatusCode()));

        // Verify: Query for a Tuesday should not return this judiciary
        final LocalDate queryStartDate = firstTuesday; // Tuesday
        final LocalDate queryEndDate = firstTuesday; // Same Tuesday

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        // Should not contain the judiciary on Tuesday
        assertTrue(!containsJudiciary(availableJudiciaries, judiciaryId), "Should not contain the judiciary on Tuesday");

        // But should be available on other days (e.g., Monday)
        final LocalDate mondayDate = futureDateOnDayOfWeek(java.time.DayOfWeek.MONDAY, 0);
        queryParams.put("startDate", mondayDate.format(DATE_FORMATTER));
        queryParams.put("endDate", mondayDate.format(DATE_FORMATTER));

        final RequestParams mondayRequestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData mondayResponseData = poll(mondayRequestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(mondayResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject mondayJsonObject = stringToJsonObjectConverter.convert(mondayResponseData.getPayload());
        final JsonArray mondayAvailableJudiciaries = mondayJsonObject.getJsonArray("availableJudiciaries");
        assertTrue(containsJudiciary(mondayAvailableJudiciaries, judiciaryId), "Should contain the judiciary on Monday");

        // Verify: Query for the rule and verify unavailabilities with reasons are returned
        final Map<String, Object> ruleQueryParams = new HashMap<>();
        ruleQueryParams.put("startDate", startDate.format(DATE_FORMATTER));
        ruleQueryParams.put("endDate", endDate.format(DATE_FORMATTER));
        ruleQueryParams.put("courtHouseId", courtHouseId);

        final RequestParams ruleRequestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, ruleQueryParams);
        final ResponseData ruleResponseData = poll(ruleRequestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(ruleResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject ruleJsonObject = stringToJsonObjectConverter.convert(ruleResponseData.getPayload());
        final JsonArray rules = ruleJsonObject.getJsonArray("rules");
        assertTrue(rules.size() > 0, "Should find at least one rule");
        
        final JsonObject rule = rules.getJsonObject(0);
        assertThat(rule.getString("judiciaryId"), is(judiciaryId));
        
        // Verify unavailabilities are present with correct reasons
        assertTrue(rule.containsKey("unavailabilities"), "Rule should contain unavailabilities array");
        final JsonArray unavailabilities = rule.getJsonArray("unavailabilities");
        assertTrue(unavailabilities.size() >= 4, "Should contain at least 4 unavailabilities (one for each Tuesday)");
        
        // Verify all unavailabilities have TRAINING as the reason
        for (int i = 0; i < unavailabilities.size(); i++) {
            final JsonObject unavailability = unavailabilities.getJsonObject(i);
            assertTrue(unavailability.containsKey("reason"), "Unavailability should have reason");
            assertThat(unavailability.getString("reason"), is("TRAINING"));
        }
    }

    @Test
    void shouldDeleteJudiciaryAvailabilityRule() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Insert a rule via database seeder
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                new ArrayList<>(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday, AvailabilityDayOfWeek.Wednesday, AvailabilityDayOfWeek.Thursday, AvailabilityDayOfWeek.Friday)
        );

        // Verify the rule exists by finding availability - ensure query dates are within the rule's date range
        LocalDate queryStartDate = futureDateOnDayOfWeek(java.time.DayOfWeek.MONDAY, 0);
        // Ensure query dates are within the availability range
        if (queryStartDate.isBefore(startDate)) {
            queryStartDate = startDate;
        }
        LocalDate queryEndDate = futureDateOnDayOfWeek(java.time.DayOfWeek.FRIDAY, 0);
        // If Friday is before Monday, use Monday + 4 days instead
        if (queryEndDate.isBefore(queryStartDate)) {
            queryEndDate = queryStartDate.plusDays(4);
        }
        // Ensure query end date doesn't exceed rule end date
        if (queryEndDate.isAfter(endDate)) {
            queryEndDate = endDate;
        }

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        RequestParams requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        JsonArray availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        assertTrue(containsJudiciary(availableJudiciaries, judiciaryId), "Judiciary should be available before deletion");

        // Delete the rule
        final String deletePayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("ruleId", ruleId)
                .build()
                .toString();

        final Response deleteResponse = postCommand(AVAILABILITY_RULES_DELETE, RESPONSE_TYPE, SYSTEM_USER_ID, deletePayload);
        assertThat(deleteResponse.getStatus(), is(OK.getStatusCode()));

        requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        assertTrue(!containsJudiciary(availableJudiciaries, judiciaryId), "Judiciary should not be available after deletion");
    }

    private boolean containsJudiciary(final JsonArray availableJudiciaries, final String judiciaryId) {
        for (int i = 0; i < availableJudiciaries.size(); i++) {
            if (judiciaryId.equals(availableJudiciaries.getString(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsJudiciaryObject(final JsonArray judiciaries, final String judiciaryId) {
        for (int i = 0; i < judiciaries.size(); i++) {
            final JsonObject judiciary = judiciaries.getJsonObject(i);
            if (judiciaryId.equals(judiciary.getString("id", ""))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithPagination() throws Exception {
        final String ruleId1 = randomUUID().toString();
        final String ruleId2 = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Insert two rules
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId1,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday)
        );

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId2,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Wednesday, AvailabilityDayOfWeek.Thursday)
        );

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("pageSize", 10);
        queryParams.put("pageNumber", 1);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray rules = jsonObject.getJsonArray("rules");
        assertTrue(rules.size() >= 2, "Should find at least 2 rules");
        assertThat(jsonObject.getInt("totalCount"), is(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        assertThat(jsonObject.getInt("pageNumber"), is(1));
        assertThat(jsonObject.getInt("pageSize"), is(10));
        assertTrue(jsonObject.containsKey("judiciaries"), "Should contain judiciaries node");
        final JsonArray judiciaries = jsonObject.getJsonArray("judiciaries");
        assertThat(judiciaries.size(), greaterThan(0));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithDefaultPagination() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertThat(jsonObject.getInt("pageNumber"), is(1));
        assertThat(jsonObject.getInt("pageSize"), is(20));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciaries() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = "7e2f843e-d639-40b3-8611-8015f3a13333"; // Use ID from stubbed judiciaries
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("withJudiciary", true);
        queryParams.put("courtHouseId", courtHouseId);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray rules = jsonObject.getJsonArray("rules");
        assertTrue(rules.size() > 0, "Should find at least one rule");
        assertTrue(jsonObject.containsKey("judiciaries"), "Should contain judiciaries node");
        final JsonArray judiciaries = jsonObject.getJsonArray("judiciaries");
        assertTrue(judiciaries.size() > 0, "Should contain judiciaries when withJudiciary is true");
        
        // Verify judiciary structure
        final JsonObject judiciary = judiciaries.getJsonObject(0);
        assertTrue(judiciary.containsKey("id"), "Judiciary should have id");
        assertTrue(judiciary.containsKey("surname"), "Judiciary should have surname");
        assertTrue(judiciary.containsKey("forenames"), "Judiciary should have forenames");
        assertTrue(judiciary.containsKey("judiciaryType"), "Judiciary should have judiciaryType");
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciariesFalse() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("withJudiciary", false);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertTrue(jsonObject.containsKey("judiciaries"), "Should always contain judiciaries node");
        final JsonArray judiciaries = jsonObject.getJsonArray("judiciaries");
        assertThat(judiciaries.size(), is(0));
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithCourtHouseIdFilter() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray rules = jsonObject.getJsonArray("rules");
        assertTrue(rules.size() > 0, "Should find at least one rule");
        
        // Verify all rules match the courtHouseId filter
        for (int i = 0; i < rules.size(); i++) {
            final JsonObject rule = rules.getJsonObject(i);
            assertThat(rule.getString("courtHouseId"), is(courtHouseId));
        }
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithJudiciaryIdFilter() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray rules = jsonObject.getJsonArray("rules");
        assertTrue(rules.size() > 0, "Should find at least one rule");
        
        // Verify all rules match the judiciaryId filter
        for (int i = 0; i < rules.size(); i++) {
            final JsonObject rule = rules.getJsonObject(i);
            assertThat(rule.getString("judiciaryId"), is(judiciaryId));
        }
    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithPaginationPage2() throws Exception {
        final String ruleId1 = randomUUID().toString();
        final String ruleId2 = randomUUID().toString();
        final String ruleId3 = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Insert three rules
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId1,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId2,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Tuesday)
        );

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId3,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Wednesday)
        );

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("pageSize", 2);
        queryParams.put("pageNumber", 2);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray rules = jsonObject.getJsonArray("rules");
        assertThat(jsonObject.getInt("totalCount"), is(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
        assertThat(jsonObject.getInt("pageNumber"), is(2));
        assertThat(jsonObject.getInt("pageSize"), is(2));
        // Page 2 with pageSize 2 should have at least 1 rule (if totalCount >= 3)
        assertTrue(rules.size() > 0, "Should find at least one rule on page 2");
    }

    @Test
    void shouldFindJudiciaryAvailabilityRule() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        // Stub the specialisms response
        uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciarySpecialisms("referencedata.judiciary-specialisms.json");

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray rules = jsonObject.getJsonArray("rules");
        assertTrue(rules.size() > 0, "Should find at least one rule");

    }

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithBothJudiciariesAndSpecialisms() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = "9ac02e8d-ee90-3da6-8d3e-0dd0af2cb976"; // Use ID from stubbed judiciaries
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        // Stub the specialisms response
        uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciarySpecialisms("referencedata.judiciary-specialisms.json");

        final LocalDate queryStartDate = futureDate(1);
        final LocalDate queryEndDate = futureDate(30);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("withJudiciary", true);
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(AVAILABILITY_RULES, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray rules = jsonObject.getJsonArray("rules");
        assertTrue(rules.size() > 0, "Should find at least one rule");

        // Verify the rule contains the expected judiciary ID
        final JsonObject rule = rules.getJsonObject(0);
        assertThat(rule.getString("judiciaryId"), is(judiciaryId));

        assertTrue(jsonObject.containsKey("judiciaries"), "Should contain judiciaries node");

        final JsonArray judiciaries = jsonObject.getJsonArray("judiciaries");

        // Verify judiciaries structure and content
        assertTrue(judiciaries.size() > 0, "Should contain judiciaries when withJudiciary is true");
        final JsonObject judiciary = java.util.stream.IntStream.range(0, judiciaries.size())
            .mapToObj(judiciaries::getJsonObject)
            .filter(j -> judiciaryId.equals(j.getString("id")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Could not find judiciary with id: " + judiciaryId));
        assertTrue(judiciary.containsKey("id"), "Judiciary should have id");
        assertThat(judiciary.getString("id"), is(judiciaryId));
        assertTrue(judiciary.containsKey("surname"), "Judiciary should have surname");
        assertTrue(judiciary.containsKey("forenames"), "Judiciary should have forenames");
        assertTrue(judiciary.containsKey("judiciaryType"), "Judiciary should have judiciaryType");
        assertTrue(judiciary.containsKey("specialisms"), "Judiciary should have specialisms");

    }

    @Test
    void shouldUpdateJudiciaryAvailabilityRule() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate originalStartDate = futureDate(10); // 10 days from now
        final LocalDate originalEndDate = futureDate(40); // ~1.3 months from now

        // Insert an initial rule via database seeder
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                new ArrayList<>(),
                originalStartDate,
                originalEndDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday)
        );

        // Verify the original rule exists by finding availability.
        // Anchor to the first Monday on or after originalStartDate so the window always
        // contains a Monday and Tuesday regardless of what day of week today is.
        LocalDate queryStartDate = originalStartDate;
        while (queryStartDate.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            queryStartDate = queryStartDate.plusDays(1);
        }
        LocalDate queryEndDate = queryStartDate.plusDays(4); // Monday → Friday

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        RequestParams requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        JsonArray availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        assertTrue(containsJudiciary(availableJudiciaries, judiciaryId), "Judiciary should be available before update");

        // Update the rule with new dates, repeat days and unavailabilities
        final LocalDate updatedStartDate = futureDate(1); // Tomorrow
        final LocalDate updatedEndDate = futureDate(30); // ~1 month from now
        final LocalDate unavailabilityStartDate = futureDate(10);
        final LocalDate unavailabilityEndDate = futureDate(15);

        final String updatePayload = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", updatedStartDate.format(DATE_FORMATTER))
                .add("endDate", updatedEndDate.format(DATE_FORMATTER))
                .add("sessionType", SessionType.AM.name())
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.Wednesday.name())
                        .add(AvailabilityDayOfWeek.Thursday.name())
                        .build())
                .add("unavailabilities", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("startDate", unavailabilityStartDate.format(DATE_FORMATTER))
                                .add("endDate", unavailabilityEndDate.format(DATE_FORMATTER))
                                .add("reason", "ANNUAL_LEAVE")
                                .build())
                        .build())
                .build()
                .toString();

        // Update the rule with ruleId is in the payload
        final Response updateResponse = postCommand(AVAILABILITY_RULES_UPDATE, RESPONSE_TYPE, SYSTEM_USER_ID, updatePayload);
        assertThat(updateResponse.getStatus(), is(OK.getStatusCode()));

        // Verify the rule is updated by checking availability in the new date range
        // Note: The unavailability period is marked, so we'll check a date after it
        // Find a Thursday that's after the unavailability period and within the updated date range
        LocalDate queryDate = unavailabilityEndDate.plusDays(1); // Day after unavailability ends
        // Ensure query date is at least the updated start date
        if (queryDate.isBefore(updatedStartDate)) {
            queryDate = updatedStartDate;
        }
        // Find the next Wednesday or Thursday (the repeatDays in the updated rule)
        while (queryDate.isBefore(updatedEndDate) || queryDate.isEqual(updatedEndDate)) {
            if (queryDate.getDayOfWeek() == java.time.DayOfWeek.WEDNESDAY || 
                queryDate.getDayOfWeek() == java.time.DayOfWeek.THURSDAY) {
                break; // Found a valid day
            }
            queryDate = queryDate.plusDays(1);
        }
        // If we didn't find a Wednesday or Thursday within range, use the last valid day
        if (queryDate.isAfter(updatedEndDate)) {
            queryDate = updatedEndDate;
            // Go backwards to find the last Wednesday or Thursday
            while (queryDate.isAfter(updatedStartDate) || queryDate.isEqual(updatedStartDate)) {
                if (queryDate.getDayOfWeek() == java.time.DayOfWeek.WEDNESDAY || 
                    queryDate.getDayOfWeek() == java.time.DayOfWeek.THURSDAY) {
                    break;
                }
                queryDate = queryDate.minusDays(1);
            }
        }
        
        queryParams = new HashMap<>();
        queryParams.put("startDate", queryDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);

        requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        assertTrue(containsJudiciary(availableJudiciaries, judiciaryId), "Judiciary should be available in updated date range (3rd Thursday)");

        // Verify unavailabilities are working - judiciary should NOT be available during unavailability period
        // Feb 10-15 is marked as unavailable, and Feb 11 is the 2nd Wednesday (which would normally be available)
        queryParams = new HashMap<>();
        queryParams.put("startDate", unavailabilityStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", unavailabilityEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);

        requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        assertTrue(!containsJudiciary(availableJudiciaries, judiciaryId), "Judiciary should not be available during unavailability period (Feb 10-15)");

        // Verify Mon/Tue availability is gone after updating the rule to Wed/Thu.
        // Use a Mon-Tue-only window to avoid overlap with the new rule's Wed/Thu repeat days.
        queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));           // Monday
        queryParams.put("endDate", queryStartDate.plusDays(1).format(DATE_FORMATTER)); // Tuesday
        queryParams.put("courtHouseId", courtHouseId);

        requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        assertTrue(!containsJudiciary(availableJudiciaries, judiciaryId), "Judiciary should not be available in old date range after update");
    }

    @Test
    void shouldGetJudiciaryAvailabilityRule() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Insert a rule via database seeder
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                new ArrayList<>(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday, AvailabilityDayOfWeek.Tuesday)
        );

        // Get the rule
        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("withJudiciary", false);

        final String getUrl = RULE_ID_AVAILABILITY_RULES
                .replace("{ruleId}", ruleId);
        final RequestParams requestParams = getRequestParams(getUrl, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertNotNull(jsonObject.getJsonObject("rule"), "Response should contain rule");
        final JsonObject rule = jsonObject.getJsonObject("rule");
        assertThat(rule.getString("id"), is(ruleId));
        assertThat(rule.getString("judiciaryId"), is(judiciaryId));
        assertThat(rule.getString("courtHouseId"), is(courtHouseId));
        assertThat(rule.getString("startDate"), is(startDate.format(DATE_FORMATTER)));
        assertThat(rule.getString("endDate"), is(endDate.format(DATE_FORMATTER)));
    }

    @Test
    void shouldGetJudiciaryAvailabilityRuleWithJudiciary() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = "7e2f843e-d639-40b3-8611-8015f3a13333"; // Use ID from stubbed judiciaries
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Insert a rule via database seeder
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                new ArrayList<>(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Wednesday)
        );

        // Get the rule with judiciary
        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("withJudiciary", true);

        final String getUrl = RULE_ID_AVAILABILITY_RULES
                .replace("{ruleId}", ruleId);
        final RequestParams requestParams = getRequestParams(getUrl, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertNotNull(jsonObject.getJsonObject("rule"), "Response should contain rule");
        final JsonObject rule = jsonObject.getJsonObject("rule");
        assertThat(rule.getString("id"), is(ruleId));

        // Judiciary may be null if not found in reference data service, but structure should be present
        assertTrue(jsonObject.containsKey("judiciary"), "Response should contain judiciary field");
    }

    @Test
    void shouldGetJudiciaryAvailabilityRuleWithDefaultWithJudiciary() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = "7e2f843e-d639-40b3-8611-8015f3a13333"; // Use ID from stubbed judiciaries
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Insert a rule via database seeder
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                new ArrayList<>(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Friday)
        );

        // Get the rule without specifying withJudiciary (should default to true)
        final String getUrl = RULE_ID_AVAILABILITY_RULES
                .replace("{ruleId}", ruleId);
        final RequestParams requestParams = getRequestParams(getUrl, RESPONSE_TYPE, SYSTEM_USER_ID, new HashMap<>());
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertNotNull(jsonObject.getJsonObject("rule"), "Response should contain rule");
        final JsonObject rule = jsonObject.getJsonObject("rule");
        assertThat(rule.getString("id"), is(ruleId));
        assertTrue(jsonObject.containsKey("judiciary"), "Response should contain judiciary field (default withJudiciary=true)");
    }

    @Test
    void shouldReturnSuccessWhenValidatingAddJudiciaryAvailabilityRule() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.now().plusDays(1);
        final LocalDate endDate = LocalDate.now().plusDays(31);

        final JsonArrayBuilder repeatDaysBuilder = Json.createArrayBuilder();
        repeatDaysBuilder.add(AvailabilityDayOfWeek.Monday.name());

        final String requestPayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("repeatDays", repeatDaysBuilder)
                .add("sessionType", SessionType.AD.name())
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_VALIDATE_ADD, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String responseString = response.readEntity(String.class);
        final JsonObject responseJson = stringToJsonObjectConverter.convert(responseString);
        final JsonObject validationResult = responseJson.getJsonObject("validationResult");
        assertThat(validationResult.getString("status"), is("SUCCESS"));
    }

    @Test
    void shouldReturnFailureWhenValidatingAddJudiciaryAvailabilityRuleWithPastDates() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.now().minusDays(5); // Past date
        final LocalDate endDate = LocalDate.now().plusDays(31);

        final JsonArrayBuilder repeatDaysBuilder = Json.createArrayBuilder();
        repeatDaysBuilder.add(AvailabilityDayOfWeek.Monday.name());

        final String requestPayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("repeatDays", repeatDaysBuilder)
                .add("sessionType", SessionType.AD.name())
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_VALIDATE_ADD, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);

        assertThat(response.getStatus(), is(422));
        final String responseString = response.readEntity(String.class);
        assertTrue(responseString.contains("future") || responseString.contains("validationError"));
    }

    @Test
    void shouldReturnSuccessWhenValidatingUpdateJudiciaryAvailabilityRule() throws Exception {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final String ruleId = randomUUID().toString();
        final LocalDate startDate = LocalDate.now().plusDays(1);
        final LocalDate endDate = LocalDate.now().plusDays(31);

        // First, create a rule in the database
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        // Now validate an update
        final JsonArrayBuilder repeatDaysBuilder = Json.createArrayBuilder();
        repeatDaysBuilder.add(AvailabilityDayOfWeek.Monday.name());

        final String requestPayload = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("repeatDays", repeatDaysBuilder)
                .add("sessionType", SessionType.AD.name())
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_VALIDATE_UPDATE, UPDATE_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String responseString = response.readEntity(String.class);
        final JsonObject responseJson = stringToJsonObjectConverter.convert(responseString);
        final JsonObject validationResult = responseJson.getJsonObject("validationResult");
        assertThat(validationResult.getString("status"), is("SUCCESS"));
    }

    @Test
    void shouldReturnFailureWhenValidatingUpdateJudiciaryAvailabilityRuleWithChangedStartDateInPast() throws Exception {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final String ruleId = randomUUID().toString();
        final LocalDate originalStartDate = LocalDate.now().plusDays(10);
        final LocalDate newStartDate = LocalDate.now().minusDays(5); // Changed to past date
        final LocalDate endDate = LocalDate.now().plusDays(31);

        // First, create a rule in the database
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                originalStartDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        // Now validate an update with changed start date in past
        final JsonArrayBuilder repeatDaysBuilder = Json.createArrayBuilder();
        repeatDaysBuilder.add(AvailabilityDayOfWeek.Monday.name());

        final String requestPayload = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", newStartDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("repeatDays", repeatDaysBuilder)
                .add("sessionType", SessionType.AD.name())
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_VALIDATE_UPDATE, UPDATE_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);

        assertThat(response.getStatus(), is(422));
        final String responseString = response.readEntity(String.class);
        assertTrue(responseString.contains("future") || responseString.contains("validationError"));
    }

    @Test
    void shouldReturnSuccessWhenValidatingDeleteJudiciaryAvailabilityRuleWithNoSessions() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.now().plusDays(1);
        final LocalDate endDate = LocalDate.now().plusDays(31);

        // First, create a rule in the database
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );

        // Now validate delete - should succeed as no sessions are assigned
        final String requestPayload = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_VALIDATE_DELETE, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final String responseString = response.readEntity(String.class);
        final JsonObject responseJson = stringToJsonObjectConverter.convert(responseString);
        final JsonObject validationResult = responseJson.getJsonObject("validationResult");
        assertThat(validationResult.getString("status"), is("SUCCESS"));
    }

    @Test
    void shouldReturnFailureWhenValidatingDeleteJudiciaryAvailabilityRuleAppliedToSession() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = futureDate(1); // Tomorrow
        final LocalDate endDate = futureDate(30); // ~1 month from now

        // Create a rule: Weekly on Mondays, AM session
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                Arrays.asList(AvailabilityDayOfWeek.Monday)
        );
        // Update session_type to AM
        databaseSeeder.updateJudiciaryAvailabilityRuleSessionType(ruleId, SessionType.AM.name());

        // Create a court schedule on a Monday with AM session
        final String courtScheduleId = randomUUID().toString();
        final LocalDate sessionDate = futureDateOnDayOfWeek(java.time.DayOfWeek.MONDAY, 0);
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCourtHouseId(courtHouseId);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setCourtSession("AD");
        courtSchedule.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        // Assign the judiciary to the court schedule
        final CourtScheduleJudiciary courtScheduleJudiciary = RANDOM.nextObject(CourtScheduleJudiciary.class);
        final CourtScheduleJudiciaryKey key = new CourtScheduleJudiciaryKey();
        key.setCourtScheduleId(courtScheduleId);
        key.setJudiciaryId(judiciaryId);
        courtScheduleJudiciary.setId(key);
        courtScheduleJudiciary.setActive(true);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        // Now validate delete - should fail as rule is applied to a session
        final String requestPayload = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_VALIDATE_DELETE, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);

        assertThat(response.getStatus(), is(422));
        final String responseString = response.readEntity(String.class);
        final JsonObject responseJson = stringToJsonObjectConverter.convert(responseString);
        final JsonObject validationResult = responseJson.getJsonObject("validationResult");
        assertThat(validationResult.getString("status"), is("FAILURE"));
        assertThat(validationResult.getString("validationError"), is("You cannot delete this itinerary because it is being used in a session. You must remove the session before you can delete it."));
    }

    @Test
    void shouldReturnFailureWhenValidatingDeleteJudiciaryAvailabilityRuleWithNonExistentRule() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();

        // Validate delete for non-existent rule
        final String requestPayload = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .build()
                .toString();

        final Response response = postCommand(AVAILABILITY_RULES_VALIDATE_DELETE, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);

        assertThat(response.getStatus(), is(422));
        final String responseString = response.readEntity(String.class);
        assertTrue(responseString.contains("not found") || responseString.contains("validationError"));
    }

}

