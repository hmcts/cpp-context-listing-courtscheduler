package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.Arrays.stream;
import static uk.gov.moj.cpp.courtscheduler.domain.SearchCourtSchedulesByIdRequestParam.SearchCourtSchedulesByIdRequestParamBuilder.searchCourtSchedulesByIdRequestParamBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.http.HttpServletRequest;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.moj.cpp.courtscheduler.api.converter.AssignCourtroomRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.AssignJudiciariesRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CreateSessionsRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.OuCodeMigrateConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.OuCodeRecalculateAvailabilityConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ProvisionalSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.SessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateCourtScheduleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ValidateSessionAvailabilityRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.MiService;
import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.api.validator.AssignJudiciariesApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciariesApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryUnassignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.config.JsonValueConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomResponse;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeMigrateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeRecalculateAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SearchCourtSchedulesByIdRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.envelope.SkipEnvelope;
import uk.gov.moj.cpp.courtscheduler.openapi.api.CourtscheduleOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.api.MiOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.api.OucodeOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.api.ProvisionalBookingOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.api.SessionOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.api.ValidateOpenApi;

/**
 * Spring Boot replacement for the legacy WildFly {@code CourtSchedulerApi} omnibus
 * controller. Implements all six OpenAPI-generated interfaces that originally lived
 * under the single {@code @CustomServiceComponent("Courtscheduler.API")} legacy class:
 * court schedule CRUD, session judiciary assignment, OU-code migration, MI exports,
 * validation, and provisional booking.
 *
 * <p>Kept as a single class deliberately so {@code git diff HEAD} highlights the
 * WildFly-to-Spring conversion against the original file (rather than producing
 * rename-shaped delete+add noise from a per-concern split).</p>
 */
@RestController
public class CourtSchedulerApi implements CourtscheduleOpenApi,
                                          SessionOpenApi,
                                          OucodeOpenApi,
                                          MiOpenApi,
                                          ValidateOpenApi,
                                          ProvisionalBookingOpenApi {

    private static final Logger LOG = LoggerFactory.getLogger(CourtSchedulerApi.class);

    private static final String ASSIGN_MT = "application/vnd.courtscheduler.assign-judiciary+json";
    private static final String UNASSIGN_MT = "application/vnd.courtscheduler.unassign.judiciary+json";
    private static final String CREATE_MT = "application/vnd.courtscheduler.validate.create+json";
    private static final String UPDATE_MT = "application/vnd.courtscheduler.validate.update+json";
    private static final String DELETE_MT = "application/vnd.courtscheduler.validate.delete+json";

    // --- shared infrastructure
    private final ObjectMapper objectMapper;
    private final HttpServletRequest request;

    // --- court schedule CRUD
    private final SessionsService sessionsService;
    private final SessionsApiValidator sessionsApiValidator;
    private final CourtScheduleApiValidator courtScheduleApiValidator;
    private final CreateSessionsRequestParamConverter createSessionsRequestParamConverter;
    private final UpdateCourtScheduleConverter updateCourtScheduleConverter;
    private final SessionsConverter sessionsConverter;
    private final AssignCourtroomRequestConverter assignCourtroomRequestConverter;
    private final ValidateSessionAvailabilityRequestParamConverter validateSessionAvailabilityRequestParamConverter;

    // --- session judiciary assignment / unassignment
    private final JudiciaryAssignmentService judiciaryAssignmentService;
    private final JudiciaryUnassignmentService judiciaryUnassignmentService;
    private final AssignJudiciariesApiValidator assignJudiciariesApiValidator;
    private final JudiciariesApiValidator judiciariesApiValidator;
    private final AssignJudiciariesRequestConverter assignJudiciariesRequestConverter;

    // --- OU code
    private final OuCodeMigrateConverter ouCodeMigrateConverter;
    private final OuCodeRecalculateAvailabilityConverter ouCodeRecalculateAvailabilityConverter;

    // --- MI exports
    private final MiService miService;
    private final MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter;

    // --- provisional booking
    private final ProvisionalBookingService provisionalBookingService;
    private final ProvisionalBookingApiValidator provisionalBookingApiValidator;
    private final ProvisionalSlotConverter provisionalSlotConverter;

    public CourtSchedulerApi(final ObjectMapper objectMapper,
                             final HttpServletRequest request,
                             final SessionsService sessionsService,
                             final SessionsApiValidator sessionsApiValidator,
                             final CourtScheduleApiValidator courtScheduleApiValidator,
                             final CreateSessionsRequestParamConverter createSessionsRequestParamConverter,
                             final UpdateCourtScheduleConverter updateCourtScheduleConverter,
                             final SessionsConverter sessionsConverter,
                             final AssignCourtroomRequestConverter assignCourtroomRequestConverter,
                             final ValidateSessionAvailabilityRequestParamConverter validateSessionAvailabilityRequestParamConverter,
                             final JudiciaryAssignmentService judiciaryAssignmentService,
                             final JudiciaryUnassignmentService judiciaryUnassignmentService,
                             final AssignJudiciariesApiValidator assignJudiciariesApiValidator,
                             final JudiciariesApiValidator judiciariesApiValidator,
                             final AssignJudiciariesRequestConverter assignJudiciariesRequestConverter,
                             final OuCodeMigrateConverter ouCodeMigrateConverter,
                             final OuCodeRecalculateAvailabilityConverter ouCodeRecalculateAvailabilityConverter,
                             final MiService miService,
                             final MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter,
                             final ProvisionalBookingService provisionalBookingService,
                             final ProvisionalBookingApiValidator provisionalBookingApiValidator,
                             final ProvisionalSlotConverter provisionalSlotConverter) {
        this.objectMapper = objectMapper;
        this.request = request;
        this.sessionsService = sessionsService;
        this.sessionsApiValidator = sessionsApiValidator;
        this.courtScheduleApiValidator = courtScheduleApiValidator;
        this.createSessionsRequestParamConverter = createSessionsRequestParamConverter;
        this.updateCourtScheduleConverter = updateCourtScheduleConverter;
        this.sessionsConverter = sessionsConverter;
        this.assignCourtroomRequestConverter = assignCourtroomRequestConverter;
        this.validateSessionAvailabilityRequestParamConverter = validateSessionAvailabilityRequestParamConverter;
        this.judiciaryAssignmentService = judiciaryAssignmentService;
        this.judiciaryUnassignmentService = judiciaryUnassignmentService;
        this.assignJudiciariesApiValidator = assignJudiciariesApiValidator;
        this.judiciariesApiValidator = judiciariesApiValidator;
        this.assignJudiciariesRequestConverter = assignJudiciariesRequestConverter;
        this.ouCodeMigrateConverter = ouCodeMigrateConverter;
        this.ouCodeRecalculateAvailabilityConverter = ouCodeRecalculateAvailabilityConverter;
        this.miService = miService;
        this.miFilterCriteriaRequestParamConverter = miFilterCriteriaRequestParamConverter;
        this.provisionalBookingService = provisionalBookingService;
        this.provisionalBookingApiValidator = provisionalBookingApiValidator;
        this.provisionalSlotConverter = provisionalSlotConverter;
    }

    /* ============================================================
     *  Shared helpers
     * ============================================================ */

    /** Map<String,Object> (Jackson) to jakarta.json.JsonObject expected by legacy converters. */
    private JsonObject toJsonObject(final Map<String, Object> body) {
        try (var reader = Json.createReader(new StringReader(toJson(body)))) {
            return reader.readObject();
        }
    }

    private String toJson(final Map<String, Object> body) {
        try {
            return body == null ? "{}" : objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid request body", e);
        }
    }

    /* ============================================================
     *  CourtscheduleOpenApi — court schedule CRUD
     * ============================================================ */

    @Override
    public ResponseEntity<Void> postCourtschedulerCreateCourtschedule(final Map<String, Object> body) {
        LOG.info("courtscheduler.create requested: {}", body);
        final CreateSessionRequestParam param = createSessionsRequestParamConverter.convert(toJsonObject(body));

        final JsonObject validate = sessionsApiValidator.getSessionsCreateValidation(param);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        sessionsService.create(param);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Override
    public ResponseEntity<Map<String, Object>> getCourtschedule(final String courtCentreId,
                                                                final String sessionStartDate,
                                                                final String sessionEndDate,
                                                                final String pageSize,
                                                                final String pageNumber,
                                                                final String courtRoomId,
                                                                final String businessType,
                                                                final Boolean isDraft) {
        LOG.info("courtscheduler.get.court_schedule courtCentreId={}, sessionStart={}, sessionEnd={}",
                courtCentreId, sessionStartDate, sessionEndDate);

        final CourtScheduleRequestParam param = new CourtScheduleRequestParam(
                courtCentreId, courtRoomId, businessType,
                sessionStartDate, sessionEndDate,
                isDraft, pageSize, pageNumber);

        final JsonObject validate = courtScheduleApiValidator.getCourtSchedulesValidation(param);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        final List<CourtSchedule> courtSchedules = sessionsService.getCourtSchedules(param);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("courtSchedules", groupByCourtRoom(courtSchedules));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Map<String, Object>> getCourtschedulesByIds(final String courtScheduleIds) {
        LOG.info("courtscheduler.search.court-schedules-by-id ids={}", courtScheduleIds);

        final List<String> ids = courtScheduleIds == null
                ? List.of()
                : stream(courtScheduleIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

        final SearchCourtSchedulesByIdRequestParam param =
                searchCourtSchedulesByIdRequestParamBuilder()
                        .withCourtScheduleIds(ids)
                        .build();

        final List<CourtSchedule> courtSchedules = sessionsService.getCourtSchedulesById(param);

        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("courtSchedules", courtSchedules);
        return ResponseEntity.ok(body);
    }

    /**
     * Reshape the flat {@code List<CourtSchedule>} into the legacy IT-asserted shape
     * grouped by {@code courtRoomId} with a nested {@code sessions} array.
     *
     * <p>Each session has its {@code sessionStartTime} / {@code sessionEndTime} fields
     * rewritten from a {@code Date} (Jackson default → full ISO) to a UTC {@code "HH:mm"}
     * string so the legacy IT contract is preserved.</p>
     */
    private List<Map<String, Object>> groupByCourtRoom(final List<CourtSchedule> schedules) {
        final Map<String, List<CourtSchedule>> byCourtRoom = new LinkedHashMap<>();
        for (final CourtSchedule cs : schedules) {
            byCourtRoom.computeIfAbsent(cs.getCourtRoomId(), k -> new ArrayList<>()).add(cs);
        }
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final var entry : byCourtRoom.entrySet()) {
            final List<CourtSchedule> sessions = new ArrayList<>(entry.getValue());
            // Legacy ordering: sessions within a room sorted by sessionDate.
            sessions.sort(Comparator.comparing(CourtSchedule::getSessionDate,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            final List<Map<String, Object>> sessionMaps = new ArrayList<>();
            for (final CourtSchedule cs : sessions) {
                sessionMaps.add(toSessionMapWithUtcTimes(cs));
            }
            final Map<String, Object> group = new LinkedHashMap<>();
            group.put("courtRoomId", entry.getKey());
            group.put("courtRoomName", sessions.isEmpty() ? null : sessions.get(0).getCourtRoomName());
            group.put("sessions", sessionMaps);
            result.add(group);
        }
        // Legacy ordering: rooms sorted alphabetically by courtRoomName.
        result.sort(Comparator.comparing(group -> (String) group.get("courtRoomName"),
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private static final java.time.format.DateTimeFormatter UTC_HH_MM_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneOffset.UTC);

    private Map<String, Object> toSessionMapWithUtcTimes(final CourtSchedule cs) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> map = objectMapper.convertValue(cs, Map.class);
        if (cs.getSessionStartTime() != null) {
            map.put("sessionStartTime", UTC_HH_MM_FORMATTER.format(cs.getSessionStartTime().toInstant()));
        }
        if (cs.getSessionEndTime() != null) {
            map.put("sessionEndTime", UTC_HH_MM_FORMATTER.format(cs.getSessionEndTime().toInstant()));
        }
        // The legacy get-court-schedule response was assembled from CourtScheduleView, whose wire
        // names for these flags are is-prefixed — unlike the raw CourtSchedule serialization used
        // by the hearing-slots and sessions-by-id responses, which keeps the bean-convention names
        // draft/overbookingAllowed. Rename here rather than annotating CourtSchedule, so the other
        // endpoints keep their contract.
        map.remove("draft");
        map.remove("overbookingAllowed");
        map.put("isOverbookingAllowed", cs.isOverbookingAllowed());
        map.put("isDraft", cs.isDraft());
        return map;
    }

    @Override
    public ResponseEntity<Map<String, Object>> postCourtschedulerAssignCourtroom(final Map<String, Object> body) {
        LOG.info("courtscheduler.assign.courtroom requested: {}", body);
        final AssignCourtroomRequest req = assignCourtroomRequestConverter.convert(toJsonObject(body));

        final JsonObject validate = sessionsApiValidator.getAssignCourtroomValidation(req);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        final AssignCourtroomResponse response = sessionsService.assignCourtroom(req);

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("errorGroups", response.getErrorGroups());
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<Map<String, Object>> postCourtschedulerDeleteCourtschedule(final Map<String, Object> body) {
        LOG.info("courtscheduler.delete requested: {}", body);
        final SessionsParam sessions = sessionsConverter.convert(toJson(body));

        final JsonObject responseObject = sessionsService.deleteCourtScheduleSessions(sessions);

        return ResponseEntity.ok(JsonValueConverter.toMap(responseObject));
    }

    @Override
    public ResponseEntity<Void> postCourtschedulerUpdateCourtscheduleEdit(final Map<String, Object> body) {
        LOG.info("courtscheduler.update requested: {}", body);
        final UpdateCourtSchedule update = updateCourtScheduleConverter.convert(toJsonObject(body));

        final JsonObject validate = sessionsApiValidator.getSessionsUpdateValidation(update);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        final Result result = sessionsService.update(update);
        if (!result.isSuccess()) {
            throw new ValidationException(
                    Json.createObjectBuilder().add("errorMessage", result.getMsg()).build());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /* ============================================================
     *  SessionOpenApi — judiciary assign / unassign on /session
     * ============================================================ */

    @Override
    public ResponseEntity<Void> postCourtschedulerSessionJudiciary(final Map<String, Object> body) {
        final String contentType = request.getContentType() == null ? "" : request.getContentType();
        LOG.info("/session ContentType={}, body={}", contentType, body);

        if (contentType.contains(ASSIGN_MT.substring(0, ASSIGN_MT.indexOf('+')))) {
            return assignJudiciary(body);
        }
        if (contentType.contains(UNASSIGN_MT.substring(0, UNASSIGN_MT.indexOf('+')))) {
            return unassignJudiciary(body);
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Content-Type for /session: " + contentType);
    }

    private ResponseEntity<Void> assignJudiciary(final Map<String, Object> body) {
        final AssignJudiciariesRequest dto = assignJudiciariesRequestConverter.convert(toJsonObject(body));
        final JsonObject validate = assignJudiciariesApiValidator.validate(dto);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        judiciaryAssignmentService.assignJudiciaries(dto, UUID.randomUUID().toString());
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Void> unassignJudiciary(final Map<String, Object> body) {
        final JsonObject validate = judiciariesApiValidator.validateUnassignJudiciaryRequest(toJsonObject(body));
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        final Map<String, List<String>> judiciaryToSessionIds = new HashMap<>();
        final List<Map<String, Object>> judiciaries =
                (List<Map<String, Object>>) body.getOrDefault("judiciaries", List.of());
        for (final Map<String, Object> j : judiciaries) {
            final String judiciaryId = (String) j.getOrDefault("judiciaryId", "");
            final List<String> sessionIds = new ArrayList<>(
                    (List<String>) j.getOrDefault("sessionIds", List.of()));
            judiciaryToSessionIds.put(judiciaryId, sessionIds);
        }
        final boolean skipValidations = Boolean.TRUE.equals(body.get("skipValidations"));
        try {
            judiciaryUnassignmentService.unassignJudiciary(
                    judiciaryToSessionIds, UUID.randomUUID().toString(), skipValidations);
        } catch (IllegalStateException ise) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ise.getMessage());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /* ============================================================
     *  OucodeOpenApi — OU code migrate / recalculate
     * ============================================================ */

    @Override
    public ResponseEntity<Void> postOuCodeMigrate(final Map<String, Object> body) {
        LOG.info("courtscheduler.oucode.migrate: {}", body);
        final OuCodeMigrateRequest req = ouCodeMigrateConverter.convert(toJson(body));
        final Result result = sessionsService.migrateOuCodes(req);
        if (!result.isSuccess()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMsg());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Override
    public ResponseEntity<Void> postOuCodeRecalculateAvailability(final Map<String, Object> body) {
        LOG.info("courtscheduler.oucode.recalculate.availability: {}", body);
        final OuCodeRecalculateAvailabilityRequest req = ouCodeRecalculateAvailabilityConverter.convert(toJson(body));
        final Result result = sessionsService.ouCodesRecalculateAvailability(req);
        if (!result.isSuccess()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getMsg());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /* ============================================================
     *  MiOpenApi — Management Information exports
     * ============================================================ */

    private MiFilterCriteria miCriteria(final String fromDate, final String toDate) {
        final JsonObjectBuilder builder = Json.createObjectBuilder();
        builder.add("fromDate", fromDate == null ? "" : fromDate);
        builder.add("toDate", toDate == null ? "" : toDate);
        return miFilterCriteriaRequestParamConverter.convert(builder.build());
    }

    @Override
    public ResponseEntity<Map<String, Object>> getMiCourtSchedules(final String fromDate, final String toDate) {
        LOG.info("courtscheduler.export.court_schedule fromDate={}, toDate={}", fromDate, toDate);
        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> rows =
                miService.getCourtSchedules(miCriteria(fromDate, toDate));
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("courtSchedules", rows);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Map<String, Object>> getMiCourtScheduleJudiciaries(final String fromDate, final String toDate) {
        LOG.info("courtscheduler.export.court_schedule_judiciary fromDate={}, toDate={}", fromDate, toDate);
        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> rows =
                miService.getCourtSchedulesJudiciary(miCriteria(fromDate, toDate));
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("courtScheduleJudiciaries", rows);
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Map<String, Object>> getMiAllocatedListings(final String fromDate, final String toDate) {
        LOG.info("courtscheduler.export.allocated_listings fromDate={}, toDate={}", fromDate, toDate);
        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> rows =
                miService.getAllocatedListings(miCriteria(fromDate, toDate));
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("allocatedListings", rows);
        return ResponseEntity.ok(body);
    }

    /* ============================================================
     *  ValidateOpenApi — Content-Type-dispatched validate.* endpoints
     * ============================================================ */

    @Override
    @SkipEnvelope
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<Void> postValidate(final Map<String, Object> body) {
        final String contentType = request.getContentType() == null ? "" : request.getContentType();
        LOG.info("courtscheduler.validate ContentType={}", contentType);

        final JsonObject validate;
        if (contentType.startsWith(CREATE_MT.substring(0, CREATE_MT.indexOf('+')))) {
            validate = sessionsApiValidator.getSessionsCreateValidation(
                    createSessionsRequestParamConverter.convert(toJsonObject(body)));
        } else if (contentType.startsWith(UPDATE_MT.substring(0, UPDATE_MT.indexOf('+')))) {
            validate = sessionsApiValidator.getSessionsUpdateValidation(
                    updateCourtScheduleConverter.convert(toJsonObject(body)));
        } else if (contentType.startsWith(DELETE_MT.substring(0, DELETE_MT.indexOf('+')))) {
            // SessionsConverter handles the parse; the resulting SessionsParam is the
            // "well-formed" check. Empty JsonObject indicates pass.
            sessionsConverter.convert(toJson(body));
            validate = Json.createObjectBuilder().build();
        } else {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported Content-Type for /validate: " + contentType);
        }

        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        // Legacy IT classes assert response body is exactly "{}" for a successful
        // validation, so explicitly return an empty Map.
        return (ResponseEntity) ResponseEntity.ok(Collections.emptyMap());
    }

    @Override
    @SkipEnvelope
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ResponseEntity<Void> postValidateSessionAvailability(final Map<String, Object> body) {
        final JsonObject validate = sessionsApiValidator.getSessionsAvailabilityValidation(
                validateSessionAvailabilityRequestParamConverter.convert(toJsonObject(body)));
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        return (ResponseEntity) ResponseEntity.ok(Collections.emptyMap());
    }

    /* ============================================================
     *  ProvisionalBookingOpenApi — provisional booking endpoints
     * ============================================================ */

    @Override
    public ResponseEntity<Map<String, Object>> postCreateProvisionalBooking(final Map<String, Object> body) {
        LOG.info("courtscheduler.create.provisional.booking: {}", body);
        final ProvisionalBookingSlots slots = provisionalSlotConverter.convert(toJson(body));
        final JsonObject validate = provisionalBookingApiValidator.createProvisionalBookingValidation(slots);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots);
        return ResponseEntity.ok(JsonValueConverter.toMap(response));
    }

    @Override
    public ResponseEntity<Map<String, Object>> getProvisionalBooking(final String bookingIds) {
        LOG.info("courtscheduler.get.provisional.booking bookingIds={}", bookingIds);
        final JsonObject validate = provisionalBookingApiValidator.getProvisionalBookingValidation(bookingIds);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        final JsonObject response = provisionalBookingService.fetchProvisionalSlots(bookingIds);
        return ResponseEntity.ok(JsonValueConverter.toMap(response));
    }
}
