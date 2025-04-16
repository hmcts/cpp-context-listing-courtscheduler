package uk.gov.moj.cpp.courtscheduler.common;

import static javax.json.Json.createObjectBuilder;

import javax.json.JsonObject;

public  class CommonUtils {
    public static JsonObject buildErrorResponse(String errorMessage) {
        return createObjectBuilder()
                .add("errorMessage", errorMessage)
                .build();
    }

    private CommonUtils() {
        // Private constructor to prevent instantiation
    }
}
