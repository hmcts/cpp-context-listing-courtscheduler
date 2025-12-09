package uk.gov.moj.cpp.courtscheduler.api.validator;

import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;

import java.util.List;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JudiciaryAvailabilityRuleApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryAvailabilityRuleApiValidator.class.getName());

    public JsonObject validateAddJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Validating AddJudiciaryAvailabilityRule input : {}", request);

        if (request == null) {
            return getMessage("Request");
        }

        if (isBlank(request.getJudiciaryId())) {
            return getMessage("judiciaryId");
        }

        if (isBlank(request.getCourtHouseId())) {
            return getMessage("courtHouseId");
        }

        if (request.getStartDate() == null) {
            return getMessage("startDate");
        }

        if (request.getEndDate() == null) {
            return getMessage("endDate");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            return buildErrorResponse("startDate must be before or equal to endDate");
        }

        if (request.getRepeatDays() == null || request.getRepeatDays().isEmpty()) {
            return getMessage("repeatDays");
        }

        final JsonObject repeatDaysValidation = validateRepeatDays(request.getRepeatDays());
        if (!EMPTY_JSON_OBJECT.equals(repeatDaysValidation)) {
            return repeatDaysValidation;
        }

        return EMPTY_JSON_OBJECT;
    }

    public JsonObject validateDeleteJudiciaryAvailabilityRule(final DeleteJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Validating DeleteJudiciaryAvailabilityRule input : {}", request);

        if (request == null) {
            return getMessage("Request");
        }

        if (isBlank(request.getRuleId())) {
            return getMessage("ruleId");
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateRepeatDays(final List<JudiciaryAvailabilityRuleRepeatDay> repeatDays) {
        for (JudiciaryAvailabilityRuleRepeatDay repeatDay : repeatDays) {
            if (repeatDay.getDayOfWeek() == null) {
                return getMessage("repeatDays.dayOfWeek");
            }
        }
        return EMPTY_JSON_OBJECT;
    }

    private boolean isValidDayName(final String day) {
        if (day == null) {
            return false;
        }
        final String normalizedDay = day.trim();
        return "Monday".equalsIgnoreCase(normalizedDay) ||
                "Tuesday".equalsIgnoreCase(normalizedDay) ||
                "Wednesday".equalsIgnoreCase(normalizedDay) ||
                "Thursday".equalsIgnoreCase(normalizedDay) ||
                "Friday".equalsIgnoreCase(normalizedDay) ||
                "Saturday".equalsIgnoreCase(normalizedDay) ||
                "Sunday".equalsIgnoreCase(normalizedDay);
    }

    private JsonObject getMessage(final String value) {
        return buildErrorResponse(MANDATORY_SEARCH_CRITERIA + value + CANNOT_BE_NULL);
    }

    private JsonObject buildErrorResponse(String errorMessage) {
        return createObjectBuilder()
                .add(ERROR_MESSAGE, errorMessage)
                .build();
    }
}

