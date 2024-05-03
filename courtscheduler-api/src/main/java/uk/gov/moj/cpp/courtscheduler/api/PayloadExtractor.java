package uk.gov.moj.cpp.courtscheduler.api;
import uk.gov.justice.services.adapter.rest.exception.BadRequestException;
import uk.gov.justice.services.messaging.JsonEnvelope;

import javax.json.JsonObject;

public class PayloadExtractor {
    private static final String SOURCE_ID = "sourceId";
    private static final String SOURCE_TYPE = "sourceType";
    private static final String TARGET_ID = "targetId";
    private static final String TARGET_TYPE = "targetType";

    private static final String BAD_REQUEST_ERROR = "Bad Request, invalid set of query parameters provided. Please query with either (targetType and sourceId) or (targetType and sourceType) or (targetType and targetId)";

    public JsonObject extractPayloadOrThrowBadRequestException(final JsonEnvelope envelope) {

        final JsonObject payload = envelope.payloadAsJsonObject();

        if (payload.containsKey(TARGET_TYPE)
                && (payload.containsKey(SOURCE_ID) || payload.containsKey(SOURCE_TYPE) || payload.containsKey(TARGET_ID))) {
            return payload;
        }

        throw new BadRequestException(BAD_REQUEST_ERROR);
    }
}
