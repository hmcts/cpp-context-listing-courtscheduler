package uk.gov.moj.cpp.courtscheduler.api.validator;

import jakarta.json.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Migrated from {@code uk.gov.justice.services.adapter.rest.exception.BadRequestException}
 * to Spring's {@code ResponseStatusException} (HTTP 400). Carries the validation errors
 * JSON for {@link uk.gov.moj.cpp.courtscheduler.controllers.GlobalExceptionHandler}.
 */
public class ValidationException extends ResponseStatusException {

    private final JsonObject errors;

    public ValidationException(final JsonObject errors) {
        super(HttpStatus.BAD_REQUEST, errors == null ? "" : errors.getString("errorMessage", errors.toString()));
        this.errors = errors;
    }

    public JsonObject getErrors() {
        return errors;
    }
}
