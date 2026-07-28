package uk.gov.moj.cpp.courtscheduler.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
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
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.envelope.SkipEnvelope;
import uk.gov.moj.cpp.courtscheduler.openapi.api.HearingslotsOpenApi;

/**
 * Implements {@link HearingslotsOpenApi} — replaces the legacy {@code @Handles} for
 * {@code courtscheduler.update.hearing.slots} and {@code courtscheduler.get.hearing.slots}
 * (+ {@code courtscheduler.get.hearing.ids} via Accept negotiation).
 *
 * <p>The SPRDT-1089 reshape moved the booking family to {@code POST /hearings/{hearingId}}
 * (see {@link CourtSchedulerApi}) and slot release to {@code DELETE /sessions/{hearingId}};
 * {@code /searchupdate/hearingslots} and {@code /searchlist/hearingslots} were removed
 * from the contract.</p>
 */
@RestController
public class HearingSlotsApi implements HearingslotsOpenApi {

    private static final Logger LOG = LoggerFactory.getLogger(HearingSlotsApi.class);

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
                                                               final String jurisdiction,
                                                               final String exactHearingStartDateTime,
                                                               final String oucodeL2Code,
                                                               final String ouCode,
                                                               final String courtRoomId,
                                                               final String courtRoomNumber,
                                                               final String businessType,
                                                               final String courtSession,
                                                               final String isSlotBased,
                                                               final String hearingStartTime,
                                                               final String availableDurationMins,
                                                               final Boolean showOverbookedSlots,
                                                               final String status) {
        final Map<String, Object> qp = new LinkedHashMap<>();
        qp.put("panel", panel);
        qp.put("sessionStartDate", sessionStartDate);
        qp.put("sessionEndDate", sessionEndDate);
        qp.put("pageSize", pageSize);
        qp.put("pageNumber", pageNumber);
        qp.put("jurisdiction", jurisdiction);
        if (exactHearingStartDateTime != null) qp.put("exactHearingStartDateTime", exactHearingStartDateTime);
        if (oucodeL2Code != null) qp.put("oucodeL2Code", oucodeL2Code);
        if (ouCode != null) qp.put("ouCode", ouCode);
        if (courtRoomId != null) qp.put("courtRoomId", courtRoomId);
        if (courtRoomNumber != null) qp.put("courtRoomNumber", courtRoomNumber);
        if (businessType != null) qp.put("businessType", businessType);
        if (courtSession != null) qp.put("courtSession", courtSession);
        if (isSlotBased != null) qp.put("isSlotBased", isSlotBased);
        if (hearingStartTime != null) qp.put("hearingStartTime", hearingStartTime);
        if (availableDurationMins != null) qp.put("availableDurationMins", availableDurationMins);
        if (showOverbookedSlots != null) qp.put("showOverbookedSlots", showOverbookedSlots.toString());
        if (status != null) qp.put("status", status);

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
}
