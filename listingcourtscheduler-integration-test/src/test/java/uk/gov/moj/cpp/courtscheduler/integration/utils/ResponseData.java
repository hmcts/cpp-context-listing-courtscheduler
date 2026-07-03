package uk.gov.moj.cpp.courtscheduler.integration.utils;

import jakarta.ws.rs.core.Response;

/**
 * Replacement for {@code uk.gov.justice.services.test.utils.core.http.ResponseData}.
 * Exposes {@code getStatus()} returning the JAX-RS {@link Response.Status} enum (so
 * legacy Hamcrest assertions like {@code assertThat(responseData.getStatus(), is(OK))}
 * compare against an actual enum constant) plus {@code getPayload()} for the body.
 */
public final class ResponseData {

    private final int rawStatus;
    private final String payload;

    public ResponseData(final int rawStatus, final String payload) {
        this.rawStatus = rawStatus;
        this.payload = payload;
    }

    /**
     * Returns the JAX-RS {@link Response.Status} enum so {@code is(Response.Status.OK)}
     * style Hamcrest assertions work via {@link Enum#equals}. {@link Response.Status}
     * exposes {@code getStatusCode()} which is what the rest of the legacy IT code
     * relies on.
     */
    public Response.Status getStatus() {
        return Response.Status.fromStatusCode(rawStatus);
    }

    public int getRawStatus() {
        return rawStatus;
    }

    public String getPayload() {
        return payload;
    }

    public Response asLegacyResponse() {
        final Response.ResponseBuilder builder = Response.status(rawStatus);
        if (payload != null) {
            builder.entity(payload);
        }
        return builder.build();
    }
}
