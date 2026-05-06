package uk.gov.moj.cpp.courtscheduler.common.service;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.moj.cpp.courtscheduler.common.config.CourtSchedulerSystemUserConfig;

/**
 * Replaces the Justice Services framework's
 * {@code Requester#requestAsAdmin(envelope, JsonObject.class)} pattern with an
 * explicit Spring {@link RestTemplate} call against the configured
 * {@code referencedata.base-url} / {@code usersgroups.base-url} services.
 *
 * <p>The legacy framework looked up the URL from the {@code @Handles}/RAML
 * mapping of the target microservice; this client takes the URL path + accept
 * media type explicitly. Each call sends:</p>
 * <ul>
 *   <li>{@code CJSCPPUID} = configured system user UUID,</li>
 *   <li>{@code Accept} = caller-supplied vendor media type,</li>
 *   <li>query parameters as supplied.</li>
 * </ul>
 *
 * <p>All calls return {@link JsonObject} so the existing parsing code in
 * {@code ReferenceDataService} continues to work unchanged.</p>
 */
@Component
public class CommonPlatformQueryClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonPlatformQueryClient.class);

    private final RestTemplate restTemplate;
    private final CourtSchedulerSystemUserConfig systemUserConfig;
    private final String referenceDataBaseUrl;
    private final String usersGroupsBaseUrl;

    public CommonPlatformQueryClient(final CourtSchedulerSystemUserConfig systemUserConfig,
                                     @Value("${referencedata.base-url:}") final String referenceDataBaseUrl,
                                     @Value("${usersgroups.base-url:}") final String usersGroupsBaseUrl) {
        this.systemUserConfig = systemUserConfig;
        this.referenceDataBaseUrl = referenceDataBaseUrl;
        this.usersGroupsBaseUrl = usersGroupsBaseUrl;
        this.restTemplate = new RestTemplate();
    }

    /**
     * GET against {@code ${referencedata.base-url}/<path>} with the supplied query params,
     * Accept header and the system-user CJSCPPUID. Returns the response body parsed as a
     * {@link JsonObject}.
     */
    public JsonObject getReferenceData(final String path,
                                       final String acceptMediaType,
                                       final Map<String, ?> queryParams) {
        return get(referenceDataBaseUrl, path, acceptMediaType, queryParams);
    }

    /** Same as {@link #getReferenceData} but against the users-groups query API. */
    public JsonObject getUsersGroups(final String path,
                                     final String acceptMediaType,
                                     final Map<String, ?> queryParams) {
        return get(usersGroupsBaseUrl, path, acceptMediaType, queryParams);
    }

    private JsonObject get(final String baseUrl,
                           final String path,
                           final String acceptMediaType,
                           final Map<String, ?> queryParams) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Base URL is not configured for path: " + path);
        }

        final UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path(path);
        if (queryParams != null) {
            queryParams.forEach((k, v) -> { if (v != null) { builder.queryParam(k, v); } });
        }
        final URI uri = builder.build().toUri();

        final HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, acceptMediaType);
        headers.set("CJSCPPUID", systemUserConfig.getRequiredSystemUserId());

        LOGGER.debug("Calling {} with Accept={} ", uri, acceptMediaType);
        final ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return parse(response.getBody());
    }

    private static JsonObject parse(final String body) {
        if (body == null || body.isBlank()) {
            return Json.createObjectBuilder().build();
        }
        try (var reader = Json.createReader(new StringReader(body))) {
            return reader.readObject();
        }
    }
}
