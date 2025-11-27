package uk.gov.moj.cpp.courtscheduler.api.validator;

import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;

import javax.enterprise.context.ApplicationScoped;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class JudiciariesApiValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciariesApiValidator.class);
    private static final String JUDICIARIES = "judiciaries";
    private static final String SESSIONIDS = "sessionIds";
    private static final String JUDICIARY_ID = "judiciaryId";

    public JsonObject validateUnassignJudiciaryRequest(final JsonObject payload) {
        LOGGER.info("Validating unassign judiciary request : {}", payload);

        if (!payload.containsKey(JUDICIARIES)) {
            return getMessage("judiciaries array is required");
        }

        if (payload.isNull(JUDICIARIES)) {
            return getMessage("judiciaries array must contain at least one item");
        }

        final JsonArray judiciaries = payload.getJsonArray(JUDICIARIES);
        if (judiciaries == null || judiciaries.isEmpty()) {
            return getMessage("judiciaries array must contain at least one item");
        }

        for (int i = 0; i < judiciaries.size(); i++) {
            final JsonObject judiciary = judiciaries.getJsonObject(i);
            final String judiciaryId = judiciary.getString(JUDICIARY_ID, "");

            if (judiciaryId.isEmpty()) {
                return getMessage(String.format("judiciaryId is required in judiciaries[%d]", i));
            }

            if (!judiciary.containsKey(SESSIONIDS)) {
                return getMessage(String.format("sessionIds array is required in judiciaries[%d]", i));
            }

            if (judiciary.isNull(SESSIONIDS)) {
                return getMessage(String.format("sessionIds array must contain at least one item in judiciaries[%d]", i));
            }

            final JsonArray sessionIds = judiciary.getJsonArray(SESSIONIDS);
            if (sessionIds == null || sessionIds.isEmpty()) {
                return getMessage(String.format("sessionIds array must contain at least one item in judiciaries[%d]", i));
            }

            for (int j = 0; j < sessionIds.size(); j++) {
                final String sessionId = sessionIds.getString(j, "");
                if (sessionId.isEmpty()) {
                    return getMessage(String.format("sessionId is required in judiciaries[%d].sessionIds[%d]", i, j));
                }
            }
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject getMessage(final String errorMessage) {
        return createObjectBuilder()
                .add(ERROR_MESSAGE, errorMessage)
                .build();
    }
}

