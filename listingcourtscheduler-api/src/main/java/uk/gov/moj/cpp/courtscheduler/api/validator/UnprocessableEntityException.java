package uk.gov.moj.cpp.courtscheduler.api.validator;

import jakarta.json.JsonObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Migrated from JAX-RS {@code WebApplicationException} (HTTP 422) to Spring's
 * {@code ResponseStatusException}. Carries the validation errors JSON for the
 * {@link uk.gov.moj.cpp.courtscheduler.controllers.GlobalExceptionHandler} to render.
 */
public class UnprocessableEntityException extends ResponseStatusException {
    private final JsonObject errors;

    public UnprocessableEntityException(final JsonObject errors) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, errors == null ? "" : errors.toString());
        this.errors = errors;
    }

    public JsonObject getErrors() {
        return errors;
    }
}
