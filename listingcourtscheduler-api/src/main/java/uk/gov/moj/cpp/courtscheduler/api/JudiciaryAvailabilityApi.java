package uk.gov.moj.cpp.courtscheduler.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.moj.cpp.courtscheduler.api.converter.AddJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.DeleteJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.FindJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.GetJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateJudiciaryAvailabilityRuleConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;
import uk.gov.moj.cpp.courtscheduler.api.service.SearchAvailableJudiciariesService;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciaryAvailabilityRuleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.UnprocessableEntityException;
import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.api.JudiciaryAvailabilityOpenApi;

/**
 * Implements {@link JudiciaryAvailabilityOpenApi} — replaces the legacy
 * {@code @Handles} for {@code courtscheduler.judiciary.*}.
 *
 * <p>These endpoints negotiate as plain {@code application/json}, so
 * cp-auth-rules-filter cannot extract an action name from the media type.
 * Authorization is instead expressed in {@code uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl}
 * by matching on the request method and path attributes that
 * {@code HttpAuthzFilter} populates on every {@code Action}, so no separate
 * header-bridge filter is needed.</p>
 */
@RestController
public class JudiciaryAvailabilityApi implements JudiciaryAvailabilityOpenApi {

    private static final Logger LOG = LoggerFactory.getLogger(JudiciaryAvailabilityApi.class);

    private final JudiciaryAvailabilityService judiciaryAvailabilityService;
    private final SearchAvailableJudiciariesService searchAvailableJudiciariesService;
    private final JudiciaryAvailabilityRuleApiValidator validator;
    private final AddJudiciaryAvailabilityRuleConverter addConverter;
    private final UpdateJudiciaryAvailabilityRuleConverter updateConverter;
    private final DeleteJudiciaryAvailabilityRuleConverter deleteConverter;
    private final FindJudiciaryAvailabilityConverter findConverter;
    private final FindJudiciaryAvailabilityRuleConverter findRulesConverter;
    private final GetJudiciaryAvailabilityRuleConverter getRuleConverter;
    private final ObjectMapper objectMapper;

    public JudiciaryAvailabilityApi(final JudiciaryAvailabilityService judiciaryAvailabilityService,
                                    final SearchAvailableJudiciariesService searchAvailableJudiciariesService,
                                    final JudiciaryAvailabilityRuleApiValidator validator,
                                    final AddJudiciaryAvailabilityRuleConverter addConverter,
                                    final UpdateJudiciaryAvailabilityRuleConverter updateConverter,
                                    final DeleteJudiciaryAvailabilityRuleConverter deleteConverter,
                                    final FindJudiciaryAvailabilityConverter findConverter,
                                    final FindJudiciaryAvailabilityRuleConverter findRulesConverter,
                                    final GetJudiciaryAvailabilityRuleConverter getRuleConverter,
                                    final ObjectMapper objectMapper) {
        this.judiciaryAvailabilityService = judiciaryAvailabilityService;
        this.searchAvailableJudiciariesService = searchAvailableJudiciariesService;
        this.validator = validator;
        this.addConverter = addConverter;
        this.updateConverter = updateConverter;
        this.deleteConverter = deleteConverter;
        this.findConverter = findConverter;
        this.findRulesConverter = findRulesConverter;
        this.getRuleConverter = getRuleConverter;
        this.objectMapper = objectMapper;
    }

    private JsonObject toJsonObject(final Map<String, Object> body) {
        try {
            final String json = body == null ? "{}" : objectMapper.writeValueAsString(body);
            try (var reader = Json.createReader(new StringReader(json))) {
                return reader.readObject();
            }
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid request body", e);
        }
    }

    /** Map the few common query params for the GET endpoints into a JsonObject. */
    private JsonObject queryToJsonObject(final String startDate, final String endDate, final String courtCentreId,
                                         final Integer pageSize, final Integer pageNumber, final Boolean withJudiciary) {
        final JsonObjectBuilder b = Json.createObjectBuilder();
        if (startDate != null)     b.add("startDate", startDate);
        if (endDate != null)       b.add("endDate", endDate);
        if (courtCentreId != null) b.add("courtCentreId", courtCentreId);
        if (pageSize != null)      b.add("pageSize", pageSize);
        if (pageNumber != null)    b.add("pageNumber", pageNumber);
        if (withJudiciary != null) b.add("withJudiciary", withJudiciary);
        return b.build();
    }

    // ----- POST /judiciaries/availability-rules/add -----
    @Override
    public ResponseEntity<Void> addJudiciaryAvailabilityRule(final Map<String, Object> body) {
        LOG.info("courtscheduler.judiciary.add.availability.rule: {}", body);
        final AddJudiciaryAvailabilityRuleRequest request = addConverter.convert(toJsonObject(body));
        final JsonObject errors = validator.validateAddJudiciaryAvailabilityRule(request);
        if (!errors.isEmpty()) {
            throw new UnprocessableEntityException(errors);
        }
        judiciaryAvailabilityService.addJudiciaryAvailabilityRule(request);
        return ResponseEntity.ok().build();
    }

    // ----- POST /judiciaries/availability-rules/update -----
    @Override
    public ResponseEntity<Void> updateJudiciaryAvailabilityRule(final Map<String, Object> body) {
        LOG.info("courtscheduler.judiciary.update.availability.rule: {}", body);
        final UpdateJudiciaryAvailabilityRuleRequest request = updateConverter.convert(toJsonObject(body));
        final JsonObject errors = validator.validateUpdateJudiciaryAvailabilityRule(request);
        if (!errors.isEmpty()) {
            throw new UnprocessableEntityException(errors);
        }
        judiciaryAvailabilityService.updateJudiciaryAvailabilityRule(request);
        return ResponseEntity.ok().build();
    }

    // ----- POST /judiciaries/availability-rules/delete -----
    @Override
    public ResponseEntity<Void> deleteJudiciaryAvailabilityRule(final Map<String, Object> body) {
        LOG.info("courtscheduler.judiciary.delete.availability.rule: {}", body);
        final DeleteJudiciaryAvailabilityRuleRequest request = deleteConverter.convert(toJsonObject(body));
        final JsonObject errors = validator.validateDeleteJudiciaryAvailabilityRule(request);
        if (!errors.isEmpty()) {
            throw new UnprocessableEntityException(errors);
        }
        judiciaryAvailabilityService.deleteJudiciaryAvailabilityRule(request);
        return ResponseEntity.ok().build();
    }

    // ----- POST /judiciaries/availability-rules/validate-add -----
    @Override
    public ResponseEntity<Map<String, Object>> validateAddJudiciaryAvailabilityRule(final Map<String, Object> body) {
        LOG.info("courtscheduler.judiciary.add.availability.rule.validate: {}", body);
        final AddJudiciaryAvailabilityRuleRequest request = addConverter.convert(toJsonObject(body));
        final JsonObject errors = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService);
        return validationResponse(errors);
    }

    // ----- POST /judiciaries/availability-rules/validate-update -----
    @Override
    public ResponseEntity<Map<String, Object>> validateUpdateJudiciaryAvailabilityRule(final Map<String, Object> body) {
        LOG.info("courtscheduler.judiciary.update.availability.rule.validate: {}", body);
        final UpdateJudiciaryAvailabilityRuleRequest request = updateConverter.convert(toJsonObject(body));
        final JsonObject errors = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService);
        return validationResponse(errors);
    }

    // ----- POST /judiciaries/availability-rules/validate-delete -----
    @Override
    public ResponseEntity<Map<String, Object>> validateDeleteJudiciaryAvailabilityRule(final Map<String, Object> body) {
        LOG.info("courtscheduler.judiciary.delete.availability.rule.validate: {}", body);
        final DeleteJudiciaryAvailabilityRuleRequest request = deleteConverter.convert(toJsonObject(body));
        final JsonObject errors = validator.validateDeleteJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService);
        return validationResponse(errors);
    }

    /**
     * Validate-* endpoints follow the legacy convention:
     * <ul>
     *   <li>Validation passes -> 200 + {@code {validationResult:{status:"SUCCESS"}}}.</li>
     *   <li>Validation fails  -> 422 + {@code {validationResult:{status:"FAILURE","validationError":"<msg>"}}}.</li>
     * </ul>
     */
    private ResponseEntity<Map<String, Object>> validationResponse(final JsonObject errors) {
        final Map<String, Object> validationResult = new LinkedHashMap<>();
        if (errors == null || errors.isEmpty()) {
            validationResult.put("status", "SUCCESS");
            final Map<String, Object> body = new LinkedHashMap<>();
            body.put("validationResult", validationResult);
            return ResponseEntity.ok(body);
        }
        validationResult.put("status", "FAILURE");
        final String message = extractFirstString(errors);
        validationResult.put("validationError", message);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("validationResult", validationResult);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    private static String extractFirstString(final JsonObject errors) {
        for (final var entry : errors.entrySet()) {
            final var v = entry.getValue();
            if (v.getValueType() == jakarta.json.JsonValue.ValueType.STRING) {
                return ((jakarta.json.JsonString) v).getString();
            }
        }
        return errors.toString();
    }

    // ----- GET /judiciaries/availability-rules -----
    @Override
    public ResponseEntity<Map<String, Object>> findJudiciaryAvailabilityRules(final String startDate,
                                                                              final String endDate,
                                                                              final String courtCentreId,
                                                                              final Integer pageSize,
                                                                              final Integer pageNumber,
                                                                              final Boolean withJudiciary) {
        LOG.info("courtscheduler.judiciary.find.availability.rule startDate={}, endDate={}, courtCentreId={}",
                startDate, endDate, courtCentreId);
        final FindJudiciaryAvailabilityRuleRequest request = findRulesConverter.convert(
                queryToJsonObject(startDate, endDate, courtCentreId, pageSize, pageNumber, withJudiciary));
        final FindJudiciaryAvailabilityRuleResponse response = judiciaryAvailabilityService.findJudiciaryAvailabilityRules(request);
        return ResponseEntity.ok(toFlatMap(response));
    }

    // ----- GET /judiciaries/availability-rules/{ruleId} -----
    @Override
    public ResponseEntity<Map<String, Object>> getJudiciaryAvailabilityRule(final String ruleId,
                                                                            final Boolean withJudiciary) {
        LOG.info("courtscheduler.judiciary.get.availability.rule ruleId={}, withJudiciary={}", ruleId, withJudiciary);
        final JsonObjectBuilder b = Json.createObjectBuilder();
        b.add("ruleId", ruleId);
        if (withJudiciary != null) b.add("withJudiciary", withJudiciary);
        final GetJudiciaryAvailabilityRuleRequest request = getRuleConverter.convert(b.build());
        final GetJudiciaryAvailabilityRuleResponse response = judiciaryAvailabilityService.getJudiciaryAvailabilityRule(request);
        return ResponseEntity.ok(toFlatMap(response));
    }

    // ----- GET /judiciaries — dual action selected by Accept header -----
    @Override
    public ResponseEntity<Map<String, Object>> findJudiciaryAvailability(final String search,
                                                                         final String judiciaryGroup,
                                                                         final String limit,
                                                                         final String dates,
                                                                         final String courtHouseId,
                                                                         final String courtScheduleIds,
                                                                         final Boolean ignoreAvailability,
                                                                         final String startDate,
                                                                         final String endDate,
                                                                         final String courtCentreId,
                                                                         final Integer pageSize,
                                                                         final Integer pageNumber,
                                                                         final Boolean withJudiciary) {
        final String accept = currentAcceptHeader();
        if (accept != null && accept.contains("vnd.courtscheduler.search.available.judiciaries")) {
            return searchAvailableJudiciaries(search, judiciaryGroup, limit, dates,
                    courtHouseId, courtScheduleIds, ignoreAvailability);
        }
        LOG.info("courtscheduler.judiciary.find.availability startDate={}, endDate={}", startDate, endDate);
        final FindJudiciaryAvailabilityRequest request = findConverter.convert(
                queryToJsonObject(startDate, endDate, courtCentreId, pageSize, pageNumber, withJudiciary));
        final FindJudiciaryAvailabilityResponse response = judiciaryAvailabilityService.findJudiciaryAvailability(request);
        return ResponseEntity.ok(toFlatMap(response));
    }

    /** Typeahead judiciary search flavour of GET /judiciaries (vendor Accept). */
    private ResponseEntity<Map<String, Object>> searchAvailableJudiciaries(final String search,
                                                                           final String judiciaryGroup,
                                                                           final String limit,
                                                                           final String dates,
                                                                           final String courtHouseId,
                                                                           final String courtScheduleIds,
                                                                           final Boolean ignoreAvailability) {
        LOG.info("courtscheduler.search.available.judiciaries search={}, judiciaryGroup={}", search, judiciaryGroup);
        final JsonObjectBuilder b = Json.createObjectBuilder();
        if (search != null)           b.add("search", search);
        if (judiciaryGroup != null)   b.add("judiciaryGroup", judiciaryGroup);
        if (limit != null)            b.add("limit", limit);
        if (dates != null)            b.add("dates", dates);
        if (courtHouseId != null)     b.add("courtHouseId", courtHouseId);
        if (courtScheduleIds != null) b.add("courtScheduleIds", courtScheduleIds);
        if (ignoreAvailability != null) b.add("ignoreAvailability", ignoreAvailability);

        final List<Judiciary> judiciaries = searchAvailableJudiciariesService.search(b.build());

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("judiciaries", judiciaries);
        return ResponseEntity.ok(body);
    }

    private static String currentAcceptHeader() {
        final ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getRequest().getHeader("Accept");
    }

    /**
     * Flatten the response object into a top-level Map so its fields appear at the
     * envelope level, matching the legacy {@code Enveloper.apply(payload)} shape.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> toFlatMap(final Object response) {
        if (response == null) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.convertValue(response, Map.class);
        } catch (Exception e) {
            LOG.warn("Failed to flatten response of type {}: {}", response.getClass(), e.getMessage());
            final Map<String, Object> body = new LinkedHashMap<>();
            body.put("payload", response);
            return body;
        }
    }
}
