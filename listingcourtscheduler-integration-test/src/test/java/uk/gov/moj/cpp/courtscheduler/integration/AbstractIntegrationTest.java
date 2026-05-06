package uk.gov.moj.cpp.courtscheduler.integration;

import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * Shared helpers for HTTP integration tests against the dockerised Spring Boot app.
 *
 * <p>System properties supplied by the Gradle {@code integration} task:</p>
 * <ul>
 *   <li>{@code app.baseUrl} – {@code http://localhost:${serverPort}/listingcourtscheduler-api/rest/courtscheduler}</li>
 *   <li>{@code wiremock.baseUrl} – {@code http://localhost:8189}</li>
 * </ul>
 */
public abstract class AbstractIntegrationTest {

    public static final String BASE_URL = System.getProperty(
            "app.baseUrl",
            "http://localhost:8083/listingcourtscheduler-api/rest/courtscheduler");

    public static final String WIREMOCK_BASE_URL =
            System.getProperty("wiremock.baseUrl", "http://localhost:8189");

    /** User with COURT_SCHEDULE permissions stubbed in {@code wiremock/mappings/identity-court-schedule-user.json}. */
    public static final UUID COURT_SCHEDULE_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** User in the {@code SYSTEM_USERS} group stubbed in {@code wiremock/mappings/identity-system-user.json}. */
    public static final UUID SYSTEM_USER_ID         = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** User with no groups and no permissions; should be rejected with 403. */
    public static final UUID DENIED_USER_ID         = UUID.fromString("33333333-3333-3333-3333-333333333333");

    protected final RestTemplate rest = newRestTemplate();

    private static RestTemplate newRestTemplate() {
        final RestTemplate template = new RestTemplate();
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(final ClientHttpResponse response) {
                // Surface 4xx/5xx responses as ResponseEntity rather than throwing,
                // so tests can assert on negative cases.
                return false;
            }
        });
        return template;
    }

    protected ResponseEntity<String> get(final String path, final UUID userId, final String acceptMediaType) {
        final HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, acceptMediaType);
        if (userId != null) {
            headers.set("CJSCPPUID", userId.toString());
        }
        return rest.exchange(URI.create(BASE_URL + path), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    protected ResponseEntity<String> post(final String path, final UUID userId, final String contentType, final String body) {
        return body(path, HttpMethod.POST, userId, contentType, body);
    }

    protected ResponseEntity<String> put(final String path, final UUID userId, final String contentType, final String body) {
        return body(path, HttpMethod.PUT, userId, contentType, body);
    }

    protected ResponseEntity<String> delete(final String path, final UUID userId, final String contentType) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        if (userId != null) {
            headers.set("CJSCPPUID", userId.toString());
        }
        return rest.exchange(URI.create(BASE_URL + path), HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> body(final String path, final HttpMethod method, final UUID userId,
                                        final String contentType, final String body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        if (userId != null) {
            headers.set("CJSCPPUID", userId.toString());
        }
        return rest.exchange(URI.create(BASE_URL + path), method,
                new HttpEntity<>(body, headers), String.class);
    }
}
