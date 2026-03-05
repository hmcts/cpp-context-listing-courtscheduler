package uk.gov.moj.cpp.courtscheduler.api.validator;

import static io.netty.handler.codec.http.HttpResponseStatus.UNPROCESSABLE_ENTITY;

import javax.json.JsonObject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

public class UnprocessableEntityException extends WebApplicationException {
    private final JsonObject errors;
    
    public UnprocessableEntityException(JsonObject errors) {
        super(Response.status(UNPROCESSABLE_ENTITY.code())
                .entity(errors)
                .type("application/json")
                .build());
        this.errors = errors;
    }
    
    public JsonObject getErrors() {
        return errors;
    }
}

