package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.RecurringType;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

class JudiciaryAvailabilityIT extends AbstractIT {

    private static final String JUDICIARY_RESOURCE_URL = "/judiciary-availability";
    private static final String ADD_AVAILABILITY_RULE_CONTENT_TYPE = "application/vnd.courtscheduler.judiciary.add.availability.rule+json";
    private static final String UPDATE_AVAILABILITY_RULE_CONTENT_TYPE = "application/vnd.courtscheduler.judiciary.update.availability.rule+json";
    private static final String DELETE_AVAILABILITY_RULE_CONTENT_TYPE = "application/vnd.courtscheduler.judiciary.delete.availability.rule+json";
    private static final String FIND_AVAILABILITY_CONTENT_TYPE = "application/vnd.courtscheduler.judiciary.find.availability+json";
    private static final String FIND_AVAILABILITY_RULE_CONTENT_TYPE = "application/vnd.courtscheduler.judiciary.find.availability.rule+json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    @Test
    void shouldAddAvailabilityMonthlyEverySecondTuesday() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 6, 30);

        // Add availability rule: Monthly, every 2nd Tuesday
        final JsonArrayBuilder repeatDaysBuilder = Json.createArrayBuilder();
        final JsonObjectBuilder dayObjectBuilder = Json.createObjectBuilder()
                .add("day", AvailabilityDayOfWeek.TUESDAY.name())
                .add("index", 2);
        repeatDaysBuilder.add(dayObjectBuilder);
        
        final String requestPayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", RecurringType.MONTHLY.name())
                .add("repeatDays", repeatDaysBuilder)
                .add("sessionType", SessionType.AD.name())
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify by finding availability for a date range that includes 2nd Tuesday
        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
    void shouldAddAvailabilityWeeklyOnAllWeekdays() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        // Add availability rule: Weekly on all weekdays (Monday-Friday)
        final String requestPayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", RecurringType.WEEKLY.name())
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.MONDAY.name())
                        .add(AvailabilityDayOfWeek.TUESDAY.name())
                        .add(AvailabilityDayOfWeek.WEDNESDAY.name())
                        .add(AvailabilityDayOfWeek.THURSDAY.name())
                        .add(AvailabilityDayOfWeek.FRIDAY.name()))
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify by finding availability
        final LocalDate queryStartDate = LocalDate.of(2026, 1, 5); // Monday
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 9); // Friday

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        // Add availability rule: Weekly on Tuesdays and Thursdays
        final String requestPayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", RecurringType.WEEKLY.name())
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.TUESDAY.name())
                        .add(AvailabilityDayOfWeek.THURSDAY.name()))
                .build()
                .toString();

        final Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, requestPayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify by finding availability
        final LocalDate queryStartDate = LocalDate.of(2026, 1, 6); // Tuesday
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 8); // Thursday

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        // Add availability for all weekdays with unavailability period in a single call
        final LocalDate unavailabilityStartDate = LocalDate.of(2026, 1, 10);
        final LocalDate unavailabilityEndDate = LocalDate.of(2026, 1, 15);

        final String payload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", RecurringType.WEEKLY.name())
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.MONDAY.name())
                        .add(AvailabilityDayOfWeek.TUESDAY.name())
                        .add(AvailabilityDayOfWeek.WEDNESDAY.name())
                        .add(AvailabilityDayOfWeek.THURSDAY.name())
                        .add(AvailabilityDayOfWeek.FRIDAY.name()))
                .add("unavailabilities", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("startDate", unavailabilityStartDate.format(DATE_FORMATTER))
                                .add("endDate", unavailabilityEndDate.format(DATE_FORMATTER))
                                .add("reason", "ANNUAL_LEAVE")))
                .build()
                .toString();

        Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, payload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify: Query during unavailable period should not return this judiciary
        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", unavailabilityStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", unavailabilityEndDate.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        ruleQueryParams.put("judiciaryId", judiciaryId);

        final RequestParams ruleRequestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, ruleQueryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        // Add availability for all weekdays with unavailability for weekly Tuesdays in a single call
        // January 2026: Tuesdays are Jan 6, 13, 20, 27
        final JsonArrayBuilder unavailabilitiesBuilder = Json.createArrayBuilder();
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 1, 6).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 1, 6).format(DATE_FORMATTER))
                .add("reason", "TRAINING"));
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 1, 13).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 1, 13).format(DATE_FORMATTER))
                .add("reason", "TRAINING"));
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 1, 20).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 1, 20).format(DATE_FORMATTER))
                .add("reason", "TRAINING"));
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 1, 27).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 1, 27).format(DATE_FORMATTER))
                .add("reason", "TRAINING"));

        final String payload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", RecurringType.WEEKLY.name())
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.MONDAY.name())
                        .add(AvailabilityDayOfWeek.TUESDAY.name())
                        .add(AvailabilityDayOfWeek.WEDNESDAY.name())
                        .add(AvailabilityDayOfWeek.THURSDAY.name())
                        .add(AvailabilityDayOfWeek.FRIDAY.name()))
                .add("unavailabilities", unavailabilitiesBuilder)
                .build()
                .toString();

        Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, payload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify: Query for a Tuesday should not return this judiciary
        final LocalDate queryStartDate = LocalDate.of(2026, 1, 6); // Tuesday
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 6); // Same Tuesday

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate mondayDate = LocalDate.of(2026, 1, 5); // Monday
        queryParams.put("startDate", mondayDate.format(DATE_FORMATTER));
        queryParams.put("endDate", mondayDate.format(DATE_FORMATTER));

        final RequestParams mondayRequestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        ruleQueryParams.put("judiciaryId", judiciaryId);

        final RequestParams ruleRequestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, ruleQueryParams);
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
    void shouldAddUnavailabilityForMonthlyEverySecondMonday() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 6, 30);

        // Add availability for all weekdays with unavailability for monthly 2nd Monday in a single call
        // 2nd Mondays: Jan 12, Feb 9, Mar 9, Apr 13, May 11, Jun 8
        final JsonArrayBuilder unavailabilitiesBuilder = Json.createArrayBuilder();
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 1, 12).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 1, 12).format(DATE_FORMATTER))
                .add("reason", "OFFICIAL_BUSINESS"));
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 2, 9).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 2, 9).format(DATE_FORMATTER))
                .add("reason", "OFFICIAL_BUSINESS"));
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 3, 9).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 3, 9).format(DATE_FORMATTER))
                .add("reason", "OFFICIAL_BUSINESS"));
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 4, 13).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 4, 13).format(DATE_FORMATTER))
                .add("reason", "OFFICIAL_BUSINESS"));
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 5, 11).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 5, 11).format(DATE_FORMATTER))
                .add("reason", "OFFICIAL_BUSINESS"));
        unavailabilitiesBuilder.add(Json.createObjectBuilder()
                .add("startDate", LocalDate.of(2026, 6, 8).format(DATE_FORMATTER))
                .add("endDate", LocalDate.of(2026, 6, 8).format(DATE_FORMATTER))
                .add("reason", "OFFICIAL_BUSINESS"));

        final String payload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", RecurringType.WEEKLY.name())
                .add("repeatDays", Json.createArrayBuilder()
                        .add(AvailabilityDayOfWeek.MONDAY.name())
                        .add(AvailabilityDayOfWeek.TUESDAY.name())
                        .add(AvailabilityDayOfWeek.WEDNESDAY.name())
                        .add(AvailabilityDayOfWeek.THURSDAY.name())
                        .add(AvailabilityDayOfWeek.FRIDAY.name()))
                .add("unavailabilities", unavailabilitiesBuilder)
                .build()
                .toString();

        Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, payload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify: Query for 2nd Monday should not return this judiciary
        // January 2026: 1st Monday is Jan 5, 2nd Monday is Jan 12
        final LocalDate secondMonday = LocalDate.of(2026, 1, 12);
        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", secondMonday.format(DATE_FORMATTER));
        queryParams.put("endDate", secondMonday.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");
        // Should not contain the judiciary on 2nd Monday
        assertTrue(!containsJudiciary(availableJudiciaries, judiciaryId), "Should not contain the judiciary on 2nd Monday");

        // Verify: Query for the rule and verify unavailabilities with reasons are returned
        final Map<String, Object> ruleQueryParams = new HashMap<>();
        ruleQueryParams.put("startDate", startDate.format(DATE_FORMATTER));
        ruleQueryParams.put("endDate", endDate.format(DATE_FORMATTER));
        ruleQueryParams.put("judiciaryId", judiciaryId);

        final RequestParams ruleRequestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, ruleQueryParams);
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
        assertTrue(unavailabilities.size() >= 6, "Should contain at least 6 unavailabilities (one for each 2nd Monday)");
        
        // Verify all unavailabilities have OFFICIAL_BUSINESS as the reason
        for (int i = 0; i < unavailabilities.size(); i++) {
            final JsonObject unavailability = unavailabilities.getJsonObject(i);
            assertTrue(unavailability.containsKey("reason"), "Unavailability should have reason");
            assertThat(unavailability.getString("reason"), is("OFFICIAL_BUSINESS"));
        }

        // But should be available on other Mondays (e.g., 1st Monday)
        final LocalDate firstMonday = LocalDate.of(2026, 1, 5); // 1st Monday
        queryParams.put("startDate", firstMonday.format(DATE_FORMATTER));
        queryParams.put("endDate", firstMonday.format(DATE_FORMATTER));

        final RequestParams firstMondayRequestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData firstMondayResponseData = poll(firstMondayRequestParams)
                .with()
                .timeout(30L, SECONDS)
                .pollInterval(50L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(firstMondayResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject firstMondayJsonObject = stringToJsonObjectConverter.convert(firstMondayResponseData.getPayload());
        final JsonArray firstMondayAvailableJudiciaries = firstMondayJsonObject.getJsonArray("availableJudiciaries");
        assertTrue(containsJudiciary(firstMondayAvailableJudiciaries, judiciaryId), "Should contain the judiciary on 1st Monday");
    }

    @Test
    void shouldDeleteJudiciaryAvailabilityRule() throws Exception {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        // Insert a rule via database seeder
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                new ArrayList<>(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY, AvailabilityDayOfWeek.WEDNESDAY, AvailabilityDayOfWeek.THURSDAY, AvailabilityDayOfWeek.FRIDAY)
        );

        // Verify the rule exists by finding availability
        final LocalDate queryStartDate = LocalDate.of(2026, 1, 5); // Monday
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 9); // Friday

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
                .add("ruleId", ruleId)
                .build()
                .toString();

        final Response deleteResponse = deleteCommand(JUDICIARY_RESOURCE_URL, DELETE_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, deletePayload);
        assertThat(deleteResponse.getStatus(), is(ACCEPTED.getStatusCode()));

        requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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

    @Test
    void shouldFindJudiciaryAvailabilityRulesWithPagination() throws Exception {
        final String ruleId1 = randomUUID().toString();
        final String ruleId2 = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        // Insert two rules
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId1,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY)
        );

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId2,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.WEDNESDAY, AvailabilityDayOfWeek.THURSDAY)
        );

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("pageSize", 10);
        queryParams.put("pageNumber", 1);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("withJudiciary", true);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("withJudiciary", false);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        // Insert three rules
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId1,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId2,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.TUESDAY)
        );

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId3,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.WEDNESDAY)
        );

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("pageSize", 2);
        queryParams.put("pageNumber", 2);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        // Stub the specialisms response
        uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciarySpecialisms("referencedata.judiciary-specialisms.json");

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                Collections.emptyList(),
                startDate,
                endDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY)
        );

        // Stub the specialisms response
        uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciarySpecialisms("referencedata.judiciary-specialisms.json");

        final LocalDate queryStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 31);

        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("withJudiciary", true);
        queryParams.put("judiciaryId", judiciaryId);

        final RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate originalStartDate = LocalDate.of(2026, 1, 1);
        final LocalDate originalEndDate = LocalDate.of(2026, 1, 31);

        // Insert an initial rule via database seeder
        databaseSeeder.insertJudiciaryAvailabilityRule(
                ruleId,
                judiciaryId,
                courtHouseId,
                new ArrayList<>(),
                originalStartDate,
                originalEndDate,
                RecurringType.WEEKLY,
                Arrays.asList(AvailabilityDayOfWeek.MONDAY, AvailabilityDayOfWeek.TUESDAY)
        );

        // Verify the original rule exists by finding availability
        final LocalDate queryStartDate = LocalDate.of(2026, 1, 5); // Monday
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 9); // Friday

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        RequestParams requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        final LocalDate updatedStartDate = LocalDate.of(2026, 2, 1);
        final LocalDate updatedEndDate = LocalDate.of(2026, 2, 28);
        final LocalDate unavailabilityStartDate = LocalDate.of(2026, 2, 10);
        final LocalDate unavailabilityEndDate = LocalDate.of(2026, 2, 15);

        final String updatePayload = Json.createObjectBuilder()
                .add("ruleId", ruleId)
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("startDate", updatedStartDate.format(DATE_FORMATTER))
                .add("endDate", updatedEndDate.format(DATE_FORMATTER))
                .add("recurringType", RecurringType.MONTHLY.name())
                .add("sessionType", SessionType.AM.name())
                .add("repeatDays", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("day", AvailabilityDayOfWeek.WEDNESDAY.name())
                                .add("index", 2)
                                .build())
                        .add(Json.createObjectBuilder()
                                .add("day", AvailabilityDayOfWeek.THURSDAY.name())
                                .add("index", 3)
                                .build())
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
        final Response updateResponse = putCommand(JUDICIARY_RESOURCE_URL, UPDATE_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, updatePayload);
        assertThat(updateResponse.getStatus(), is(ACCEPTED.getStatusCode()));

        // Verify the rule is updated by checking availability in the new date range
        // February 2026: 2nd Wednesday = Feb 11, 3rd Thursday = Feb 19
        // Note: Feb 10-15 is marked as unavailable, so we'll check around Feb 19 (3rd Thursday)
        queryParams = new HashMap<>();
        queryParams.put("startDate", LocalDate.of(2026, 2, 18).format(DATE_FORMATTER));
        queryParams.put("endDate", LocalDate.of(2026, 2, 20).format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
        queryParams.put("judiciaryId", judiciaryId);

        requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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

        // Verify the rule is not available in the old date range
        queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("judiciaryId", judiciaryId);

        requestParams = getRequestParams(JUDICIARY_RESOURCE_URL, FIND_AVAILABILITY_CONTENT_TYPE, SYSTEM_USER_ID, queryParams);
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
}

