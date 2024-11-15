package uk.gov.moj.cpp.courtscheduler.api.validator;

import uk.gov.justice.services.adapter.rest.exception.BadRequestException;

import javax.json.JsonObject;

public class ValidationException extends BadRequestException {
    public ValidationException(JsonObject errors) {
        super(errors.asJsonObject().getString("errorMessage"));
    }
}
