package uk.gov.moj.cpp.courtscheduler.integration.performance;

import org.junit.jupiter.api.Test;
import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.integration.AbstractIT;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder.RuleData;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;

class JudiciaryAvailabilityBatchIT extends AbstractIT {

    private static final String JUDICIARIES_AVAILABILITY = "/judiciaries/availability";
    private static final String RESPONSE_TYPE = "application/json";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    private static final int TOTAL_JUDICIARIES = 10000;
    private static final int EXPECTED_RESULTS = 20;

    @Test
    void findBatchJudiciaryAvailability() throws Exception {
        final String courtHouseId = randomUUID().toString();
        final LocalDate queryStartDate = LocalDate.of(2026, 1, 5); // Monday
        final LocalDate queryEndDate = LocalDate.of(2026, 1, 9); // Friday

        // Collect all rules for batch insertion
        final List<RuleData> allRules = new ArrayList<>();
        final List<String> matchingJudiciaryIds = new ArrayList<>();

        // Create 20 judiciaries that will match the query (available on weekdays in the query range)
        for (int i = 0; i < EXPECTED_RESULTS; i++) {
            final String judiciaryId = randomUUID().toString();
            matchingJudiciaryIds.add(judiciaryId);

            // Create at least 3 available rules
            final LocalDate availableStart1 = LocalDate.of(2026, 1, 1);
            final LocalDate availableEnd1 = LocalDate.of(2026, 1, 31);
            allRules.add(new RuleData(
                    randomUUID().toString(),
                    judiciaryId,
                    courtHouseId,
                    Collections.emptyList(),
                    availableStart1,
                    availableEnd1,
                    List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
            ));

            final LocalDate availableStart2 = LocalDate.of(2026, 2, 1);
            final LocalDate availableEnd2 = LocalDate.of(2026, 2, 28);
            allRules.add(new RuleData(
                    randomUUID().toString(),
                    judiciaryId,
                    courtHouseId,
                    Collections.emptyList(),
                    availableStart2,
                    availableEnd2,
                    List.of("Monday", "Tuesday", "Wednesday")
            ));

            final LocalDate availableStart3 = LocalDate.of(2026, 3, 1);
            final LocalDate availableEnd3 = LocalDate.of(2026, 3, 31);
            allRules.add(new RuleData(
                    randomUUID().toString(),
                    judiciaryId,
                    courtHouseId,
                    Collections.emptyList(),
                    availableStart3,
                    availableEnd3,
                    List.of("Thursday", "Friday")
            ));

            // Create at least 1 unavailable rule (but not affecting the query range)
            final LocalDate unavailableStart = LocalDate.of(2026, 1, 20);
            final LocalDate unavailableEnd = LocalDate.of(2026, 1, 25);
            final JudiciaryUnavailabilityRequest unavail = new JudiciaryUnavailabilityRequest();
            unavail.setStartDate(unavailableStart);
            unavail.setEndDate(unavailableEnd);
            List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
            unavailabilities.add(unavail);
            allRules.add(new RuleData(
                    randomUUID().toString(),
                    judiciaryId,
                    courtHouseId,
                    unavailabilities,
                    unavailableStart,
                    unavailableEnd,
                    List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
            ));
        }

        // Create 9980 judiciaries that will NOT match the query
        // Strategy: Make them unavailable during the query range OR have rules outside the query range
        for (int i = 0; i < TOTAL_JUDICIARIES - EXPECTED_RESULTS; i++) {
            final String judiciaryId = randomUUID().toString();

            // Create 3 available rules, but outside the query date range
            final LocalDate availableStart1 = LocalDate.of(2026, 2, 1);
            final LocalDate availableEnd1 = LocalDate.of(2026, 2, 28);
            allRules.add(new RuleData(
                    randomUUID().toString(),
                    judiciaryId,
                    courtHouseId,
                    new ArrayList<>(),
                    availableStart1,
                    availableEnd1,
                    List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
            ));

            final LocalDate availableStart2 = LocalDate.of(2026, 3, 1);
            final LocalDate availableEnd2 = LocalDate.of(2026, 3, 31);
            allRules.add(new RuleData(
                    randomUUID().toString(),
                    judiciaryId,
                    courtHouseId,
                    new ArrayList<>(),
                    availableStart2,
                    availableEnd2,
                    List.of("Monday", "Tuesday", "Wednesday")
            ));

            final LocalDate availableStart3 = LocalDate.of(2026, 4, 1);
            final LocalDate availableEnd3 = LocalDate.of(2026, 4, 30);
            allRules.add(new RuleData(
                    randomUUID().toString(),
                    judiciaryId,
                    courtHouseId,
                    new ArrayList<>(),
                    availableStart3,
                    availableEnd3,
                    List.of("Thursday", "Friday")
            ));
            final JudiciaryUnavailabilityRequest unavail = new JudiciaryUnavailabilityRequest();
            unavail.setStartDate(queryStartDate);
            unavail.setEndDate(queryEndDate);
            List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();
            unavailabilities.add(unavail);
            // Create 1 unavailable rule that makes them unavailable during the query range
            allRules.add(new RuleData(
                    randomUUID().toString(),
                    judiciaryId,
                    courtHouseId,
                    unavailabilities,
                    queryStartDate,
                    queryEndDate,
                    List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
            ));
        }

        // Batch insert all rules at once (much faster than individual inserts)
        databaseSeeder.insertJudiciaryAvailabilityRulesBatch(allRules);

        // Call the find endpoint
        final Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("startDate", queryStartDate.format(DATE_FORMATTER));
        queryParams.put("endDate", queryEndDate.format(DATE_FORMATTER));
        queryParams.put("courtHouseId", courtHouseId);

        final RequestParams requestParams = getRequestParams(JUDICIARIES_AVAILABILITY, RESPONSE_TYPE, SYSTEM_USER_ID, queryParams);
        final ResponseData responseData = poll(requestParams)
                .with()
                .timeout(60L, SECONDS) // Increased timeout for large dataset
                .pollInterval(100L, MILLISECONDS)
                .pollDelay(0L, MILLISECONDS)
                .until();

        assertThat(responseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        final JsonArray availableJudiciaries = jsonObject.getJsonArray("availableJudiciaries");

        // Verify we get exactly 20 results
        assertEquals(EXPECTED_RESULTS, availableJudiciaries.size(), 
                "Should return exactly " + EXPECTED_RESULTS + " available judiciaries");

        // Verify all expected judiciaries are in the results
        for (String expectedJudiciaryId : matchingJudiciaryIds) {
            assertTrue(containsJudiciary(availableJudiciaries, expectedJudiciaryId),
                    "Should contain judiciary: " + expectedJudiciaryId);
        }
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

