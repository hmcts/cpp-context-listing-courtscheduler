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
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.moj.cpp.courtscheduler.api.converter.AssignCourtroomRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.AssignJudiciariesRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.AssignJudiciaryToSessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.CreateSessionsRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListHearingSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ProvisionalSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.SessionsConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.UpdateCourtScheduleConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ValidateSessionAvailabilityRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.CourtScheduleRoomSanitiser;
import uk.gov.moj.cpp.courtscheduler.api.service.MiService;
import uk.gov.moj.cpp.courtscheduler.api.service.ProvisionalBookingService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.validator.AssignJudiciariesApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.CourtScheduleApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.JudiciariesApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ProvisionalBookingApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.UnprocessableEntityException;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryUnassignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.config.JsonValueConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomResponse;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciaryToSessionsRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ChangeCourtRoomForMultidayHearingRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ChangeCourtRoomForMultidayHearingResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ReserveUnconfirmedHearingRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ReserveUnconfirmedHearingResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SearchCourtSchedulesByIdRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.exception.ConfirmedBookingExistsException;
import uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackInvalidRequestException;
import uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackNoSessionException;
import uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedDay;
import uk.gov.moj.cpp.courtscheduler.exception.NoAllocationOnDateException;
import uk.gov.moj.cpp.courtscheduler.exception.NoSessionAvailableException;
import uk.gov.moj.cpp.courtscheduler.envelope.SkipEnvelope;
import uk.gov.moj.cpp.courtscheduler.openapi.api.CourtscheduleOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.api.HearingsOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.api.MiOpenApi;
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
                                          HearingsOpenApi,
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
    private final AllocatedListingService allocatedListingService;
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
    private final AssignJudiciaryToSessionsConverter assignJudiciaryToSessionsConverter;

    // --- hearings booking family (SPRDT-1089 reshape)
    private final SlotsUpdateService slotsUpdateService;
    private final SlotsRemoveService slotsRemoveService;
    private final HearingSlotsApiValidator hearingSlotsApiValidator;
    private final ListHearingSlotConverter listHearingSlotConverter;

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
                             final AllocatedListingService allocatedListingService,
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
                             final AssignJudiciaryToSessionsConverter assignJudiciaryToSessionsConverter,
                             final SlotsUpdateService slotsUpdateService,
                             final SlotsRemoveService slotsRemoveService,
                             final HearingSlotsApiValidator hearingSlotsApiValidator,
                             final ListHearingSlotConverter listHearingSlotConverter,
                             final MiService miService,
                             final MiFilterCriteriaRequestParamConverter miFilterCriteriaRequestParamConverter,
                             final ProvisionalBookingService provisionalBookingService,
                             final ProvisionalBookingApiValidator provisionalBookingApiValidator,
                             final ProvisionalSlotConverter provisionalSlotConverter) {
        this.objectMapper = objectMapper;
        this.request = request;
        this.sessionsService = sessionsService;
        this.allocatedListingService = allocatedListingService;
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
        this.assignJudiciaryToSessionsConverter = assignJudiciaryToSessionsConverter;
        this.slotsUpdateService = slotsUpdateService;
        this.slotsRemoveService = slotsRemoveService;
        this.hearingSlotsApiValidator = hearingSlotsApiValidator;
        this.listHearingSlotConverter = listHearingSlotConverter;
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
        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(courtSchedules);

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
     *  SessionOpenApi — SPRDT-1089 additions on /sessions/*
     * ============================================================ */

    /** POST /sessions/bulk-assign-judiciaries — replace-all judiciary assignment. */
    @Override
    public ResponseEntity<Void> postBulkAssignJudiciaries(final Map<String, Object> body) {
        LOG.info("courtscheduler.assign-judiciary-to-sessions requested: {}", body);
        try {
            final AssignJudiciaryToSessionsRequest dto = assignJudiciaryToSessionsConverter.convert(toJsonObject(body));
            judiciaryAssignmentService.assignJudiciaryToSessions(dto, UUID.randomUUID().toString());
        } catch (final IllegalArgumentException e) {
            LOG.warn("courtscheduler.assign-judiciary-to-sessions: {}", e.getMessage());
            throw new ValidationException(
                    Json.createObjectBuilder().add("errorMessage", e.getMessage()).build());
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /** POST /sessions/remove-all-judiciaries — remove all judiciary from the given sessions. */
    @Override
    public ResponseEntity<Void> postRemoveAllJudiciaries(final Map<String, Object> body) {
        LOG.info("courtscheduler.remove-all-judiciary requested: {}", body);
        final JsonObject payload = toJsonObject(body);

        if (!payload.containsKey("courtScheduleIds") || payload.isNull("courtScheduleIds")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courtScheduleIds is required");
        }
        final jakarta.json.JsonArray courtScheduleIdsArray = payload.getJsonArray("courtScheduleIds");
        if (courtScheduleIdsArray == null || courtScheduleIdsArray.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courtScheduleIds must contain at least one value");
        }
        final List<String> courtScheduleIds = new ArrayList<>();
        for (int i = 0; i < courtScheduleIdsArray.size(); i++) {
            final String courtScheduleId = courtScheduleIdsArray.getString(i, "").trim();
            if (courtScheduleId.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("courtScheduleIds[%d] must not be empty", i));
            }
            courtScheduleIds.add(courtScheduleId);
        }

        judiciaryUnassignmentService.removeAllJudiciaryByCourtScheduleIds(courtScheduleIds);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /** POST /sessions — purge allocated listings (reserved sessions) whose expiresAt has already passed. */
    @Override
    public ResponseEntity<Void> postPurgeExpiredReservedSessions(final Map<String, Object> body) {
        LOG.info("courtscheduler.purge-expired-reserved-sessions requested");
        final int purged = allocatedListingService.purgeExpiredReservedSessions();
        LOG.info("courtscheduler.purge-expired-reserved-sessions purged {} allocated listing(s)", purged);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /** DELETE /sessions/{hearingId} — release the sessions booked for the hearing (was DELETE /hearingslots/{hearingId}). */
    @Override
    public ResponseEntity<Void> deleteHearingSlots(final String hearingId) {
        LOG.info("courtscheduler.release.sessions hearingId={}", hearingId);
        slotsRemoveService.remove(hearingId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * PUT /sessions/{sessionId}/hearings/{unconfirmedHearingId} — reserve an unconfirmed hearing
     * against a session (LPT-2433): books through the normal capacity-decrementing pipeline but
     * marks the resulting allocation with an expiry so the purge job sweeps it up if never
     * confirmed.
     */
    @Override
    public ResponseEntity<Map<String, Object>> putReserveUnconfirmedHearing(final String sessionId,
                                                                             final String unconfirmedHearingId,
                                                                             final Map<String, Object> body) {
        LOG.info("courtscheduler.reserve-unconfirmed-hearing sessionId={}, unconfirmedHearingId={}, body={}",
                Encode.forJava(sessionId), Encode.forJava(unconfirmedHearingId), body);
        final JsonObject payload = toJsonObject(body);
        final ReserveUnconfirmedHearingRequest reserveRequest = new ReserveUnconfirmedHearingRequest()
                .setHearingStartTime(getStringOrNull(payload, "hearingStartTime"))
                .setSlotBased(getBooleanOrFalse(payload, "isSlotBased"))
                .setDuration(payload.containsKey("duration") ? payload.getInt("duration") : 0);

        final JsonObject validationError = hearingSlotsApiValidator.reserveUnconfirmedHearingValidation(reserveRequest);
        if (!validationError.isEmpty()) {
            throw new ValidationException(validationError);
        }

        try {
            final ReserveUnconfirmedHearingResponse response =
                    slotsUpdateService.reserveUnconfirmedHearing(sessionId, unconfirmedHearingId, reserveRequest);
            return ResponseEntity.ok(toResponseMap(response));
        } catch (NoSessionAvailableException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (ConfirmedBookingExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    /* ============================================================
     *  HearingsOpenApi — SPRDT-1089 booking family on /hearings/*
     * ============================================================ */

    private static final String CROWN_SAB_MT = "application/vnd.courtscheduler.crown.search.and.book";
    private static final String MAGS_SAB_MT = "application/vnd.courtscheduler.mags.search.and.book";
    private static final String MOVE_PAST_MT = "application/vnd.courtscheduler.move-hearing-to-past-date";
    private static final String CHANGE_ROOM_MULTIDAY_MT = "application/vnd.courtscheduler.change-court-room-for-multiday-hearing";

    /** POST /hearings — list a hearing into already-chosen court sessions (was PUT /list/hearingslots). */
    @Override
    public ResponseEntity<Map<String, Object>> postListHearingsInSessions(final Map<String, Object> body) {
        LOG.info("courtscheduler.list.hearings-in-sessions: {}", body);
        final RequestedSlots requested = listHearingSlotConverter.convert(toJson(body));
        final JsonObject validate = hearingSlotsApiValidator.listHearingSlotsValidation(requested.getHearingSlots());
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        final ListHearingSlotsResponse response = slotsUpdateService.listHearingSlots(requested);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("hearings", response.getHearings());
        return ResponseEntity.ok(result);
    }

    /** POST /hearings/{hearingId} — search-and-book, action selected by Content-Type. */
    @Override
    public ResponseEntity<Map<String, Object>> postSearchAndBookHearing(final String hearingId,
                                                                        final Map<String, Object> body) {
        final String contentType = request.getContentType() == null ? "" : request.getContentType();
        LOG.info("POST /hearings/{} ContentType={}, body={}", hearingId, contentType, body);

        final JsonObject payload = toJsonObject(body);
        try {
            if (contentType.contains(CROWN_SAB_MT)) {
                return crownSearchAndBook(hearingId, payload);
            }
            if (contentType.contains(MAGS_SAB_MT)) {
                return magsSearchAndBook(hearingId, payload);
            }
            if (contentType.contains(MOVE_PAST_MT)) {
                return moveHearingToPastDate(hearingId, payload);
            }
            if (contentType.contains(CHANGE_ROOM_MULTIDAY_MT)) {
                return changeCourtRoomForMultidayHearing(hearingId, payload);
            }
        } catch (CrownFallbackInvalidRequestException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (CrownFallbackNoSessionException | NoSessionAvailableException e) {
            // Booking-family 422s keep the legacy FLAT body ({"errorCode":...,"message":...}) —
            // UnprocessableEntityException would render the judiciary-validate wrapper instead.
            return ResponseEntity.unprocessableEntity()
                    .body(JsonValueConverter.toMap(buildNoSessionErrorBody(e.getMessage())));
        } catch (NoAllocationOnDateException e) {
            return ResponseEntity.unprocessableEntity()
                    .body(JsonValueConverter.toMap(buildErrorBody("NO_ALLOCATION_ON_DATE", e.getMessage())));
        } catch (ExtendMultidayHearingException e) {
            // SPRDT-1273: a same-start resize inside crown.search.and.book is delegated to the
            // extend/shrink service; its rejections (NO_AVAILABILITY with the unavailable dates,
            // INVALID_DATE_RANGE) surface on this endpoint with the same flat 422 body the retired
            // PATCH extend endpoint used, so the listing caller can propagate them to the UI.
            return ResponseEntity.unprocessableEntity()
                    .body(JsonValueConverter.toMap(buildExtendErrorBody(e)));
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Content-Type for /hearings/{hearingId}: " + contentType);
    }

    private ResponseEntity<Map<String, Object>> crownSearchAndBook(final String hearingId, final JsonObject payload) {
        final CrownSearchAndBookRequest sabRequest = new CrownSearchAndBookRequest()
                .setHearingId(hearingId)
                .setCourtCentreId(getStringOrNull(payload, "courtCentreId"))
                .setHearingDate(getDateOrNull(payload, "hearingDate"))
                .setEndDate(getDateOrNull(payload, "endDate"))
                .setDurationInMinutes(payload.containsKey("durationInMinutes") ? payload.getInt("durationInMinutes") : 0)
                .setCourtRoomId(getStringOrNull(payload, "courtRoomId"))
                .setEarliestHearingTime(getStringOrNull(payload, "earliestHearingTime"))
                .setCourtScheduleId(getStringOrNull(payload, "courtScheduleId"))
                .setSource(getStringOrNull(payload, "source"))
                // SPRDT-1283: optional centre metadata for auto-creating a session at a
                // never-seeded centre (single-day fallback only).
                .setOuCode(getStringOrNull(payload, "ouCode"))
                .setCourtCentreName(getStringOrNull(payload, "courtCentreName"))
                .setCourtRoomName(getStringOrNull(payload, "courtRoomName"));

        final JsonObject validationError = hearingSlotsApiValidator.crownSearchAndBookValidation(sabRequest);
        if (!validationError.isEmpty()) {
            throw new ValidationException(validationError);
        }
        final CrownSearchAndBookResponse response = slotsUpdateService.crownSearchAndBook(sabRequest);
        return ResponseEntity.ok(toResponseMap(response));
    }

    private ResponseEntity<Map<String, Object>> magsSearchAndBook(final String hearingId, final JsonObject payload) {
        final MagsSearchAndBookRequest sabRequest = new MagsSearchAndBookRequest()
                .setHearingId(hearingId)
                .setCourtCentreId(getStringOrNull(payload, "courtCentreId"))
                .setHearingDate(getDateOrNull(payload, "hearingDate"))
                .setEndDate(getDateOrNull(payload, "endDate"))
                .setDurationInMinutes(payload.containsKey("durationInMinutes") ? payload.getInt("durationInMinutes") : 0)
                .setCourtRoomId(getStringOrNull(payload, "courtRoomId"))
                .setHearingStartTime(getStringOrNull(payload, "hearingStartTime"))
                .setHearingSessionDateSearchCutOff(getStringOrNull(payload, "hearingSessionDateSearchCutOff"))
                .setIsPolice(getBooleanOrFalse(payload, "isPolice"));

        final JsonObject validationError = hearingSlotsApiValidator.magsSearchAndBookValidation(sabRequest);
        if (!validationError.isEmpty()) {
            throw new ValidationException(validationError);
        }
        final MagsSearchAndBookResponse response = slotsUpdateService.magsSearchAndBook(sabRequest);
        return ResponseEntity.ok(toResponseMap(response));
    }

    private ResponseEntity<Map<String, Object>> moveHearingToPastDate(final String hearingId, final JsonObject payload) {
        final MoveHearingToPastDateRequest moveRequest = new MoveHearingToPastDateRequest()
                .setHearingId(hearingId)
                .setCourtCentreId(getStringOrNull(payload, "courtCentreId"))
                .setJurisdiction(getStringOrNull(payload, "jurisdiction"))
                .setStartDate(getDateOrNull(payload, "startDate"))
                .setEndDate(getDateOrNull(payload, "endDate"))
                .setDurationInMinutes(payload.containsKey("durationInMinutes") ? payload.getInt("durationInMinutes") : 0)
                .setCourtScheduleId(getStringOrNull(payload, "courtScheduleId"));

        final JsonObject validationError = hearingSlotsApiValidator.moveHearingToPastDateValidation(moveRequest);
        if (!validationError.isEmpty()) {
            throw new ValidationException(validationError);
        }
        final MoveHearingToPastDateResponse response = slotsUpdateService.moveHearingToPastDate(moveRequest);
        return ResponseEntity.ok(toResponseMap(response));
    }

    /**
     * change-court-room-for-multiday-hearing — re-allocates ONLY the submitted day(s) of an
     * existing multi-day hearing; days not submitted stay untouched. The everit request-schema
     * filter has already enforced the required days[] shape.
     */
    private ResponseEntity<Map<String, Object>> changeCourtRoomForMultidayHearing(final String hearingId,
                                                                                  final JsonObject payload) {
        final List<RequestedDay> days = new ArrayList<>();
        final jakarta.json.JsonArray daysArray = payload.getJsonArray("days");
        for (int i = 0; i < daysArray.size(); i++) {
            final JsonObject dayJson = daysArray.getJsonObject(i);
            days.add(new RequestedDay(
                    java.time.LocalDate.parse(dayJson.getString("sessionDate")),
                    dayJson.getString("courtScheduleId"),
                    dayJson.getInt("durationInMinutes")));
        }

        final ChangeCourtRoomForMultidayHearingRequest changeRequest = new ChangeCourtRoomForMultidayHearingRequest()
                .setHearingId(hearingId)
                .setDays(days);

        final ChangeCourtRoomForMultidayHearingResponse response =
                slotsUpdateService.changeCourtRoomForMultidayHearing(changeRequest);
        return ResponseEntity.ok(toResponseMap(response));
    }

    private static JsonObject buildErrorBody(final String errorCode, final String message) {
        return Json.createObjectBuilder()
                .add("errorCode", errorCode)
                .add("message", message == null ? "" : message)
                .build();
    }

    private static JsonObject buildExtendErrorBody(final ExtendMultidayHearingException e) {
        final JsonObjectBuilder body = Json.createObjectBuilder()
                .add("errorCode", e.getErrorCode().name())
                .add("message", e.getMessage() == null ? "" : e.getMessage());
        if (!e.getUnavailableDates().isEmpty()) {
            final jakarta.json.JsonArrayBuilder dates = Json.createArrayBuilder();
            for (final java.time.LocalDate d : e.getUnavailableDates()) {
                dates.add(d.toString());
            }
            body.add("unavailableDates", dates);
        }
        return body.build();
    }

    private static JsonObject buildNoSessionErrorBody(final String message) {
        return Json.createObjectBuilder()
                .add("errorCode", "NO_SESSION_FOUND")
                .add("message", message == null ? "" : message)
                .build();
    }

    private static String getStringOrNull(final JsonObject json, final String key) {
        return json.containsKey(key) && !json.isNull(key) ? json.getString(key) : null;
    }

    private static boolean getBooleanOrFalse(final JsonObject json, final String key) {
        return json.containsKey(key) && !json.isNull(key) && json.getBoolean(key);
    }

    private static java.time.LocalDate getDateOrNull(final JsonObject json, final String key) {
        final String raw = getStringOrNull(json, key);
        return raw == null ? null : java.time.LocalDate.parse(raw);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toResponseMap(final Object response) {
        return response == null ? new LinkedHashMap<>() : objectMapper.convertValue(response, Map.class);
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
