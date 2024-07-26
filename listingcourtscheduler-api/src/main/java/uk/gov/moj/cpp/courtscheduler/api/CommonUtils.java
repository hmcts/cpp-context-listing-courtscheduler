package uk.gov.moj.cpp.courtscheduler.api;

import static javax.json.Json.createObjectBuilder;

import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationStatus;

import java.util.Objects;

import javax.json.JsonObject;

public  class CommonUtils {
    public static JsonObject getValidationResult(final String message) {
        final ValidationStatus validationStatus = Objects.nonNull(message) ? ValidationStatus.FAILURE : ValidationStatus.SUCCESS;
        return  createObjectBuilder().add("validationResult",createObjectBuilder()
                .add("status", validationStatus.getValidationStatus())
                .add("validationError", message)
                .build()).build();
    }
}
