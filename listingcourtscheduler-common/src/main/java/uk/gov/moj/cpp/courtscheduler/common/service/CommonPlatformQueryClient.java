package uk.gov.moj.cpp.courtscheduler.common.service;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.moj.cpp.courtscheduler.common.config.CourtSchedulerSystemUserConfig;

/**
 * Replaces the Justice Services framework's
 * {@code Requester#requestAsAdmin(envelope, JsonObject.class)} pattern with an
 * explicit Spring {@link RestClient} call against the configured
 * {@code referencedata.base-url} / {@code usersgroups.base-url} services.
 *
 * <p>Each call sends:</p>
 * <ul>
 *   <li>{@code CJSCPPUID} = configured system user UUID,</li>
 *   <li>{@code Accept} = caller-supplied vendor media type,</li>
 *   <li>query parameters as supplied.</li>
 * </ul>
 *
 * <p>RestClient is used (not RestTemplate) because Spring's
 * {@code HttpEntityRequestCallback} adds {@code Content-Length: 0} to every
 * body-less {@code RestTemplate.exchange(...)} call, and the JDK
 * {@code java.net.http.HttpClient} (Spring 7's default request factory when
 * neither Apache HttpComponents nor Jetty is on the classpath) treats
 * {@code Content-Length} as a restricted header and throws.</p>
 */
@Component
public class CommonPlatformQueryClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonPlatformQueryClient.class);

    private final RestClient restClient;
    private final CourtSchedulerSystemUserConfig systemUserConfig;
    private final String referenceDataBaseUrl;
    private final String usersGroupsBaseUrl;

    public CommonPlatformQueryClient(final CourtSchedulerSystemUserConfig systemUserConfig,
                                     @Value("${referencedata.base-url:}") final String referenceDataBaseUrl,
                                     @Value("${usersgroups.base-url:}") final String usersGroupsBaseUrl,
                                     @Value("${courtscheduler.http.connect-timeout-seconds:5}") final int connectTimeoutSeconds,
                                     @Value("${courtscheduler.http.read-timeout-seconds:30}") final int readTimeoutSeconds) {
        this.systemUserConfig = systemUserConfig;
        this.referenceDataBaseUrl = referenceDataBaseUrl;
        this.usersGroupsBaseUrl = usersGroupsBaseUrl;

        // Without these timeouts a hung refdata peer would wedge captureRotaFilesAndProcessEach
        // indefinitely — the @Async pipeline keeps that thread alive and the next scheduled
        // run picks up no work because the previous one is still "in flight".
        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                .build();
        final JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
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

        LOGGER.debug("Calling {} with Accept={} ", uri, acceptMediaType);
        final String body = restClient.get()
                .uri(uri)
                .accept(MediaType.parseMediaType(acceptMediaType))
                .header("CJSCPPUID", systemUserConfig.getRequiredSystemUserId())
                .retrieve()
                .body(String.class);
        return parse(body);
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
