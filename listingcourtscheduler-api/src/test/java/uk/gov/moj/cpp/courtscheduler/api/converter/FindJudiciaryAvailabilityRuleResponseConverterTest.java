package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialism;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindJudiciaryAvailabilityRuleResponseConverterTest {
    private static final String END_DATE = "endDate";
    private static final String REASON = "reason";
    private static final String REPEAT_DAYS = "repeatDays";
    private static final String RULES = "rules";
    private static final String SESSION_TYPE = "sessionType";
    private static final String START_DATE = "startDate";
    private static final String UNAVAILABILITIES = "unavailabilities";


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

        final List<uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek> repeatDays = Arrays.asList(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.MONDAY, uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.TUESDAY);

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
        rule.setSessionType(SessionType.AM);
        rule.setRepeatDays(repeatDays);
        rule.setUnavailabilities(unavailabilities);

        final Judiciary judiciary = new Judiciary();
        judiciary.setId(judiciaryId);
        judiciary.setSurname("Smith");
        judiciary.setRequestedName("MR RECORDER J SMITH");

        final JudiciarySpecialism specialism = new JudiciarySpecialism();
        specialism.setJudiciaryId(judiciaryId);
        specialism.setSpecialisms(Arrays.asList(JudiciarySpecialismType.MURDER, JudiciarySpecialismType.ATTEMPTED_MURDER));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);
        response.setJudiciaries(Arrays.asList(judiciary));
        final JsonObject result = converter.convert(response);

        assertNotNull(result);
        assertThat(result.getInt("totalCount"), is(1));
        assertThat(result.getInt("pageNumber"), is(1));
        assertThat(result.getInt("pageSize"), is(20));

        // Verify rules array
        final JsonArray rulesArray = result.getJsonArray(RULES);
        assertNotNull(rulesArray);
        assertThat(rulesArray.size(), is(1));

        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        assertThat(ruleObject.getString("id"), is(ruleId));
        assertThat(ruleObject.getString("judiciaryId"), is(judiciaryId));
        assertThat(ruleObject.getString("courtHouseId"), is(courtHouseId));
        assertThat(ruleObject.getString(START_DATE), is("2026-01-01"));
        assertThat(ruleObject.getString(END_DATE), is("2026-01-31"));
        assertThat(ruleObject.getString(SESSION_TYPE), is("AM"));

        // Verify repeatDays - simple string array
        final JsonArray repeatDaysArray = ruleObject.getJsonArray(REPEAT_DAYS);
        assertThat(repeatDaysArray.size(), is(2));
        assertThat(repeatDaysArray.getString(0), is("Monday"));
        assertThat(repeatDaysArray.getString(1), is("Tuesday"));

        // Verify unavailabilities
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray(UNAVAILABILITIES);
        assertThat(unavailabilitiesArray.size(), is(1));
        final JsonObject unavailabilityObject = unavailabilitiesArray.getJsonObject(0);
        assertThat(unavailabilityObject.getString(START_DATE), is("2026-01-10"));
        assertThat(unavailabilityObject.getString(END_DATE), is("2026-01-12"));
        assertThat(unavailabilityObject.getString(REASON), is("ANNUAL_LEAVE"));

        // Verify judiciaries array
        final JsonArray judiciariesArray = result.getJsonArray("judiciaries");
        assertNotNull(judiciariesArray);
        assertThat(judiciariesArray.size(), is(1));
        
        // Verify judiciary fields including requestedName
        final JsonObject judiciaryObject = judiciariesArray.getJsonObject(0);
        assertThat(judiciaryObject.getString("id"), is(judiciaryId));
        assertThat(judiciaryObject.getString("surname"), is("Smith"));
        assertThat(judiciaryObject.getString("requestedName"), is("MR RECORDER J SMITH"));
    }

    @Test
    void shouldConvertResponseWithNullListsAsEmptyArrays() {
        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(new ArrayList<>());
        response.setTotalCount(0);
        response.setPageNumber(1);
        response.setPageSize(20);
        response.setJudiciaries(null);

        final JsonObject result = converter.convert(response);

        assertNotNull(result);
        assertThat(result.getInt("totalCount"), is(0));
        final JsonArray rulesArray = result.getJsonArray(RULES);
        assertNotNull(rulesArray);
        assertThat(rulesArray.size(), is(0));
        final JsonArray judiciariesArray = result.getJsonArray("judiciaries");
        assertNotNull(judiciariesArray);
        assertThat(judiciariesArray.size(), is(0));
    }

    @Test
    void shouldConvertRepeatDaysAsStringArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRepeatDays(Arrays.asList(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.MONDAY, uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.TUESDAY, uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.FRIDAY));

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray repeatDaysArray = ruleObject.getJsonArray(REPEAT_DAYS);

        assertThat(repeatDaysArray.size(), is(3));
        assertThat(repeatDaysArray.getString(0), is("Monday"));
        assertThat(repeatDaysArray.getString(1), is("Tuesday"));
        assertThat(repeatDaysArray.getString(2), is("Friday"));
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
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray(UNAVAILABILITIES);

        assertThat(unavailabilitiesArray.size(), is(1));
        final JsonObject unavailabilityObject = unavailabilitiesArray.getJsonObject(0);
        assertThat(unavailabilityObject.getString(START_DATE), is("2026-02-01"));
        assertThat(unavailabilityObject.getString(END_DATE), is("2026-02-05"));
        assertThat(unavailabilityObject.getString(REASON), is("TRAINING"));
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
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray(UNAVAILABILITIES);

        assertThat(unavailabilitiesArray.size(), is(1));
        final JsonObject unavailabilityObject = unavailabilitiesArray.getJsonObject(0);
        assertThat(unavailabilityObject.getString(START_DATE), is("2026-02-01"));
        assertThat(unavailabilityObject.getString(END_DATE), is("2026-02-05"));
        assertTrue(!unavailabilityObject.containsKey(REASON) || unavailabilityObject.isNull(REASON));
    }

    @Test
    void shouldConvertRuleWithoutOptionalFields() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setSessionType(null);

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);

        assertThat(ruleObject.getString("id"), is(notNullValue()));
        assertThat(ruleObject.getString("judiciaryId"), is(notNullValue()));
        assertTrue(!ruleObject.containsKey(SESSION_TYPE) || ruleObject.isNull(SESSION_TYPE));
    }

    @Test
    void shouldConvertRuleWithAllOptionalFields() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setSessionType(SessionType.PM);

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);

        final JsonObject result = converter.convert(response);
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);

        assertThat(ruleObject.getString(SESSION_TYPE), is("PM"));
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
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray repeatDaysArray = ruleObject.getJsonArray(REPEAT_DAYS);

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
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray repeatDaysArray = ruleObject.getJsonArray(REPEAT_DAYS);

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
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray(UNAVAILABILITIES);

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
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray(UNAVAILABILITIES);

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
        final JsonArray rulesArray = result.getJsonArray(RULES);

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
        final JsonArray rulesArray = result.getJsonArray(RULES);
        final JsonObject ruleObject = rulesArray.getJsonObject(0);
        final JsonArray unavailabilitiesArray = ruleObject.getJsonArray(UNAVAILABILITIES);

        assertThat(unavailabilitiesArray.size(), is(4));
        assertThat(unavailabilitiesArray.getJsonObject(0).getString(REASON), is("TRAINING"));
        assertThat(unavailabilitiesArray.getJsonObject(1).getString(REASON), is("ANNUAL_LEAVE"));
        assertThat(unavailabilitiesArray.getJsonObject(2).getString(REASON), is("OFFICIAL_BUSINESS"));
        assertThat(unavailabilitiesArray.getJsonObject(3).getString(REASON), is("SICK_LEAVE"));
    }

    @Test
    void shouldConvertJudiciaryWithNullRequestedName() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        final Judiciary judiciary = new Judiciary();
        judiciary.setId(rule.getJudiciaryId());
        judiciary.setSurname("Doe");
        judiciary.setRequestedName(null);

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse();
        response.setRules(Arrays.asList(rule));
        response.setTotalCount(1);
        response.setPageNumber(1);
        response.setPageSize(20);
        response.setJudiciaries(Arrays.asList(judiciary));

        final JsonObject result = converter.convert(response);
        final JsonArray judiciariesArray = result.getJsonArray("judiciaries");
        final JsonObject judiciaryObject = judiciariesArray.getJsonObject(0);

        assertThat(judiciaryObject.getString("id"), is(rule.getJudiciaryId()));
        assertThat(judiciaryObject.getString("surname"), is("Doe"));
        // requestedName should be null or not present when null
        assertTrue(!judiciaryObject.containsKey("requestedName") || judiciaryObject.isNull("requestedName"));
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

