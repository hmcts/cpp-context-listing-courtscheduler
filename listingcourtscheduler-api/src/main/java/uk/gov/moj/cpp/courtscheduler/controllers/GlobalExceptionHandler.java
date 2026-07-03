package uk.gov.moj.cpp.courtscheduler.controllers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reproduces the legacy framework's error response shape so the UI's error
 * handlers continue to work.
 *
 * <ul>
 *   <li>{@link uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException} → 400 with the
 *       validator's full {@code JsonObject} body (legacy shape: {@code {"errorMessage": "..."}}).</li>
 *   <li>{@link IllegalArgumentException} / bean-validation failures → 400 with {@code {"error": "<msg>"}}.</li>
 *   <li>{@link ResponseStatusException} (other) → status code with {@code {"error": "<reason>"}}.</li>
 *   <li>{@link uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException} → 500
 *       with {@code {"error": "<msg>"}} and an error log entry.</li>
 *   <li>Anything else → 500 with {@code {"error": "Internal Server Error"}}.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ValidationFailedException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(final ValidationFailedException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(ex.getErrorMessages()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleArgumentNotValid(final MethodArgumentNotValidException ex) {
        final List<String> messages = ex.getBindingResult().getAllErrors().stream()
                .map(e -> e.getDefaultMessage() == null ? e.toString() : e.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(messages));
    }

    /** Missing or unparseable required query parameters / unparseable JSON body → 400 (Spring's default is 500). */
    @ExceptionHandler({
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            com.fasterxml.jackson.core.JsonParseException.class,
            com.fasterxml.jackson.databind.JsonMappingException.class,
            NumberFormatException.class,
            jakarta.json.JsonException.class
    })
    public ResponseEntity<Map<String, Object>> handleMissingRequestParam(final Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(List.of(ex.getMessage())));
    }

    /** Method validation (jakarta.validation on @RequestParam) failures → 400. */
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            final jakarta.validation.ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(List.of(ex.getMessage())));
    }

    /** {@code @ModelAttribute}-bound parameter binding failures → 400. */
    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<Map<String, Object>> handleBindException(
            final org.springframework.validation.BindException ex) {
        final List<String> messages = ex.getBindingResult().getAllErrors().stream()
                .map(e -> e.getDefaultMessage() == null ? e.toString() : e.getDefaultMessage())
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(messages));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(final IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(List.of(ex.getMessage())));
    }

    /**
     * Persistence-store failures bubbling up from the view-store repositories. Map to
     * 500 explicitly so the catch-all {@link #handleAny} doesn't shadow them with a
     * generic "Internal Server Error" message that hides the underlying cause from
     * the operator log.
     */
    @ExceptionHandler(uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException.class)
    public ResponseEntity<Map<String, Object>> handlePersistenceStore(
            final uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException ex) {
        LOG.error("Persistence-store failure", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(List.of(ex.getMessage())));
    }

    /** Map upstream CPP service failures to 502 Bad Gateway (rather than masking as 500). */
    @ExceptionHandler(org.springframework.web.client.RestClientResponseException.class)
    public ResponseEntity<Map<String, Object>> handleUpstream(
            final org.springframework.web.client.RestClientResponseException ex) {
        LOG.warn("Upstream CPP service call failed with {}: {}",
                ex.getStatusCode(), ex.getResponseBodyAsString());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorBody(List.of(
                        "Upstream service responded with " + ex.getStatusCode().value())));
    }

    /** {@link NullPointerException} from a converter likely indicates a missing payload field. */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNpe(final NullPointerException ex) {
        LOG.warn("NPE while processing request body (likely missing field): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(List.of(
                        "Request body is missing a required field: " + ex.getMessage())));
    }

    /** Empty-list / out-of-range access in a service is a request-body shape problem, not a server bug. */
    @ExceptionHandler(IndexOutOfBoundsException.class)
    public ResponseEntity<Map<String, Object>> handleIndexOob(final IndexOutOfBoundsException ex) {
        LOG.warn("IndexOutOfBoundsException while processing request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(List.of("Request body has missing or empty required collection")));
    }

    /** Map legacy {@code ConverterException} (request body shape mismatches) to 400. */
    @ExceptionHandler(uk.gov.moj.cpp.courtscheduler.api.converter.ConverterException.class)
    public ResponseEntity<Map<String, Object>> handleConverter(
            final uk.gov.moj.cpp.courtscheduler.api.converter.ConverterException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody(List.of(ex.getMessage())));
    }

    /** Map {@code UnprocessableEntityException} (422) preserving its errors body. */
    @ExceptionHandler(uk.gov.moj.cpp.courtscheduler.api.validator.UnprocessableEntityException.class)
    public ResponseEntity<Map<String, Object>> handleUnprocessable(
            final uk.gov.moj.cpp.courtscheduler.api.validator.UnprocessableEntityException ex) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("errors", ex.getErrors());
        body.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Validator-driven 400s (legacy {@code BadRequestException}) carry the validator's
     * errors {@code JsonObject} which usually has a single {@code errorMessage} key.
     * The migration normalised the response shape to {@code {"error":"<msg>"}} (matching
     * every other 400 in {@link #errorBody}); extract the validator message and wrap it
     * in that shape so all 400 bodies share the same key.
     */
    @ExceptionHandler(uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(
            final uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(List.of(extractValidationMessage(ex))));
    }

    private static String extractValidationMessage(
            final uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException ex) {
        final jakarta.json.JsonObject errors = ex.getErrors();
        if (errors == null) {
            return ex.getMessage() == null ? "" : ex.getMessage();
        }
        final jakarta.json.JsonString errorMessage = errors.getJsonString("errorMessage");
        if (errorMessage != null) {
            return errorMessage.getString();
        }
        return errors.toString();
    }

    /**
     * A request whose {@code Accept} matches no producible media type must yield 406 Not Acceptable
     * (as WildFly/JAX-RS did) — without this, the catch-all {@link #handleAny} would map it to 500,
     * a client-visible regression. (Conforming clients sending the vendor {@code Accept} never hit this.)
     */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleNotAcceptable(
            final org.springframework.web.HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(errorBody(List.of(ex.getMessage())));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(final ResponseStatusException ex) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getReason() == null ? ex.getMessage() : ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(final Exception ex) {
        LOG.error("Unhandled exception", ex);
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Legacy error body shape: {@code {"error":"<msg>"}} (single-field map). Tests
     * compare the entire response string with this exact shape, so we return only
     * the {@code error} key — no {@code timestamp}, no list wrapping.
     */
    private static Map<String, Object> errorBody(final List<String> messages) {
        final Map<String, Object> body = new LinkedHashMap<>();
        if (messages.isEmpty()) {
            body.put("error", "");
        } else if (messages.size() == 1) {
            body.put("error", messages.get(0));
        } else {
            body.put("error", String.join("; ", messages));
        }
        return body;
    }
}
