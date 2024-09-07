package uk.gov.moj.cpp.courtscheduler.api.validator;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;

import javax.json.JsonObject;

public class ValidationException extends BadRequestException {
    private final JsonObject errors;

    public ValidationException(JsonObject errors) {
        super(errors.asJsonObject().toString());
        this.errors = errors;
    }

    public JsonObject getErrors() {
        return errors;
    }
}

