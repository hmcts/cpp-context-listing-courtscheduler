package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;

import uk.gov.justice.services.core.annotation.CustomServiceComponent;
import uk.gov.justice.services.core.annotation.Handles;
import uk.gov.justice.services.core.enveloper.Enveloper;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.courtscheduler.api.converter.AddJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.DeleteJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityRuleResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.GetJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.GetJudiciaryAvailabilityRuleResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciaryAvailabilityRuleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.UnprocessableEntityException;
import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@CustomServiceComponent("Courtscheduler.API")
@SuppressWarnings({"squid:S6813"})
public class JudiciaryAvailabilityApi {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryAvailabilityApi.class.getName());
    private static final String RULE_ID = "ruleId";
    private static final String VALIDATION_RESULT = "validationResult";
    private static final String STATUS = "status";
    private static final String VALIDATION_ERROR = "validationError";
    private static final String FAILURE = "FAILURE";
    private static final String SUCCESS = "SUCCESS";

    @Inject
    private Enveloper enveloper;

    @Inject
    private Requester requester;

    @Inject
    private AddJudiciaryAvailabilityRuleConverter addJudiciaryAvailabilityRuleConverter;

    @Inject
    private UpdateJudiciaryAvailabilityRuleConverter updateJudiciaryAvailabilityRuleConverter;

    @Inject
    private DeleteJudiciaryAvailabilityRuleConverter deleteJudiciaryAvailabilityRuleConverter;

    @Inject
    private FindJudiciaryAvailabilityConverter findJudiciaryAvailabilityConverter;

    @Inject
    private FindJudiciaryAvailabilityRuleConverter findJudiciaryAvailabilityRuleConverter;

    @Inject
    private JudiciaryAvailabilityService judiciaryAvailabilityService;

    @Inject
    private JudiciaryAvailabilityRuleApiValidator judiciaryAvailabilityRuleApiValidator;

    @Inject
    private FindJudiciaryAvailabilityRuleResponseConverter findJudiciaryAvailabilityRuleResponseConverter;

    @Inject
    private GetJudiciaryAvailabilityRuleConverter getJudiciaryAvailabilityRuleConverter;

    @Inject
    private GetJudiciaryAvailabilityRuleResponseConverter getJudiciaryAvailabilityRuleResponseConverter;

    @Handles("courtscheduler.judiciary.find.availability")
    public JsonEnvelope findJudiciaryAvailability(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.find.availability requested : {}", requestFromApiJsonObject);

        FindJudiciaryAvailabilityRequest request = findJudiciaryAvailabilityConverter.convert(requestFromApiJsonObject);
        FindJudiciaryAvailabilityResponse response = judiciaryAvailabilityService.findJudiciaryAvailability(request);

        // Build JSON array directly for strings (ListToJsonArrayConverter is for objects, not strings)
        final JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
        if (response.getAvailableJudiciaries() != null) {
            for (String judiciaryId : response.getAvailableJudiciaries()) {
                arrayBuilder.add(judiciaryId);
            }
        }
        final JsonValue result = arrayBuilder.build();

        return enveloper
                .withMetadataFrom(envelope, "courtscheduler.judiciary.find.availability")
                .apply(createObjectBuilder().add("availableJudiciaries", result).build());
    }

    @Handles("courtscheduler.judiciary.find.availability.rule")
    public JsonEnvelope findJudiciaryAvailabilityRules(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.find.availability.rule requested : {}", requestFromApiJsonObject);

        FindJudiciaryAvailabilityRuleRequest request = findJudiciaryAvailabilityRuleConverter.convert(requestFromApiJsonObject);
        FindJudiciaryAvailabilityRuleResponse response = judiciaryAvailabilityService.findJudiciaryAvailabilityRules(request, requester);

        final JsonObject responseObject = findJudiciaryAvailabilityRuleResponseConverter.convert(response);

        return enveloper
                .withMetadataFrom(envelope, "courtscheduler.judiciary.find.availability.rule")
                .apply(responseObject);
    }

    @Handles("courtscheduler.judiciary.add.availability.rule")
    public JsonEnvelope addJudiciaryAvailabilityRule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.add.availability.rule requested : {}", requestFromApiJsonObject);

        AddJudiciaryAvailabilityRuleRequest request = addJudiciaryAvailabilityRuleConverter.convert(requestFromApiJsonObject);
        validateAndThrowIfError(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService));

        judiciaryAvailabilityService.addJudiciaryAvailabilityRule(request);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.judiciary.add.availability.rule").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.judiciary.update.availability.rule")
    public JsonEnvelope updateJudiciaryAvailabilityRule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.update.availability.rule requested : {}", requestFromApiJsonObject);

        UpdateJudiciaryAvailabilityRuleRequest request = updateJudiciaryAvailabilityRuleConverter.convert(requestFromApiJsonObject);
        extractAndSetRuleId(requestFromApiJsonObject, request);

        validateAndThrowIfError(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService));

        judiciaryAvailabilityService.updateJudiciaryAvailabilityRule(request);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.judiciary.update.availability.rule").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.judiciary.delete.availability.rule")
    public JsonEnvelope deleteJudiciaryAvailabilityRule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.delete.availability.rule requested : {}", requestFromApiJsonObject);

        DeleteJudiciaryAvailabilityRuleRequest request = deleteJudiciaryAvailabilityRuleConverter.convert(requestFromApiJsonObject);

        validateAndThrowIfError(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService));

        judiciaryAvailabilityService.deleteJudiciaryAvailabilityRule(request);

        return enveloper.withMetadataFrom(envelope, "courtscheduler.judiciary.delete.availability.rule").apply(createObjectBuilder().build());
    }

    @Handles("courtscheduler.judiciary.get.availability.rule")
    public JsonEnvelope getJudiciaryAvailabilityRule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.get.availability.rule requested : {}", requestFromApiJsonObject);

        uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleRequest request = getJudiciaryAvailabilityRuleConverter.convert(requestFromApiJsonObject);
        uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse response = judiciaryAvailabilityService.getJudiciaryAvailabilityRule(request, requester);

        final JsonObject responseObject = getJudiciaryAvailabilityRuleResponseConverter.convert(response);

        return enveloper
                .withMetadataFrom(envelope, "courtscheduler.judiciary.get.availability.rule")
                .apply(responseObject);
    }

    @Handles("courtscheduler.judiciary.add.availability.rule.validate")
    public JsonEnvelope validateAddJudiciaryAvailabilityRule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.add.availability.rule.validate requested : {}", requestFromApiJsonObject);

        AddJudiciaryAvailabilityRuleRequest request = addJudiciaryAvailabilityRuleConverter.convert(requestFromApiJsonObject);
        validateAndThrowIfError(judiciaryAvailabilityRuleApiValidator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService));

        return enveloper.withMetadataFrom(envelope, "courtscheduler.judiciary.add.availability.rule.validate")
                .apply(createValidationSuccessResponse());
    }

    @Handles("courtscheduler.judiciary.update.availability.rule.validate")
    public JsonEnvelope validateUpdateJudiciaryAvailabilityRule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.update.availability.rule.validate requested : {}", requestFromApiJsonObject);

        UpdateJudiciaryAvailabilityRuleRequest request = updateJudiciaryAvailabilityRuleConverter.convert(requestFromApiJsonObject);
        extractAndSetRuleId(requestFromApiJsonObject, request);

        validateAndThrowIfError(judiciaryAvailabilityRuleApiValidator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService));

        return enveloper.withMetadataFrom(envelope, "courtscheduler.judiciary.update.availability.rule.validate")
                .apply(createValidationSuccessResponse());
    }

    @Handles("courtscheduler.judiciary.delete.availability.rule.validate")
    public JsonEnvelope validateDeleteJudiciaryAvailabilityRule(final JsonEnvelope envelope) {
        final JsonObject requestFromApiJsonObject = envelope.payloadAsJsonObject();
        LOGGER.info("courtscheduler.judiciary.delete.availability.rule.validate requested : {}", requestFromApiJsonObject);

        DeleteJudiciaryAvailabilityRuleRequest request = deleteJudiciaryAvailabilityRuleConverter.convert(requestFromApiJsonObject);

        validateAndThrowIfError(judiciaryAvailabilityRuleApiValidator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService));

        return enveloper.withMetadataFrom(envelope, "courtscheduler.judiciary.delete.availability.rule.validate")
                .apply(createValidationSuccessResponse());
    }

    private void validateAndThrowIfError(final JsonObject validate) {
        if (!validate.isEmpty()) {
            final String errorMessage = validate.getString(ERROR_MESSAGE);
            final JsonObject validationResult = createObjectBuilder()
                    .add(VALIDATION_RESULT, createObjectBuilder()
                            .add(STATUS, FAILURE)
                            .add(VALIDATION_ERROR, errorMessage)
                            .build())
                    .build();
            throw new UnprocessableEntityException(validationResult);
        }
    }

    private JsonObject createValidationSuccessResponse() {
        return createObjectBuilder()
                .add(VALIDATION_RESULT, createObjectBuilder()
                        .add(STATUS, SUCCESS)
                        .build())
                .build();
    }

    private void extractAndSetRuleId(final JsonObject requestFromApiJsonObject, final UpdateJudiciaryAvailabilityRuleRequest request) {
        final String ruleId = requestFromApiJsonObject.containsKey(RULE_ID) ?
                requestFromApiJsonObject.getString(RULE_ID) : null;

        if (ruleId != null && (request.getRuleId() == null || request.getRuleId().isEmpty())) {
            request.setRuleId(ruleId);
        }
    }
}
