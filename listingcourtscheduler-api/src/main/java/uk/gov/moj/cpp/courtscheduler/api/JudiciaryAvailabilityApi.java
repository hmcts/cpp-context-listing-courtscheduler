package uk.gov.moj.cpp.courtscheduler.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.time.LocalDate;
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
import uk.gov.moj.cpp.courtscheduler.api.service.JudiciaryAvailabilityService;
import uk.gov.moj.cpp.courtscheduler.api.service.SearchAvailableJudiciariesService;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciaryAvailabilityRuleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.UnprocessableEntityException;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Judiciary;
import uk.gov.moj.cpp.courtscheduler.openapi.api.JudiciaryAvailabilityOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerFindJudiciaryAvailabilityRuleQuery;
import uk.gov.moj.cpp.courtscheduler.openapi.model.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateJudiciaryAvailabilityRuleRequest;

import jakarta.json.JsonObject;

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
    private final ObjectMapper objectMapper;

    public JudiciaryAvailabilityApi(final JudiciaryAvailabilityService judiciaryAvailabilityService,
                                    final SearchAvailableJudiciariesService searchAvailableJudiciariesService,
                                    final JudiciaryAvailabilityRuleApiValidator validator,
                                    final ObjectMapper objectMapper) {
        this.judiciaryAvailabilityService = judiciaryAvailabilityService;
        this.searchAvailableJudiciariesService = searchAvailableJudiciariesService;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    // ----- POST /judiciaries/availability-rules/add -----
    @Override
    public ResponseEntity<Void> addJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        LOG.info("courtscheduler.judiciary.add.availability.rule: {}", request);
        final JsonObject errors = validator.validateAddJudiciaryAvailabilityRule(request);
        if (!errors.isEmpty()) {
            throw new UnprocessableEntityException(errors);
        }
        judiciaryAvailabilityService.addJudiciaryAvailabilityRule(request);
        return ResponseEntity.ok().build();
    }

    // ----- POST /judiciaries/availability-rules/update -----
    @Override
    public ResponseEntity<Void> updateJudiciaryAvailabilityRule(final UpdateJudiciaryAvailabilityRuleRequest request) {
        LOG.info("courtscheduler.judiciary.update.availability.rule: {}", request);
        final JsonObject errors = validator.validateUpdateJudiciaryAvailabilityRule(request);
        if (!errors.isEmpty()) {
            throw new UnprocessableEntityException(errors);
        }
        judiciaryAvailabilityService.updateJudiciaryAvailabilityRule(request);
        return ResponseEntity.ok().build();
    }

    // ----- POST /judiciaries/availability-rules/delete -----
    @Override
    public ResponseEntity<Void> deleteJudiciaryAvailabilityRule(final DeleteJudiciaryAvailabilityRuleRequest request) {
        LOG.info("courtscheduler.judiciary.delete.availability.rule: {}", request);
        final JsonObject errors = validator.validateDeleteJudiciaryAvailabilityRule(request);
        if (!errors.isEmpty()) {
            throw new UnprocessableEntityException(errors);
        }
        judiciaryAvailabilityService.deleteJudiciaryAvailabilityRule(request);
        return ResponseEntity.ok().build();
    }

    // ----- POST /judiciaries/availability-rules/validate-add -----
    @Override
    public ResponseEntity<Map<String, Object>> validateAddJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        LOG.info("courtscheduler.judiciary.add.availability.rule.validate: {}", request);
        final JsonObject errors = validator.validateAddJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService);
        return validationResponse(errors);
    }

    // ----- POST /judiciaries/availability-rules/validate-update -----
    @Override
    public ResponseEntity<Map<String, Object>> validateUpdateJudiciaryAvailabilityRule(final UpdateJudiciaryAvailabilityRuleRequest request) {
        LOG.info("courtscheduler.judiciary.update.availability.rule.validate: {}", request);
        final JsonObject errors = validator.validateUpdateJudiciaryAvailabilityRuleForValidationEndpoint(request, judiciaryAvailabilityService);
        return validationResponse(errors);
    }

    // ----- POST /judiciaries/availability-rules/validate-delete -----
    @Override
    public ResponseEntity<Map<String, Object>> validateDeleteJudiciaryAvailabilityRule(final DeleteJudiciaryAvailabilityRuleRequest request) {
        LOG.info("courtscheduler.judiciary.delete.availability.rule.validate: {}", request);
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
    public ResponseEntity<FindJudiciaryAvailabilityRuleResponse> findJudiciaryAvailabilityRules(final String startDate,
                                                                                                       final String endDate,
                                                                                                       final String courtCentreId,
                                                                                                       final Integer pageSize,
                                                                                                       final Integer pageNumber,
                                                                                                       final Boolean withJudiciary) {
        LOG.info("courtscheduler.judiciary.find.availability.rule startDate={}, endDate={}, courtCentreId={}",
                startDate, endDate, courtCentreId);
        final FindJudiciaryAvailabilityRuleResponse response = judiciaryAvailabilityService.findJudiciaryAvailabilityRules(
                new CourtschedulerFindJudiciaryAvailabilityRuleQuery()
                        .startDate(LocalDate.parse(startDate))
                        .endDate(LocalDate.parse(endDate))
                        .courtHouseId(courtCentreId)
                        .pageSize(pageSize)
                        .pageNumber(pageNumber)
                        .withJudiciary(withJudiciary));
        return ResponseEntity.ok(response);
    }

    // ----- GET /judiciaries/availability-rules/{ruleId} -----
    @Override
    public ResponseEntity<GetJudiciaryAvailabilityRuleResponse> getJudiciaryAvailabilityRule(final String ruleId,
                                                                                                    final Boolean withJudiciary) {
        LOG.info("courtscheduler.judiciary.get.availability.rule ruleId={}, withJudiciary={}", ruleId, withJudiciary);
        final GetJudiciaryAvailabilityRuleResponse response = judiciaryAvailabilityService.getJudiciaryAvailabilityRule(ruleId, withJudiciary);
        return ResponseEntity.ok(response);
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
        final var response = judiciaryAvailabilityService.findJudiciaryAvailability(
                LocalDate.parse(startDate), LocalDate.parse(endDate), courtCentreId, null);
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
