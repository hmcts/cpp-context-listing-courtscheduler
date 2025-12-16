package uk.gov.moj.cpp.courtscheduler.api.validator;

import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.BaseJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;

import java.util.List;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JudiciaryAvailabilityRuleApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryAvailabilityRuleApiValidator.class.getName());

    public JsonObject validateAddJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Validating AddJudiciaryAvailabilityRule input : {}", request);

        JsonObject validation = validateRequestNotNull(request);
        if (validation != null) {
            return validation;
        }

        validation = validateBaseFields(request);
        if (validation != null) {
            return validation;
        }

        validation = validateRepeatDays(request.getRepeatDays());
        if (validation != null) {
            return validation;
        }

        return EMPTY_JSON_OBJECT;
    }

    public JsonObject validateUpdateJudiciaryAvailabilityRule(final UpdateJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Validating UpdateJudiciaryAvailabilityRule input : {}", request);

        JsonObject validation = validateRequestNotNull(request);
        if (validation != null) {
            return validation;
        }

        if (isBlank(request.getRuleId())) {
            return getMessage("ruleId");
        }

        validation = validateBaseFields(request);
        if (validation != null) {
            return validation;
        }

        validation = validateRepeatDays(request.getRepeatDays());
        if (validation != null) {
            return validation;
        }

        return EMPTY_JSON_OBJECT;
    }

    public JsonObject validateDeleteJudiciaryAvailabilityRule(final DeleteJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Validating DeleteJudiciaryAvailabilityRule input : {}", request);

        JsonObject validation = validateRequestNotNull(request);
        if (validation != null) {
            return validation;
        }

        if (isBlank(request.getRuleId())) {
            return getMessage("ruleId");
        }

        return EMPTY_JSON_OBJECT;
    }

    /**
     * Validates common base fields shared by Add and Update requests.
     * Validates: judiciaryId, courtHouseId, startDate, endDate, and date range.
     */
    private JsonObject validateBaseFields(final BaseJudiciaryAvailabilityRuleRequest request) {
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

        return null;
    }

    /**
     * Validates that the request object is not null.
     */
    private JsonObject validateRequestNotNull(final Object request) {
        if (request == null) {
            return getMessage("Request");
        }
        return null;
    }

    /**
     * Validates that repeatDays is not null/empty and all days have valid dayOfWeek.
     */
    private JsonObject validateRepeatDays(final List<JudiciaryAvailabilityRuleRepeatDay> repeatDays) {
        if (repeatDays == null || repeatDays.isEmpty()) {
            return getMessage("repeatDays");
        }

        for (JudiciaryAvailabilityRuleRepeatDay repeatDay : repeatDays) {
            if (repeatDay.getDayOfWeek() == null) {
                return getMessage("repeatDays.dayOfWeek");
            }
        }

        return null;
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

