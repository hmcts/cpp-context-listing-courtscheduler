package uk.gov.moj.cpp.courtscheduler.api;

import static java.util.Arrays.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import uk.gov.moj.cpp.courtscheduler.api.converter.ListHearingSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.MiFilterCriteriaRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ProvisionalSlotConverter;
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
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryAssignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.JudiciaryUnassignmentService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.config.JsonValueConverter;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ChangeCourtRoomForMultidayHearingRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ChangeCourtRoomForMultidayHearingResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleGroupedSession;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleRoomGroup;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AssignJudiciaryToSessionsRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleDeleteResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerGetCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerSearchCourtSchedulesByIdResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CrownSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MagsSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.openapi.model.MoveHearingToPastDateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Result;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackInvalidRequestException;
import uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackNoSessionException;
import uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RequestedDay;
import uk.gov.moj.cpp.courtscheduler.exception.NoAllocationOnDateException;
import uk.gov.moj.cpp.courtscheduler.exception.NoSessionAvailableException;
import uk.gov.moj.cpp.courtscheduler.exception.SlotsBookException;
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
    // CourtSchedule serializes overbookingAllowed/draft with no "is" prefix (see the yml comment
    // on CourtSchedule), which CourtScheduleGroupedSession's isOverbookingAllowed/isDraft fields
    // don't recognise - toGroupedSession() overrides both explicitly after conversion, so the
    // intermediate convertValue must tolerate (not fail on) those two unknown keys.
    private final ObjectMapper lenientObjectMapper;
    private final HttpServletRequest request;

    // --- court schedule CRUD
    private final SessionsService sessionsService;
    private final SessionsApiValidator sessionsApiValidator;
    private final CourtScheduleApiValidator courtScheduleApiValidator;
    private final ValidateSessionAvailabilityRequestParamConverter validateSessionAvailabilityRequestParamConverter;

    // --- session judiciary assignment / unassignment
    private final JudiciaryAssignmentService judiciaryAssignmentService;
    private final JudiciaryUnassignmentService judiciaryUnassignmentService;
    private final AssignJudiciariesApiValidator assignJudiciariesApiValidator;
    private final JudiciariesApiValidator judiciariesApiValidator;

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
                             final SessionsApiValidator sessionsApiValidator,
                             final CourtScheduleApiValidator courtScheduleApiValidator,
                             final ValidateSessionAvailabilityRequestParamConverter validateSessionAvailabilityRequestParamConverter,
                             final JudiciaryAssignmentService judiciaryAssignmentService,
                             final JudiciaryUnassignmentService judiciaryUnassignmentService,
                             final AssignJudiciariesApiValidator assignJudiciariesApiValidator,
                             final JudiciariesApiValidator judiciariesApiValidator,
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
        this.lenientObjectMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.request = request;
        this.sessionsService = sessionsService;
        this.sessionsApiValidator = sessionsApiValidator;
        this.courtScheduleApiValidator = courtScheduleApiValidator;
        this.validateSessionAvailabilityRequestParamConverter = validateSessionAvailabilityRequestParamConverter;
        this.judiciaryAssignmentService = judiciaryAssignmentService;
        this.judiciaryUnassignmentService = judiciaryUnassignmentService;
        this.assignJudiciariesApiValidator = assignJudiciariesApiValidator;
        this.judiciariesApiValidator = judiciariesApiValidator;
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

    private String toJson(final Object body) {
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
    public ResponseEntity<Void> postCourtschedulerCreateCourtschedule(final CreateSessionRequestParam body) {
        LOG.info("courtscheduler.create requested: {}", body);

        final JsonObject validate = sessionsApiValidator.getSessionsCreateValidation(body);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        sessionsService.create(body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @Override
    public ResponseEntity<CourtschedulerGetCourtSchedule> getCourtschedule(final String courtCentreId,
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

        final CourtschedulerGetCourtSchedule body = new CourtschedulerGetCourtSchedule()
                .courtSchedules(groupByCourtRoom(courtSchedules));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<CourtschedulerSearchCourtSchedulesByIdResponse> getCourtschedulesByIds(final String courtScheduleIds) {
        LOG.info("courtscheduler.search.court-schedules-by-id ids={}", courtScheduleIds);

        final List<String> ids = courtScheduleIds == null
                ? List.of()
                : stream(courtScheduleIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

        final List<CourtSchedule> courtSchedules = sessionsService.getCourtSchedulesById(ids);
        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(courtSchedules);

        final CourtschedulerSearchCourtSchedulesByIdResponse body =
                new CourtschedulerSearchCourtSchedulesByIdResponse().courtSchedules(courtSchedules);
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
    private List<CourtScheduleRoomGroup> groupByCourtRoom(final List<CourtSchedule> schedules) {
        final Map<String, List<CourtSchedule>> byCourtRoom = new LinkedHashMap<>();
        for (final CourtSchedule cs : schedules) {
            byCourtRoom.computeIfAbsent(cs.getCourtRoomId(), k -> new ArrayList<>()).add(cs);
        }
        final List<CourtScheduleRoomGroup> result = new ArrayList<>();
        for (final var entry : byCourtRoom.entrySet()) {
            final List<CourtSchedule> sessions = new ArrayList<>(entry.getValue());
            // Legacy ordering: sessions within a room sorted by sessionDate.
            sessions.sort(Comparator.comparing(CourtSchedule::getSessionDate,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            final List<CourtScheduleGroupedSession> sessionViews = new ArrayList<>();
            for (final CourtSchedule cs : sessions) {
                sessionViews.add(toGroupedSession(cs));
            }
            final CourtScheduleRoomGroup group = new CourtScheduleRoomGroup()
                    .courtRoomId(entry.getKey())
                    .courtRoomName(sessions.isEmpty() ? null : sessions.get(0).getCourtRoomName())
                    .sessions(sessionViews);
            result.add(group);
        }
        // Legacy ordering: rooms sorted alphabetically by courtRoomName.
        result.sort(Comparator.comparing(CourtScheduleRoomGroup::getCourtRoomName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return result;
    }

    private static final java.time.format.DateTimeFormatter UTC_HH_MM_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneOffset.UTC);

    private CourtScheduleGroupedSession toGroupedSession(final CourtSchedule cs) {
        // Field-for-field copy via convertValue (matching field names/types) covers everything
        // except sessionStartTime/sessionEndTime (custom "HH:mm" UTC format here, not the raw
        // OffsetDateTime) and overbookingAllowed/draft -> isOverbookingAllowed/isDraft (the legacy
        // get-court-schedule response used is-prefixed wire keys for these two flags, unlike the
        // raw CourtSchedule serialization used by the hearing-slots and sessions-by-id responses,
        // which keeps the bean-convention names draft/overbookingAllowed) — both overridden below.
        final CourtScheduleGroupedSession session = lenientObjectMapper.convertValue(cs, CourtScheduleGroupedSession.class);
        session.setSessionStartTime(cs.getSessionStartTime() != null
                ? UTC_HH_MM_FORMATTER.format(cs.getSessionStartTime().toInstant()) : null);
        session.setSessionEndTime(cs.getSessionEndTime() != null
                ? UTC_HH_MM_FORMATTER.format(cs.getSessionEndTime().toInstant()) : null);
        session.setIsOverbookingAllowed(cs.getOverbookingAllowed());
        session.setIsDraft(cs.getDraft());
        return session;
    }

    @Override
    public ResponseEntity<AssignCourtroomResponse> postCourtschedulerAssignCourtroom(final AssignCourtroomRequest courtschedulerAssignCourtroom) {
        LOG.info("courtscheduler.assign.courtroom requested: {}", courtschedulerAssignCourtroom);

        final JsonObject validate = sessionsApiValidator.getAssignCourtroomValidation(courtschedulerAssignCourtroom);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        final AssignCourtroomResponse response = sessionsService.assignCourtroom(courtschedulerAssignCourtroom);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<CourtScheduleDeleteResponse> postCourtschedulerDeleteCourtschedule(final SessionsParam courtschedulerDelete) {
        LOG.info("courtscheduler.delete requested: {}", courtschedulerDelete);

        final CourtScheduleDeleteResponse response = sessionsService.deleteCourtScheduleSessions(courtschedulerDelete);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Void> postCourtschedulerUpdateCourtscheduleEdit(final UpdateCourtSchedule body) {
        LOG.info("courtscheduler.update requested: {}", body);

        final JsonObject validate = sessionsApiValidator.getSessionsUpdateValidation(body);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        final Result result = sessionsService.update(body);
        if (!Boolean.TRUE.equals(result.getSuccess())) {
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
        final AssignJudiciariesRequest dto = objectMapper.convertValue(body, AssignJudiciariesRequest.class);
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
    public ResponseEntity<Void> postBulkAssignJudiciaries(final AssignJudiciaryToSessionsRequest dto) {
        LOG.info("courtscheduler.assign-judiciary-to-sessions requested: {}", dto);
        try {
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

    /** DELETE /sessions/{hearingId} — release the sessions booked for the hearing (was DELETE /hearingslots/{hearingId}). */
    @Override
    public ResponseEntity<Void> deleteHearingSlots(final String hearingId) {
        LOG.info("courtscheduler.release.sessions hearingId={}", hearingId);
        slotsRemoveService.remove(hearingId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
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
    public ResponseEntity<uk.gov.moj.cpp.courtscheduler.openapi.model.ListHearingSlotsResponse> postListHearingsInSessions(final uk.gov.moj.cpp.courtscheduler.openapi.model.RequestedSlots body) {
        LOG.info("courtscheduler.list.hearings-in-sessions: {}", body);
        final RequestedSlots requested = listHearingSlotConverter.convert(toJson(body));
        final JsonObject validate = hearingSlotsApiValidator.listHearingSlotsValidation(requested.getHearingSlots());
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        final ListHearingSlotsResponse response = slotsUpdateService.listHearingSlots(requested);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("hearings", response.getHearings());
        return ResponseEntity.ok(objectMapper.convertValue(result, uk.gov.moj.cpp.courtscheduler.openapi.model.ListHearingSlotsResponse.class));
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
        } catch (SlotsBookException e) {
            // A DB persist failure during booking — same flat error body as the other
            // booking-family failures above, rather than falling through to a generic 500.
            return ResponseEntity.unprocessableEntity()
                    .body(JsonValueConverter.toMap(buildErrorBody("BOOKING_PERSIST_FAILED", e.getMessage())));
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported Content-Type for /hearings/{hearingId}: " + contentType);
    }

    private ResponseEntity<Map<String, Object>> crownSearchAndBook(final String hearingId, final JsonObject payload) {
        final CrownSearchAndBookRequest sabRequest = new CrownSearchAndBookRequest()
                .hearingId(hearingId)
                .courtCentreId(getStringOrNull(payload, "courtCentreId"))
                .hearingDate(getDateOrNull(payload, "hearingDate"))
                .endDate(getDateOrNull(payload, "endDate"))
                .durationInMinutes(payload.containsKey("durationInMinutes") ? payload.getInt("durationInMinutes") : 0)
                .courtRoomId(getStringOrNull(payload, "courtRoomId"))
                .earliestHearingTime(getStringOrNull(payload, "earliestHearingTime"))
                .courtScheduleId(getStringOrNull(payload, "courtScheduleId"))
                .source(getStringOrNull(payload, "source"))
                // SPRDT-1283: optional centre metadata for auto-creating a session at a
                // never-seeded centre (single-day fallback only).
                .ouCode(getStringOrNull(payload, "ouCode"))
                .courtCentreName(getStringOrNull(payload, "courtCentreName"))
                .courtRoomName(getStringOrNull(payload, "courtRoomName"));

        final JsonObject validationError = hearingSlotsApiValidator.crownSearchAndBookValidation(sabRequest);
        if (!validationError.isEmpty()) {
            throw new ValidationException(validationError);
        }
        final CrownSearchAndBookResponse response = slotsUpdateService.crownSearchAndBook(sabRequest);
        return ResponseEntity.ok(toResponseMap(response));
    }

    private ResponseEntity<Map<String, Object>> magsSearchAndBook(final String hearingId, final JsonObject payload) {
        final MagsSearchAndBookRequest sabRequest = new MagsSearchAndBookRequest()
                .hearingId(hearingId)
                .courtCentreId(getStringOrNull(payload, "courtCentreId"))
                .hearingDate(getDateOrNull(payload, "hearingDate"))
                .endDate(getDateOrNull(payload, "endDate"))
                .durationInMinutes(payload.containsKey("durationInMinutes") ? payload.getInt("durationInMinutes") : 0)
                .courtRoomId(getStringOrNull(payload, "courtRoomId"))
                .hearingStartTime(getStringOrNull(payload, "hearingStartTime"))
                .hearingSessionDateSearchCutOff(getStringOrNull(payload, "hearingSessionDateSearchCutOff"))
                .isPolice(getBooleanOrFalse(payload, "isPolice"));

        final JsonObject validationError = hearingSlotsApiValidator.magsSearchAndBookValidation(sabRequest);
        if (!validationError.isEmpty()) {
            throw new ValidationException(validationError);
        }
        final MagsSearchAndBookResponse response = slotsUpdateService.magsSearchAndBook(sabRequest);
        return ResponseEntity.ok(toResponseMap(response));
    }

    private ResponseEntity<Map<String, Object>> moveHearingToPastDate(final String hearingId, final JsonObject payload) {
        final MoveHearingToPastDateRequest moveRequest = new MoveHearingToPastDateRequest()
                .hearingId(hearingId)
                .courtCentreId(getStringOrNull(payload, "courtCentreId"))
                .jurisdiction(getStringOrNull(payload, "jurisdiction"))
                .startDate(getDateOrNull(payload, "startDate"))
                .endDate(getDateOrNull(payload, "endDate"))
                .durationInMinutes(payload.containsKey("durationInMinutes") ? payload.getInt("durationInMinutes") : 0)
                .courtScheduleId(getStringOrNull(payload, "courtScheduleId"));

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
            days.add(new RequestedDay()
                    .sessionDate(java.time.LocalDate.parse(dayJson.getString("sessionDate")))
                    .courtScheduleId(dayJson.getString("courtScheduleId"))
                    .durationInMinutes(dayJson.getInt("durationInMinutes")));
        }

        final ChangeCourtRoomForMultidayHearingRequest changeRequest = new ChangeCourtRoomForMultidayHearingRequest()
                .hearingId(hearingId)
                .days(days);

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
    public ResponseEntity<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerExportCourtSchedule> getMiCourtSchedules(final String fromDate, final String toDate) {
        LOG.info("courtscheduler.export.court_schedule fromDate={}, toDate={}", fromDate, toDate);
        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> rows =
                miService.getCourtSchedules(miCriteria(fromDate, toDate));
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("courtSchedules", rows);
        return ResponseEntity.ok(objectMapper.convertValue(body, uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerExportCourtSchedule.class));
    }

    @Override
    public ResponseEntity<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerExportCourtScheduleJudiciary> getMiCourtScheduleJudiciaries(final String fromDate, final String toDate) {
        LOG.info("courtscheduler.export.court_schedule_judiciary fromDate={}, toDate={}", fromDate, toDate);
        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> rows =
                miService.getCourtSchedulesJudiciary(miCriteria(fromDate, toDate));
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("courtScheduleJudiciaries", rows);
        return ResponseEntity.ok(objectMapper.convertValue(body, uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerExportCourtScheduleJudiciary.class));
    }

    @Override
    public ResponseEntity<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerExportAllocatedListings> getMiAllocatedListings(final String fromDate, final String toDate) {
        LOG.info("courtscheduler.export.allocated_listings fromDate={}, toDate={}", fromDate, toDate);
        final List<uk.gov.moj.cpp.courtscheduler.domain.mi.AllocatedListing> rows =
                miService.getAllocatedListings(miCriteria(fromDate, toDate));
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("allocatedListings", rows);
        return ResponseEntity.ok(objectMapper.convertValue(body, uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerExportAllocatedListings.class));
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
                    objectMapper.convertValue(body, CreateSessionRequestParam.class));
        } else if (contentType.startsWith(UPDATE_MT.substring(0, UPDATE_MT.indexOf('+')))) {
            validate = sessionsApiValidator.getSessionsUpdateValidation(
                    objectMapper.convertValue(body, UpdateCourtSchedule.class));
        } else if (contentType.startsWith(DELETE_MT.substring(0, DELETE_MT.indexOf('+')))) {
            // Jackson deserialization into SessionsParam is the "well-formed" check.
            // Empty JsonObject indicates pass.
            objectMapper.convertValue(body, SessionsParam.class);
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
    public ResponseEntity<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerCreateProvisionalBookingResponse> postCreateProvisionalBooking(final uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalBookingSlots body) {
        LOG.info("courtscheduler.create.provisional.booking: {}", body);
        final ProvisionalBookingSlots slots = provisionalSlotConverter.convert(toJson(body));
        final JsonObject validate = provisionalBookingApiValidator.createProvisionalBookingValidation(slots);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots);
        return ResponseEntity.ok(objectMapper.convertValue(JsonValueConverter.toMap(response),
                uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerCreateProvisionalBookingResponse.class));
    }

    @Override
    public ResponseEntity<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerGetProvisionalBooking> getProvisionalBooking(final String bookingIds) {
        LOG.info("courtscheduler.get.provisional.booking bookingIds={}", bookingIds);
        final JsonObject validate = provisionalBookingApiValidator.getProvisionalBookingValidation(bookingIds);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        final JsonObject response = provisionalBookingService.fetchProvisionalSlots(bookingIds);
        return ResponseEntity.ok(objectMapper.convertValue(JsonValueConverter.toMap(response),
                uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerGetProvisionalBooking.class));
    }
}
