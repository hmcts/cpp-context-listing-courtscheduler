package uk.gov.moj.cpp.courtscheduler.api.validator;

import java.io.Serializable;

import javax.json.JsonObject;

public class ValidationException extends RuntimeException implements Serializable {
    private final JsonObject errors;

    public ValidationException(JsonObject errors) {
        super("Validation failed");
        this.errors = errors;
    }

    public JsonObject getErrors() {
        return errors;
    }
}

