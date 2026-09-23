package uk.gov.moj.cpp.courtscheduler.api.validator;

import org.springframework.stereotype.Service;

import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.ENTER_END_DATE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.ENTER_START_DATE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.SELECT_COURTHOUSE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.SELECT_DAY_OF_WEEK;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.SELECT_JUDICIARY;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.SELECT_REPEAT_DAYS;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.START_DATE_MUST_BE_BEFORE_OR_EQUAL_TO_END_DATE;

import uk.gov.moj.cpp.courtscheduler.openapi.model.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;

import java.util.List;

import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JudiciaryAvailabilityRuleApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryAvailabilityRuleApiValidator.class.getName());
    private static final String REQUEST_FIELD = "Request";
    private static final String RULE_ID_FIELD = "ruleId";

    public JsonObject validateAddJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Validating AddJudiciaryAvailabilityRule input : {}", request);
        return validateRequestWithDetails(request, false, true);
    }

    public JsonObject validateUpdateJudiciaryAvailabilityRule(final UpdateJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Validating UpdateJudiciaryAvailabilityRule input : {}", request);
        return validateRequestWithDetails(request, true, true);
    }

    public JsonObject validateDeleteJudiciaryAvailabilityRule(final DeleteJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Validating DeleteJudiciaryAvailabilityRule input : {}", request);
        if (request == null) {
            return getMessage(REQUEST_FIELD);
        }

        if (isBlank(request.getRuleId())) {
            return getMessage(RULE_ID_FIELD);
        }

        return EMPTY_JSON_OBJECT;
    }

    // Note: AddJudiciaryAvailabilityRuleRequest and UpdateJudiciaryAvailabilityRuleRequest no longer
    // share a common generated supertype (the openapi-generator flattens allOf into duplicated
    // fields, verified by generating and reading the output) even though they have an identical
    // field shape, so the request-with-details validation below is overloaded per concrete type
    // instead of relying on a shared base type / instanceof check.
    private JsonObject validateRequestWithDetails(final AddJudiciaryAvailabilityRuleRequest request, final boolean validateRuleId, final boolean validateJudiciaryId) {
        if (request == null) {
            return getMessage(REQUEST_FIELD);
        }
        return validateRequestWithDetails(request.getRuleId(), request.getJudiciaryId(), request.getCourtHouseId(),
                request.getStartDate(), request.getEndDate(), request.getRepeatDays(), validateRuleId, validateJudiciaryId);
    }

    private JsonObject validateRequestWithDetails(final UpdateJudiciaryAvailabilityRuleRequest request, final boolean validateRuleId, final boolean validateJudiciaryId) {
        if (request == null) {
            return getMessage(REQUEST_FIELD);
        }
        return validateRequestWithDetails(request.getRuleId(), request.getJudiciaryId(), request.getCourtHouseId(),
                request.getStartDate(), request.getEndDate(), request.getRepeatDays(), validateRuleId, validateJudiciaryId);
    }

    private JsonObject validateRequestWithDetails(final String ruleId, final String judiciaryId, final String courtHouseId,
                                                   final java.time.LocalDate startDate, final java.time.LocalDate endDate,
                                                   final List<String> repeatDays,
                                                   final boolean validateRuleId, final boolean validateJudiciaryId) {
        if (validateRuleId && isBlank(ruleId)) {
            return getMessage(RULE_ID_FIELD);
        }

        if (validateJudiciaryId && isBlank(judiciaryId)) {
            return buildErrorResponse(SELECT_JUDICIARY);
        }

        JsonObject validation = validateBaseFields(courtHouseId, startDate, endDate);
        if (!validation.isEmpty()) {
            return validation;
        }

        validation = validateRepeatDays(repeatDays);
        if (!validation.isEmpty()) {
            return validation;
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateBaseFields(final AddJudiciaryAvailabilityRuleRequest request) {
        return validateBaseFields(request.getCourtHouseId(), request.getStartDate(), request.getEndDate());
    }

    private JsonObject validateBaseFields(final UpdateJudiciaryAvailabilityRuleRequest request) {
        return validateBaseFields(request.getCourtHouseId(), request.getStartDate(), request.getEndDate());
    }

    private JsonObject validateBaseFields(final String courtHouseId, final java.time.LocalDate startDate, final java.time.LocalDate endDate) {
        if (isBlank(courtHouseId)) {
            return buildErrorResponse("Select a courthouse");
        }

        if (startDate == null) {
            return buildErrorResponse("Enter a start date");
        }

        if (endDate == null) {
            return buildErrorResponse("Enter an end date");
        }

        if (startDate.isAfter(endDate)) {
            return buildErrorResponse("The start date must be the same as or before the end date");
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateRepeatDays(final List<String> repeatDays) {
        if (repeatDays == null || repeatDays.isEmpty()) {
            return buildErrorResponse(SELECT_REPEAT_DAYS);
        }

        for (String repeatDay : repeatDays) {
            if (repeatDay == null) {
                return buildErrorResponse(SELECT_DAY_OF_WEEK);
            }
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject getMessage(final String value) {
        return buildErrorResponse(MANDATORY_SEARCH_CRITERIA + value + CANNOT_BE_NULL);
    }

    private JsonObject buildErrorResponse(String errorMessage) {
        return createObjectBuilder()
                .add(ERROR_MESSAGE, errorMessage)
                .build();
    }

    private JsonObject validateRequestNotNull(final Object request) {
        if (request == null) {
            return getMessage(REQUEST_FIELD);
        }
        return EMPTY_JSON_OBJECT;
    }

    public JsonObject validateAddJudiciaryAvailabilityRuleForValidationEndpoint(final AddJudiciaryAvailabilityRuleRequest request, 
                                                                                final JudiciaryAvailabilityService service) {
        LOGGER.info("Validating AddJudiciaryAvailabilityRule for validation endpoint: {}", request);

        JsonObject validation = validateRequestNotNull(request);
        if (!validation.isEmpty()) {
            return validation;
        }

        validation = validateBaseFields(request);
        if (!validation.isEmpty()) {
            return validation;
        }

        validation = validateRepeatDays(request.getRepeatDays());
        if (!validation.isEmpty()) {
            return validation;
        }

        // Call service validation for business rules
        final String businessError = service.validateAddJudiciaryAvailabilityRule(request);
        if (businessError != null) {
            return buildErrorResponse(businessError);
        }

        return EMPTY_JSON_OBJECT;
    }

    public JsonObject validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(final UpdateJudiciaryAvailabilityRuleRequest request,
                                                                                    final JudiciaryAvailabilityService service) {
        LOGGER.info("Validating UpdateJudiciaryAvailabilityRule for validation endpoint: {}", request);

        JsonObject validation = validateRequestNotNull(request);
        if (!validation.isEmpty()) {
            return validation;
        }

        if (isBlank(request.getRuleId())) {
            return getMessage(RULE_ID_FIELD);
        }

        validation = validateBaseFields(request);
        if (!validation.isEmpty()) {
            return validation;
        }

        validation = validateRepeatDays(request.getRepeatDays());
        if (!validation.isEmpty()) {
            return validation;
        }

        // Call service validation for business rules
        final String businessError = service.validateUpdateJudiciaryAvailabilityRule(request);
        if (businessError != null) {
            return buildErrorResponse(businessError);
        }

        return EMPTY_JSON_OBJECT;
    }

    public JsonObject validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(final DeleteJudiciaryAvailabilityRuleRequest request,
                                                                                    final JudiciaryAvailabilityService service) {
        LOGGER.info("Validating DeleteJudiciaryAvailabilityRule for validation endpoint: {}", request);

        JsonObject validation = validateRequestNotNull(request);
        if (!validation.isEmpty()) {
            return validation;
        }

        if (isBlank(request.getRuleId())) {
            return getMessage(RULE_ID_FIELD);
        }

        // Call service validation for business rules (check if rule is applied to sessions)
        final String businessError = service.validateDeleteJudiciaryAvailabilityRule(request);
        if (businessError != null) {
            return buildErrorResponse(businessError);
        }

        return EMPTY_JSON_OBJECT;
    }
}

