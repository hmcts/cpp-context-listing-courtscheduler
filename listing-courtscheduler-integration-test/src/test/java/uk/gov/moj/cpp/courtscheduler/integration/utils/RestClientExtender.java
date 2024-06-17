package uk.gov.moj.cpp.courtscheduler.integration.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.justice.services.test.utils.core.rest.ResteasyClientBuilderFactory;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

public class RestClientExtender extends RestClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientExtender.class);

    public Response putCommand(final String url, final String contentType, final String requestPayload) {
        Entity<String> entity = Entity.entity(requestPayload, MediaType.valueOf(contentType));
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Making POST request to '{}' with Content Type '{}'", url, contentType);
            LOGGER.info("Request payload: '{}'", requestPayload);
        }

        Response response = ResteasyClientBuilderFactory.clientBuilder().build().target(url).request().put(entity);
        if (LOGGER.isInfoEnabled()) {
            Response.StatusType statusType = response.getStatusInfo();
            LOGGER.info("Received response status '{}' '{}'", statusType.getStatusCode(), statusType.getReasonPhrase());
        }

        return response;
    }

    public Response putCommand(final String url, final String contentType, final String requestPayload, final MultivaluedMap<String, Object> headers) {
        Entity<String> entity = Entity.entity(requestPayload, MediaType.valueOf(contentType));
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Making POST request to '{}' with Content Type '{}'", url, contentType);
            LOGGER.info("Request payload: '{}'", requestPayload);
            LOGGER.info("Headers: {}", headers);
        }

        Response response = ResteasyClientBuilderFactory.clientBuilder().build().target(url).request().headers(headers).put(entity);
        if (LOGGER.isInfoEnabled()) {
            Response.StatusType statusType = response.getStatusInfo();
            LOGGER.info("Received response status '{}' '{}'", statusType.getStatusCode(), statusType.getReasonPhrase());
        }

        return response;
    }

    public Response patchCommand(final String url, final String contentType, final String requestPayload, final MultivaluedMap<String, Object> headers) {
        Entity<String> entity = Entity.entity(requestPayload, MediaType.valueOf(contentType));
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Making POST request to '{}' with Content Type '{}'", url, contentType);
            LOGGER.info("Request payload: '{}'", requestPayload);
            LOGGER.info("Headers: {}", headers);
        }

        Response response = ResteasyClientBuilderFactory.clientBuilder().build().target(url).request().headers(headers).method("PATCH", entity);
        if (LOGGER.isInfoEnabled()) {
            Response.StatusType statusType = response.getStatusInfo();
            LOGGER.info("Received response status '{}' '{}'", statusType.getStatusCode(), statusType.getReasonPhrase());
        }

        return response;
    }
}
