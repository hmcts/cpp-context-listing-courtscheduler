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

        final JsonArray judiciaries = payload.getJsonArray(JUDICIARIES);


        for (int i = 0; i < judiciaries.size(); i++) {
            final JsonObject judiciary = judiciaries.getJsonObject(i);
            final String judiciaryId = judiciary.getString(JUDICIARY_ID, "");

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

