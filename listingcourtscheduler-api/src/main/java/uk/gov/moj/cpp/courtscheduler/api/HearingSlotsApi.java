package uk.gov.moj.cpp.courtscheduler.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotSearchRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListHearingSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.envelope.SkipEnvelope;
import uk.gov.moj.cpp.courtscheduler.exception.MoveHearingToPastDateNoSessionException;
import uk.gov.moj.cpp.courtscheduler.openapi.api.HearingslotsOpenApi;
import uk.gov.moj.cpp.courtscheduler.openapi.api.HearingsOpenApi;

/**
 * Implements {@link HearingslotsOpenApi} and {@link HearingsOpenApi} — replaces the legacy
 * {@code @Handles} for {@code courtscheduler.update.hearing.slots}, {@code courtscheduler.get.hearing.slots},
 * {@code courtscheduler.remove.hearing.slots}, {@code courtscheduler.list.hearings-in-court-sessions},
 * {@code courtscheduler.search.update.hearing.slots}, {@code courtscheduler.search.book.hearing.slots}
 * and {@code courtscheduler.move-hearing-to-past-date}.
 *
 * <p>Lives alongside the other {@code *Api} classes in this package even though
 * there was no legacy WildFly {@code HearingSlotsApi.java} — the hearing-slot
 * action handlers were originally folded into {@link CourtSchedulerApi}.</p>
 */
@RestController
public class HearingSlotsApi implements HearingslotsOpenApi, HearingsOpenApi {

    private static final Logger LOG = LoggerFactory.getLogger(HearingSlotsApi.class);

    // A multi-day move books a full court day per sitting day; a single-day move uses the submitted window.
    private static final int MULTI_DAY_DURATION_MINUTES = 360;
    private static final MediaType MOVE_TO_PAST_DATE_RESPONSE_MT =
            MediaType.parseMediaType("application/vnd.courtscheduler.move-hearing-to-past-date.response+json");

    private final SlotsUpdateService slotsUpdateService;
    private final SlotsSearchService slotsSearchService;
    private final SlotsRemoveService slotsRemoveService;
    private final AllocatedListingService allocatedListingService;
    private final HearingSlotsApiValidator hearingSlotsApiValidator;
    private final AllocatedSlotConverter allocatedSlotConverter;
    private final HearingSlotRequestParamConverter hearingSlotRequestParamConverter;
    private final HearingSlotSearchRequestConverter hearingSlotSearchRequestConverter;
    private final ListHearingSlotConverter listHearingSlotConverter;
    private final ObjectMapper objectMapper;

    public HearingSlotsApi(final SlotsUpdateService slotsUpdateService,
                           final SlotsSearchService slotsSearchService,
                           final SlotsRemoveService slotsRemoveService,
                           final AllocatedListingService allocatedListingService,
                           final HearingSlotsApiValidator hearingSlotsApiValidator,
                           final AllocatedSlotConverter allocatedSlotConverter,
                           final HearingSlotRequestParamConverter hearingSlotRequestParamConverter,
                           final HearingSlotSearchRequestConverter hearingSlotSearchRequestConverter,
                           final ListHearingSlotConverter listHearingSlotConverter,
                           final ObjectMapper objectMapper) {
        this.slotsUpdateService = slotsUpdateService;
        this.slotsSearchService = slotsSearchService;
        this.slotsRemoveService = slotsRemoveService;
        this.allocatedListingService = allocatedListingService;
        this.hearingSlotsApiValidator = hearingSlotsApiValidator;
        this.allocatedSlotConverter = allocatedSlotConverter;
        this.hearingSlotRequestParamConverter = hearingSlotRequestParamConverter;
        this.hearingSlotSearchRequestConverter = hearingSlotSearchRequestConverter;
        this.listHearingSlotConverter = listHearingSlotConverter;
        this.objectMapper = objectMapper;
    }

    private String toJson(final Map<String, Object> body) {
        try {
            return body == null ? "{}" : objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid request body", e);
        }
    }

    private JsonObject toJsonObject(final Map<String, Object> body) {
        try (var reader = Json.createReader(new StringReader(toJson(body)))) {
            return reader.readObject();
        }
    }

    /** PUT /hearingslots — allocate hearing slots. */
    @Override
    public ResponseEntity<Map<String, Object>> putUpdateHearingSlots(final Map<String, Object> body) {
        LOG.info("courtscheduler.update.hearing.slots: {}", body);
        final List<AllocatedSlot> slots = allocatedSlotConverter.convert(toJson(body)).getHearingSlots();
        final JsonObject schedules = slotsUpdateService.update(slots);
        return ResponseEntity.ok(uk.gov.moj.cpp.courtscheduler.config.JsonValueConverter.toMap(schedules));
    }

    /** GET /hearingslots — search hearing slots / hearing ids (depending on Accept). */
    @Override
    public ResponseEntity<Map<String, Object>> getHearingSlots(final String panel,
                                                               final String sessionStartDate,
                                                               final String sessionEndDate,
                                                               final String pageSize,
                                                               final String pageNumber,
                                                               final String exactHearingStartDateTime,
                                                               final String oucodeL2Code,
                                                               final String ouCode,
                                                               final String courtRoomId,
                                                               final String courtRoomNumber,
                                                               final String businessType,
                                                               final String courtSession,
                                                               final String isSlotBased,
                                                               final String hearingStartTime,
                                                               final String duration,
                                                               final Boolean showOverbookedSlots,
                                                               final String caseIdentifier) {
        final Map<String, Object> qp = new LinkedHashMap<>();
        qp.put("panel", panel);
        qp.put("sessionStartDate", sessionStartDate);
        qp.put("sessionEndDate", sessionEndDate);
        qp.put("pageSize", pageSize);
        qp.put("pageNumber", pageNumber);
        if (exactHearingStartDateTime != null) qp.put("exactHearingStartDateTime", exactHearingStartDateTime);
        if (oucodeL2Code != null) qp.put("oucodeL2Code", oucodeL2Code);
        if (ouCode != null) qp.put("ouCode", ouCode);
        if (courtRoomId != null) qp.put("courtRoomId", courtRoomId);
        if (courtRoomNumber != null) qp.put("courtRoomNumber", courtRoomNumber);
        if (businessType != null) qp.put("businessType", businessType);
        if (courtSession != null) qp.put("courtSession", courtSession);
        if (isSlotBased != null) qp.put("isSlotBased", isSlotBased);
        if (hearingStartTime != null) qp.put("hearingStartTime", hearingStartTime);
        if (duration != null) qp.put("duration", duration);
        if (showOverbookedSlots != null) qp.put("showOverbookedSlots", showOverbookedSlots.toString());
        if (caseIdentifier != null) qp.put("caseIdentifier", caseIdentifier);

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(toJsonObject(qp));

        final JsonObject validate = hearingSlotsApiValidator.getHearingSlotsValidation(param);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }

        final String accept = currentAcceptHeader();
        final JsonObject result = "application/vnd.courtscheduler.get.hearing.ids+json".equals(accept)
                ? allocatedListingService.getHearingIds(param)
                : slotsSearchService.search(param);
        return ResponseEntity.ok(jsonObjectAsMap(result));
    }

    private static String currentAcceptHeader() {
        final ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getRequest().getHeader("Accept");
    }

    /** Top-level flatten so the response shape matches the legacy {@code envelope.payload} body. */
    private static Map<String, Object> jsonObjectAsMap(final JsonObject obj) {
        final Map<String, Object> out = new LinkedHashMap<>();
        if (obj != null) {
            for (final Map.Entry<String, JsonValue> e : obj.entrySet()) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    /** DELETE /hearingslots/{hearingId} — remove all allocated listings for a hearing. */
    @Override
    public ResponseEntity<Void> deleteHearingSlots(final String hearingId) {
        LOG.info("courtscheduler.remove.hearing.slots hearingId={}", hearingId);
        slotsRemoveService.remove(hearingId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /** PUT /list/hearingslots — allocate multiple hearing slots in court sessions. */
    @Override
    public ResponseEntity<Map<String, Object>> putListHearingSlotsInCourtSessions(final Map<String, Object> body) {
        LOG.info("courtscheduler.list.hearings-in-court-sessions: {}", body);
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

    /** PUT /searchupdate/hearingslots — search and allocate when courtScheduleId is null. */
    @Override
    public ResponseEntity<Void> putSearchUpdateHearingSlots(final Map<String, Object> body) {
        LOG.info("courtscheduler.search.update.hearing.slots: {}", body);
        final List<AllocatedSlot> slots = allocatedSlotConverter.convert(toJson(body)).getHearingSlots();
        slotsUpdateService.searchUpdate(slots);
        return ResponseEntity.noContent().build();
    }

    /** GET /searchlist/hearingslots — search and list hearings in court sessions. */
    @Override
    @SkipEnvelope
    public ResponseEntity<Map<String, Object>> getSearchListHearingSlots(final String hearingId,
                                                                         final String courtCentreId,
                                                                         final String hearingDate,
                                                                         final Integer durationInMinutes,
                                                                         final String courtRoomId,
                                                                         final String hearingSessionDateSearchCutOff,
                                                                         final String hearingStartTime,
                                                                         final Boolean isPolice) {
        final Map<String, Object> qp = new LinkedHashMap<>();
        qp.put("hearingId", hearingId);
        qp.put("courtCentreId", courtCentreId);
        qp.put("hearingDate", hearingDate);
        qp.put("durationInMinutes", durationInMinutes);
        if (courtRoomId != null) qp.put("courtRoomId", courtRoomId);
        if (hearingSessionDateSearchCutOff != null) qp.put("hearingSessionDateSearchCutOff", hearingSessionDateSearchCutOff);
        if (hearingStartTime != null) qp.put("hearingStartTime", hearingStartTime);
        if (isPolice != null) qp.put("isPolice", isPolice);

        final HearingSlotSearchRequest req = hearingSlotSearchRequestConverter.convert(toJsonObject(qp));
        final JsonObject validate = hearingSlotsApiValidator.searchAndBookRequestValidation(req);
        if (!validate.isEmpty()) {
            throw new ValidationException(validate);
        }
        final HearingSlotSearchAndBookResponse response = slotsUpdateService.searchAndBook(req);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("hearingSlots", response);
        return ResponseEntity.ok(result);
    }

    /**
     * POST /hearings/{hearingId} — move a hearing to past-dated session(s). Supports both jurisdictions
     * (listing only calls this for MAGISTRATES today; CROWN stays listing-side until Phase 2), an
     * optional room-scoped and time-of-day (range-containment) search, and a multi-day [startDate,
     * endDate] span booked atomically (one booked slot per sitting day).
     */
    @Override
    public ResponseEntity<Map<String, Object>> postMoveHearingToPastDate(final String hearingId,
                                                                         final Map<String, Object> body) {
        final JsonObject payload = toJsonObject(body);
        LOG.info("courtscheduler.move-hearing-to-past-date requested for hearingId {}: {}", hearingId, payload);

        final String courtCentreId = payload.getString("courtCentreId");
        final String jurisdiction = payload.getString("jurisdiction");
        final String courtRoomId = payload.getString("courtRoomId");
        // startTime/endTime are absolute UTC instants (e.g. 2026-07-20T10:30:00.000Z); the day and
        // time-of-day for the range-containment search AND the booked slots are derived from them.
        final ZonedDateTime startInstant = ZonedDateTime.parse(payload.getString("startTime"));
        final ZonedDateTime endInstant = payload.containsKey("endTime") && !payload.isNull("endTime")
                ? ZonedDateTime.parse(payload.getString("endTime")) : startInstant;
        final LocalDate startDate = startInstant.toLocalDate();
        final LocalDate endDate = endInstant.toLocalDate();
        final String hearingStartTime = String.format("%02d:%02d", startInstant.getHour(), startInstant.getMinute());
        final String hearingEndTime = String.format("%02d:%02d", endInstant.getHour(), endInstant.getMinute());
        // Duration rule: a multi-day move (endDate after startDate) books a full court day per sitting
        // day; a single-day move uses the submitted window.
        final int durationInMinutes = endDate.isAfter(startDate)
                ? MULTI_DAY_DURATION_MINUTES
                : (int) java.time.temporal.ChronoUnit.MINUTES.between(startInstant, endInstant);

        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (startDate.isAfter(today) || endDate.isAfter(today)) {
            return moveErrorResponse("FUTURE_DATE_NOT_ALLOWED",
                    "startDate/endDate must not be after today");
        }

        try {
            final List<MoveHearingToPastDateResponse> responses = slotsUpdateService.moveHearingToPastDate(
                    hearingId, courtCentreId, courtRoomId, startDate, endDate, hearingStartTime, hearingEndTime, jurisdiction, durationInMinutes);
            final Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("bookedSlots", objectMapper.convertValue(responses, new TypeReference<List<Map<String, Object>>>() {}));
            return ResponseEntity.ok()
                    .contentType(MOVE_TO_PAST_DATE_RESPONSE_MT)
                    .body(responseBody);
        } catch (MoveHearingToPastDateNoSessionException e) {
            LOG.warn("Move hearing to past date no session: {}", e.getMessage());
            // Keep the propagated message (team/pasthearings behaviour) - the listing side already
            // normalises NO_SESSION_FOUND to its fixed user-facing copy.
            return moveErrorResponse("NO_SESSION_FOUND", e.getMessage());
        }
    }

    /**
     * Bare {@code {errorCode, message}} 422 body: the legacy JAX-RS UnprocessableEntityException
     * emitted its errors JsonObject as the entity, and listing's MoveHearingToPastDateException
     * reads {@code errorCode} at the top level — GlobalExceptionHandler's {@code {"errors": ...}}
     * wrapper would break that contract, so this endpoint returns the 422 directly.
     */
    private static ResponseEntity<Map<String, Object>> moveErrorResponse(final String errorCode, final String message) {
        final Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("errorCode", errorCode);
        errorBody.put("message", message == null ? "" : message);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(errorBody);
    }
}
