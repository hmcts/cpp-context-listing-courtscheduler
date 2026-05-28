package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

import java.time.LocalDate;
import java.util.Arrays;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetJudiciaryAvailabilityRuleResponseConverterTest {

    private GetJudiciaryAvailabilityRuleResponseConverter converter;

    @BeforeEach
    void setUp() {
        converter = new GetJudiciaryAvailabilityRuleResponseConverter();
    }

    @Test
    void shouldConvertFullResponseWithAllFields() {
        final String ruleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();
        final String courtHouseId = randomUUID().toString();
        final LocalDate startDate = LocalDate.of(2026, 1, 1);
        final LocalDate endDate = LocalDate.of(2026, 1, 31);

        final JudiciaryAvailabilityRuleResponse rule = new JudiciaryAvailabilityRuleResponse();
        rule.setId(ruleId);
        rule.setJudiciaryId(judiciaryId);
        rule.setCourtHouseId(courtHouseId);
        rule.setStartDate(startDate);
        rule.setEndDate(endDate);
        rule.setSessionType(SessionType.AM);
        rule.setRepeatDays(Arrays.asList(uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.Monday, uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek.Tuesday));
        rule.setUnavailabilities(Arrays.asList(
                new JudiciaryUnavailabilityResponse(
                        LocalDate.of(2026, 1, 10),
                        LocalDate.of(2026, 1, 12),
                        UnavailabilityReason.ANNUAL_LEAVE
                )
        ));

        final Judiciary judiciary = new Judiciary();
        judiciary.setId(judiciaryId);
        judiciary.setSurname("Smith");

        final GetJudiciaryAvailabilityRuleResponse response = new GetJudiciaryAvailabilityRuleResponse(rule, judiciary);
        final JsonObject result = converter.convert(response);

        assertNotNull(result);
        
        // Verify rule object
        final JsonObject ruleObject = result.getJsonObject("rule");
        assertNotNull(ruleObject);
        assertThat(ruleObject.getString("id"), is(ruleId));
        assertThat(ruleObject.getString("judiciaryId"), is(judiciaryId));
        assertThat(ruleObject.getString("courtHouseId"), is(courtHouseId));
        assertThat(ruleObject.getString("startDate"), is("2026-01-01"));
        assertThat(ruleObject.getString("endDate"), is("2026-01-31"));
        assertThat(ruleObject.getString("sessionType"), is("AM"));

        // Verify repeatDays - simple string array
        final jakarta.json.JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");
        assertThat(repeatDaysArray.size(), is(2));
        assertThat(repeatDaysArray.getString(0), is("Monday"));
        assertThat(repeatDaysArray.getString(1), is("Tuesday"));

        // Verify unavailabilities
        final jakarta.json.JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");
        assertThat(unavailabilitiesArray.size(), is(1));
        final JsonObject unavailabilityObject = unavailabilitiesArray.getJsonObject(0);
        assertThat(unavailabilityObject.getString("startDate"), is("2026-01-10"));
        assertThat(unavailabilityObject.getString("endDate"), is("2026-01-12"));
        assertThat(unavailabilityObject.getString("reason"), is("ANNUAL_LEAVE"));

        // Verify judiciary object
        final JsonObject judiciaryObject = result.getJsonObject("judiciary");
        assertNotNull(judiciaryObject);
        assertThat(judiciaryObject.getString("id"), is(judiciaryId));
        assertThat(judiciaryObject.getString("surname"), is("Smith"));
    }

    @Test
    void shouldConvertResponseWithNullJudiciary() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        final GetJudiciaryAvailabilityRuleResponse response = new GetJudiciaryAvailabilityRuleResponse(rule, null);

        final JsonObject result = converter.convert(response);

        assertNotNull(result);
        assertNotNull(result.getJsonObject("rule"));
        assertTrue(result.isNull("judiciary"));
    }

    @Test
    void shouldConvertRuleWithoutOptionalFields() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setSessionType(null);

        final GetJudiciaryAvailabilityRuleResponse response = new GetJudiciaryAvailabilityRuleResponse(rule, null);
        final JsonObject result = converter.convert(response);
        final JsonObject ruleObject = result.getJsonObject("rule");

        assertThat(ruleObject.getString("id"), is(notNullValue()));
        assertThat(ruleObject.getString("judiciaryId"), is(notNullValue()));
        assertTrue(!ruleObject.containsKey("sessionType") || ruleObject.isNull("sessionType"));
    }

    @Test
    void shouldConvertRuleWithAllOptionalFields() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setSessionType(SessionType.PM);

        final GetJudiciaryAvailabilityRuleResponse response = new GetJudiciaryAvailabilityRuleResponse(rule, null);
        final JsonObject result = converter.convert(response);
        final JsonObject ruleObject = result.getJsonObject("rule");

        assertThat(ruleObject.getString("sessionType"), is("PM"));
    }

    @Test
    void shouldConvertEmptyRepeatDaysAsEmptyArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRepeatDays(Arrays.asList());

        final GetJudiciaryAvailabilityRuleResponse response = new GetJudiciaryAvailabilityRuleResponse(rule, null);
        final JsonObject result = converter.convert(response);
        final JsonObject ruleObject = result.getJsonObject("rule");
        final jakarta.json.JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");

        assertNotNull(repeatDaysArray);
        assertThat(repeatDaysArray.size(), is(0));
    }

    @Test
    void shouldConvertNullRepeatDaysAsEmptyArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setRepeatDays(null);

        final GetJudiciaryAvailabilityRuleResponse response = new GetJudiciaryAvailabilityRuleResponse(rule, null);
        final JsonObject result = converter.convert(response);
        final JsonObject ruleObject = result.getJsonObject("rule");
        final jakarta.json.JsonArray repeatDaysArray = ruleObject.getJsonArray("repeatDays");

        assertNotNull(repeatDaysArray);
        assertThat(repeatDaysArray.size(), is(0));
    }

    @Test
    void shouldConvertEmptyUnavailabilitiesAsEmptyArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setUnavailabilities(Arrays.asList());

        final GetJudiciaryAvailabilityRuleResponse response = new GetJudiciaryAvailabilityRuleResponse(rule, null);
        final JsonObject result = converter.convert(response);
        final JsonObject ruleObject = result.getJsonObject("rule");
        final jakarta.json.JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");

        assertNotNull(unavailabilitiesArray);
        assertThat(unavailabilitiesArray.size(), is(0));
    }

    @Test
    void shouldConvertNullUnavailabilitiesAsEmptyArray() {
        final JudiciaryAvailabilityRuleResponse rule = createBasicRule();
        rule.setUnavailabilities(null);

        final GetJudiciaryAvailabilityRuleResponse response = new GetJudiciaryAvailabilityRuleResponse(rule, null);
        final JsonObject result = converter.convert(response);
        final JsonObject ruleObject = result.getJsonObject("rule");
        final jakarta.json.JsonArray unavailabilitiesArray = ruleObject.getJsonArray("unavailabilities");

        assertNotNull(unavailabilitiesArray);
        assertThat(unavailabilitiesArray.size(), is(0));
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
