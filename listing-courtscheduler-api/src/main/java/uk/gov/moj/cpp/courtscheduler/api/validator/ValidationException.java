package uk.gov.moj.cpp.courtscheduler.api.validator;

import javax.json.JsonObject;

public class ValidationException extends RuntimeException {
    private final JsonObject errors;

    public ValidationException(JsonObject errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public JsonObject getErrors() {
        return errors;
    }
}

