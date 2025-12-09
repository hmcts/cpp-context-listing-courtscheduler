package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialism;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.RecurringType;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindJudiciaryAvailabilityRuleResponseConverterTest {

    private FindJudiciaryAvailabilityRuleResponseConverter converter;

    @BeforeEach
    void setUp() {
        converter = new FindJudiciaryAvailabilityRuleResponseConverter();
    }

    @Test
    void shouldConvertFullResponseWithAllFields() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        final List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.MONDAY, null),
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.TUESDAY, 1)
        );

        final List<JudiciaryUnavailabilityResponse> unavailabilities = Arrays.asList(
                new JudiciaryUnavailabilityResponse(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 12),
                        UnavailabilityReason.ANNUAL_LEAVE
                )
        );

        final JudiciaryAvailabilityRuleResponse rule = new JudiciaryAvailabilityRuleResponse();
        rule.setId(ruleId);
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setStartDate(startDate);
        rule.setEndDate(endDate);
        rule.setRecurringType(RecurringType.WEEKLY);
        rule.setSessionType(SessionType.AM);
        rule.setRepeatDays(repeatDays);
        rule.setUnavailabilities(unavailabilities);

        final Judiciary judiciary = new Judiciary();
        judiciary.setId(judiciaryId);
        judiciary.setSurname("Smith");

        final JudiciarySpecialism specialism = new JudiciarySpecialism();
        specialism.setJudiciaryId(judiciaryId);
        specialism.setSpecialisms(Arrays.asList(JudiciarySpecialismType.MURDER, JudiciarySpecialismType.ATTEMPTED_MURDER));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);
        response.setJudiciaries(Arrays.asList(judiciary));
        response.setSpecialisms(Arrays.asList(specialism));

        final JsonObject result = converter.convert(response);

        assertNotNull(result);
        assertThat(result.getInt("totalCount"), is(1));
        assertThat(result.getInt("pageNumber"), is(1));
        assertThat(result.getInt("pageSize"), is(20));

        // Verify rules array
        final JsonArray rulesArray = result.getJsonArray("rules");
        assertNotNull(rulesArray);
        assertThat(rulesArray.size(), is(1));

        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        assertThat(ruleObject.getString("id"), is(ruleId));
        assertThat(ruleObject.getString("judiciaryId"), is(judiciaryId));
        assertThat(ruleObject.getString("courtHouseId"), is(courtHouseId));
        assertThat(ruleObject.getString("startDate"), is("2026-01-01"));
        assertThat(ruleObject.getString("endDate"), is("2026-01-31"));
        assertThat(ruleObject.getString("recurringType"), is("WEEKLY"));
        assertThat(ruleObject.getString("sessionType"), is("AM"));

        // Verify repeatDays - first is string, second is object
        final JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");
        assertThat(repeatDaysArray.size(), is(2));
        assertThat(repeatDaysArray.getString(0), is("MONDAY"));
        final JsonObject repeatDayObject = repeatDaysArray.getJsonObject(1);
        assertThat(repeatDayObject.getString("day"), is("TUESDAY"));
        assertThat(repeatDayObject.getInt("index"), is(1));

        // Verify unavailabilities
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");
        assertThat(unavailabilitiesArray.size(), is(1));
        final JsonObject unavailabilityObject = unavailabilitiesArray.getJsonObject(0);
        assertThat(unavailabilityObject.getString("startDate"), is("2026-01-10"));
        assertThat(unavailabilityObject.getString("endDate"), is("2026-01-12"));
        assertThat(unavailabilityObject.getString("reason"), is("ANNUAL_LEAVE"));

        // Verify judiciaries array
        final JsonArray judiciariesArray = result.getJsonArray("judiciaries");
        assertNotNull(judiciariesArray);
        assertThat(judiciariesArray.size(), is(1));

        // Verify specialisms array
        final JsonArray specialismsArray = result.getJsonArray("specialisms");
        assertNotNull(specialismsArray);
        assertThat(specialismsArray.size(), is(1));
    }

    @Test
    void shouldConvertResponseWithNullListsAsEmptyArrays() {
        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(new ArrayList<>());
        response.setTotalCount(0);
        response.setPageNumber(1);
        response.setPageSize(20);
        response.setJudiciaries(null);
        response.setSpecialisms(null);

        final JsonObject result = converter.convert(response);

        assertNotNull(result);
        assertThat(result.getInt("totalCount"), is(0));
        final JsonArray rulesArray = result.getJsonArray("rules");
        assertNotNull(rulesArray);
        assertThat(rulesArray.size(), is(0));
        final JsonArray judiciariesArray = result.getJsonArray("judiciaries");
        assertNotNull(judiciariesArray);
        assertThat(judiciariesArray.size(), is(0));
        final JsonArray specialismsArray = result.getJsonArray("specialisms");
        assertNotNull(specialismsArray);
        assertThat(specialismsArray.size(), is(0));
    }

    @Test
    void shouldConvertRepeatDaysAsStringWhenIndexIsNull() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.WEDNESDAY, null)
        ));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");

        assertThat(repeatDaysArray.size(), is(1));
        assertThat(repeatDaysArray.getString(0), is("WEDNESDAY"));
    }

    @Test
    void shouldConvertRepeatDaysAsObjectWhenIndexIsNotNull() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.THURSDAY, 2)
        ));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");

        assertThat(repeatDaysArray.size(), is(1));
        final JsonObject repeatDayObject = repeatDaysArray.getJsonObject(0);
        assertThat(repeatDayObject.getString("day"), is("THURSDAY"));
        assertThat(repeatDayObject.getInt("index"), is(2));
    }

    @Test
    void shouldConvertRepeatDaysWithMixedFormats() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRepeatDays(Arrays.asList(
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.MONDAY, null),
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.TUESDAY, 1),
                new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.FRIDAY, null)
        ));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");

        assertThat(repeatDaysArray.size(), is(3));
        assertThat(repeatDaysArray.getString(0), is("MONDAY"));
        assertThat(repeatDaysArray.getJsonObject(1).getString("day"), is("TUESDAY"));
        assertThat(repeatDaysArray.getString(2), is("FRIDAY"));
    }

    @Test
    void shouldConvertUnavailabilityWithReason() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setUnavailabilities(Arrays.asList(
                new JudiciaryUnavailabilityResponse(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 5),
                        UnavailabilityReason.TRAINING
                )
        ));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");

        assertThat(unavailabilitiesArray.size(), is(1));
        final JsonObject unavailabilityObject = unavailabilitiesArray.getJsonObject(0);
        assertThat(unavailabilityObject.getString("startDate"), is("2026-02-01"));
        assertThat(unavailabilityObject.getString("endDate"), is("2026-02-05"));
        assertThat(unavailabilityObject.getString("reason"), is("TRAINING"));
    }

    @Test
    void shouldConvertUnavailabilityWithoutReason() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setUnavailabilities(Arrays.asList(
                new JudiciaryUnavailabilityResponse(
                        LocalDate.of(2026, 2, 1),
                        LocalDate.of(2026, 2, 5),
                        null
                )
        ));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");

        assertThat(unavailabilitiesArray.size(), is(1));
        final JsonObject unavailabilityObject = unavailabilitiesArray.getJsonObject(0);
        assertThat(unavailabilityObject.getString("startDate"), is("2026-02-01"));
        assertThat(unavailabilityObject.getString("endDate"), is("2026-02-05"));
        assertTrue(!unavailabilityObject.containsKey("reason") || unavailabilityObject.isNull("reason"));
    }

    @Test
    void shouldConvertRuleWithoutOptionalFields() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRecurringType(null);
        rule.setSessionType(null);

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);

        assertThat(ruleObject.getString("id"), is(notNullValue()));
        assertThat(ruleObject.getString("judiciaryId"), is(notNullValue()));
        assertTrue(!ruleObject.containsKey("recurringType") || ruleObject.isNull("recurringType"));
        assertTrue(!ruleObject.containsKey("sessionType") || ruleObject.isNull("sessionType"));
    }

    @Test
    void shouldConvertRuleWithAllOptionalFields() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRecurringType(RecurringType.MONTHLY);
        rule.setSessionType(SessionType.PM);

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);

        assertThat(ruleObject.getString("recurringType"), is("MONTHLY"));
        assertThat(ruleObject.getString("sessionType"), is("PM"));
    }

    @Test
    void shouldConvertEmptyRepeatDaysAsEmptyArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRepeatDays(new ArrayList<>());

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");

        assertNotNull(repeatDaysArray);
        assertThat(repeatDaysArray.size(), is(0));
    }

    @Test
    void shouldConvertNullRepeatDaysAsEmptyArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRepeatDays(null);

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");

        assertNotNull(repeatDaysArray);
        assertThat(repeatDaysArray.size(), is(0));
    }

    @Test
    void shouldConvertEmptyUnavailabilitiesAsEmptyArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setUnavailabilities(new ArrayList<>());

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");

        assertNotNull(unavailabilitiesArray);
        assertThat(unavailabilitiesArray.size(), is(0));
    }

    @Test
    void shouldConvertNullUnavailabilitiesAsEmptyArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setUnavailabilities(null);

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");

        assertNotNull(unavailabilitiesArray);
        assertThat(unavailabilitiesArray.size(), is(0));
    }

    @Test
    void shouldConvertMultipleRules() {
        final JudiciaryAvailabilityRuleResponse rule1 = createBasicRule();
        rule1.setId(randomUUID().toString());
        final JudiciaryAvailabilityRuleResponse rule2 = createBasicRule();
        rule2.setId(randomUUID().toString());

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule1, rule2));
        response.setTotalCount(2);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");

        assertThat(rulesArray.size(), is(2));
        assertThat(result.getInt("totalCount"), is(2));
    }

    @Test
    void shouldConvertAllUnavailabilityReasons() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setUnavailabilities(Arrays.asList(
                new JudiciaryUnavailabilityResponse(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), UnavailabilityReason.TRAINING),
                new JudiciaryUnavailabilityResponse(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 1, 4), UnavailabilityReason.ANNUAL_LEAVE),
                new JudiciaryUnavailabilityResponse(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 6), UnavailabilityReason.OFFICIAL_BUSINESS),
                new JudiciaryUnavailabilityResponse(LocalDate.of(2026, 1, 7), LocalDate.of(2026, 1, 8), UnavailabilityReason.SICK_LEAVE)
        ));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray("rules");
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");

        assertThat(unavailabilitiesArray.size(), is(4));
        assertThat(unavailabilitiesArray.getJsonObject(0).getString("reason"), is("TRAINING"));
        assertThat(unavailabilitiesArray.getJsonObject(1).getString("reason"), is("ANNUAL_LEAVE"));
        assertThat(unavailabilitiesArray.getJsonObject(2).getString("reason"), is("OFFICIAL_BUSINESS"));
        assertThat(unavailabilitiesArray.getJsonObject(3).getString("reason"), is("SICK_LEAVE"));
    }

    private JudiciaryAvailabilityRuleResponse createBasicRule() {
        final JudiciaryAvailabilityRuleResponse rule = new JudiciaryAvailabilityRuleResponse();
        rule.setId(randomUUID().toString());
        rule.setJudiciaryId(randomUUID().toString());
        rule.setCourtHouseId(randomUUID().toString());
        rule.setStartDate(LocalDate.of(2026, 1, 1));
        rule.setEndDate(LocalDate.of(2026, 1, 31));
        return rule;
    }
}

