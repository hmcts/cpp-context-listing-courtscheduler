package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;

import org.springframework.stereotype.Service;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class JudiciariesApiValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciariesApiValidator.class);
    private static final String JUDICIARIES = "judiciaries";
    private static final String SESSIONIDS = "sessionIds";
    private static final String JUDICIARY_ID = "judiciaryId";
    private static final String SKIP_VALIDATIONS = "skipValidations";

    public JsonObject validateUnassignJudiciaryRequest(final JsonObject payload) {
        LOGGER.info("Validating unassign judiciary request : {}", payload);

        if (payload == null) {
            return getMessage("Request payload is required");
        }

        // Skip validation if skipValidations flag is set to true
        if (payload.containsKey(SKIP_VALIDATIONS) && payload.getBoolean(SKIP_VALIDATIONS)) {
            return EMPTY_JSON_OBJECT;
        }

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

