package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    private static final String JUDICIARY_RESOURCE_URL = "/judiciary";
    private static final String ADD_AVAILABILITY_RULE_CONTENT_TYPE = "application/vnd.courtscheduler.judiciary.add.availability.rule+json";
    private static final String FIND_AVAILABILITY_CONTENT_TYPE = "application/vnd.courtscheduler.judiciary.find.availability+json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;

    @Test
    void shouldAddAvailabilityMonthyEverySecondTuesday() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 6, 30);

        // Add availability rule: Monthly, every 2nd Tuesday
        final JsonArrayBuilder repeatDaysBuilder = Json.createArrayBuilder();
        final JsonObjectBuilder dayObjectBuilder = Json.createObjectBuilder()
                .add("day", "Tuesday")
                .add("index", 2);
        repeatDaysBuilder.add(dayObjectBuilder);
        
        final String requestPayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Available")
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", "Monthly")
                .add("repeatDays", repeatDaysBuilder)
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
                .add("group", "Available")
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", "Weekly")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("Tuesday")
                        .add("Wednesday")
                        .add("Thursday")
                        .add("Friday"))
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
                .add("group", "Available")
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", "Weekly")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Tuesday")
                        .add("Thursday"))
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

        // First, add availability for all weekdays
        final String availablePayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Available")
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", "Weekly")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("Tuesday")
                        .add("Wednesday")
                        .add("Thursday")
                        .add("Friday"))
                .build()
                .toString();

        Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, availablePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Then, add unavailability for a specific date range
        final LocalDate unavailabilityStartDate = LocalDate.of(2026, 1, 10);
        final LocalDate unavailabilityEndDate = LocalDate.of(2026, 1, 15);

        final String unavailablePayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Unavailable")
                .add("startDate", unavailabilityStartDate.format(DATE_FORMATTER))
                .add("endDate", unavailabilityEndDate.format(DATE_FORMATTER))
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("Tuesday")
                        .add("Wednesday")
                        .add("Thursday")
                        .add("Friday"))
                .add("reason", "Holiday")
                .build()
                .toString();

        response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, unavailablePayload);
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
    }

    @Test
    void shouldAddUnavailabilityForWeeklyEachTuesday() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        // First, add availability for all weekdays
        final String availablePayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Available")
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", "Weekly")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("Tuesday")
                        .add("Wednesday")
                        .add("Thursday")
                        .add("Friday"))
                .build()
                .toString();

        Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, availablePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Then, add unavailability for weekly Tuesdays
        final String unavailablePayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Unavailable")
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", "Weekly")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Tuesday"))
                .add("reason", "Regular training day")
                .build()
                .toString();

        response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, unavailablePayload);
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
    }

    @Test
    void shouldAddUnavailabilityForMonthlyEverySecondMonday() {
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 6, 30);

        // First, add availability for all weekdays
        final String availablePayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Available")
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", "Weekly")
                .add("repeatDays", Json.createArrayBuilder()
                        .add("Monday")
                        .add("Tuesday")
                        .add("Wednesday")
                        .add("Thursday")
                        .add("Friday"))
                .build()
                .toString();

        Response response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, availablePayload);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Then, add unavailability for monthly 2nd Monday
        final String unavailablePayload = Json.createObjectBuilder()
                .add("judiciaryId", judiciaryId)
                .add("courtHouseId", courtHouseId)
                .add("group", "Unavailable")
                .add("startDate", startDate.format(DATE_FORMATTER))
                .add("endDate", endDate.format(DATE_FORMATTER))
                .add("recurringType", "Monthly")
                .add("repeatDays", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("day", "Monday")
                                .add("index", 2)))
                .add("reason", "Monthly meeting")
                .build()
                .toString();

        response = postCommand(JUDICIARY_RESOURCE_URL, ADD_AVAILABILITY_RULE_CONTENT_TYPE, SYSTEM_USER_ID, unavailablePayload);
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

    private boolean containsJudiciary(final JsonArray availableJudiciaries, final String judiciaryId) {
        for (int i = 0; i < availableJudiciaries.size(); i++) {
            if (judiciaryId.equals(availableJudiciaries.getString(i))) {
                return true;
            }
        }
        return false;
    }
}

