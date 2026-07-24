package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.UUID.fromString;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupUserAsSystemUser;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetCpCourtRooms;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceCourtRooms;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataCourtRoomSessionAllocations;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciaries;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciarySpecialisms;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.benas.randombeans.EnhancedRandomBuilder;
import io.github.benas.randombeans.api.EnhancedRandom;
import io.github.benas.randombeans.api.Randomizer;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseReader;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder;
import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;

/**
 * Re-platformed in place: was {@code extends RestClient} (Justice Services framework)
 * targeting {@code localhost:8080}; now uses Spring {@link RestTemplate} against the
 * {@code app.baseUrl} system property set by the Gradle {@code integration} task.
 *
 * <p>The legacy {@code postCommand}/{@code putCommand}/{@code deleteCommand} method
 * surface is preserved — they still return {@code jakarta.ws.rs.core.Response}, built
 * via {@link #toLegacyResponse(ResponseEntity)} from the underlying Spring response —
 * so the IT classes compile without rewrites of every {@code response.getStatus()} or
 * {@code response.readEntity(...)} call site.</p>
 */
public abstract class AbstractIT {

    /** Base URL of the dockerised Spring Boot app, set by the Gradle {@code integration} task. */
    protected static final String APP_BASE_URL = System.getProperty(
            "app.baseUrl",
            "http://localhost:8083/listingcourtscheduler-api/rest/courtscheduler");

    /** Kept for source-compatibility with the legacy test classes. */
    protected final String BASE_URL = APP_BASE_URL;

    protected static final UUID USER_ID = fromString("11111111-1111-1111-1111-111111111111");
    protected static final UUID SYSTEM_USER_ID = fromString("22222222-2222-2222-2222-222222222222");

    protected static final Random random = new Random();
    protected static final EnhancedRandom RANDOM = new EnhancedRandomBuilder()
            .maxStringLength(5)
            .randomize(int.class, (Randomizer<Integer>) () -> random.nextInt(500))
            .randomize(Integer.class, (Randomizer<Integer>) () -> random.nextInt(500))
            .randomize(long.class, (Randomizer<Long>) () -> (long) random.nextInt(500))
            .build();

    protected final DatabaseSeeder databaseSeeder = new DatabaseSeeder();
    protected final DatabaseReader databaseReader = new DatabaseReader();
    protected final ObjectMapper mapper = new ObjectMapper();
    protected final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    private static final RestTemplate REST = newRestTemplate();

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @BeforeAll
    public static void setUp() {
        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        setupUserAsSystemUser(SYSTEM_USER_ID.toString());
        stubGetReferenceDataCourtRoomSessionAllocations("referencedata.rota-courtroom-sessionallocations.json");
        stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");
        stubGetReferenceDataJudiciarySpecialisms("referencedata.judiciary-specialisms.json");
        setupReferenceDataStubs();
    }

    @BeforeEach
    public void cleanTheDatabase() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        databaseSeeder.cleanDb();
    }

    protected static void setupReferenceDataStubs() {
        stubGetReferenceCourtRooms("referencedata.rota-courtrooms.json");
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types.json");
        stubGetCpCourtRooms("referencedata.get.ou-courtrooms.json");
    }

    private static RestTemplate newRestTemplate() {
        final RestTemplate template = new RestTemplate();
        // Surface 4xx/5xx as ResponseEntity so legacy assertions on status codes work.
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(final ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }

    // ---------- Legacy method surface ----------

    protected Response postCommand(final String path,
                                   final String contentType,
                                   final UUID userId,
                                   final String requestPayload) {
        return toLegacyResponse(exchange(HttpMethod.POST, path, contentType, userId, requestPayload));
    }

    protected Response deleteCommand(final String path,
                                     final String contentType,
                                     final UUID userId) {
        return toLegacyResponse(exchange(HttpMethod.DELETE, path, contentType, userId, null));
    }

    protected Response deleteCommand(final String path,
                                     final String contentType,
                                     final UUID userId,
                                     final String requestPayload) {
        return toLegacyResponse(exchange(HttpMethod.DELETE, path, contentType, userId, requestPayload));
    }

    protected Response putCommand(final String path,
                                  final String contentType,
                                  final UUID userId,
                                  final String requestPayload) {
        return toLegacyResponse(exchange(HttpMethod.PUT, path, contentType, userId, requestPayload));
    }

    /** Builds the {@code RequestParams}-like aggregator used by the legacy test code. */
    protected RequestParams getRequestParams(final String path,
                                             final String contentType,
                                             final UUID userId,
                                             final Map<String, Object> queryParams) {
        final String url = (queryParams == null || queryParams.isEmpty())
                ? BASE_URL + path
                : (BASE_URL + path + "?" + createUrlFromParam(queryParams));
        return new RequestParams(url, contentType, userId == null ? null : userId.toString());
    }

    /** GET via {@link RestTemplate}; returns the legacy {@code jakarta.ws.rs.core.Response}. */
    protected Response getCommand(final RequestParams params) {
        final HttpHeaders headers = new HttpHeaders();
        if (params.getMediaType() != null) {
            headers.set(HttpHeaders.ACCEPT, params.getMediaType());
        }
        if (params.getUserId() != null) {
            headers.set("CJSCPPUID", params.getUserId());
        }
        final ResponseEntity<String> response = REST.exchange(
                URI.create(params.getUrl()), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
        return toLegacyResponse(response);
    }

    private ResponseEntity<String> exchange(final HttpMethod method,
                                            final String path,
                                            final String contentType,
                                            final UUID userId,
                                            final String body) {
        final HttpHeaders headers = new HttpHeaders();
        if (contentType != null) {
            headers.setContentType(MediaType.parseMediaType(contentType));
        }
        if (userId != null) {
            headers.set("CJSCPPUID", userId.toString());
        }
        final HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return REST.exchange(URI.create(BASE_URL + path), method, entity, String.class);
    }

    /**
     * POST with an explicit {@code Accept} header. The default {@link #postCommand} helper sets no
     * Accept, so RestTemplate sends {@code *}/{@code *}, which matches any {@code produces} and so
     * hides response content-negotiation mismatches. This variant lets a test send the specific
     * Accept a legacy WildFly client would use (e.g. {@code application/json}) to verify the
     * migrated endpoint does not reject it with 406.
     */
    protected Response postCommandWithAccept(final String path,
                                             final String contentType,
                                             final String accept,
                                             final UUID userId,
                                             final String body) {
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        headers.setAccept(java.util.List.of(MediaType.parseMediaType(accept)));
        if (userId != null) {
            headers.set("CJSCPPUID", userId.toString());
        }
        final HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return toLegacyResponse(REST.exchange(URI.create(BASE_URL + path), HttpMethod.POST, entity, String.class));
    }

    /**
     * POST with NO {@code Accept} header at all — faithfully mirrors the real cpp-context-hearing
     * caller ({@code ProvisionalBookingService.bookSlots}, Apache HttpClient, which sets only
     * Content-Type + CJSCPPUID). RestTemplate would otherwise auto-add an Accept for a String
     * response, so an interceptor strips it after the request callback runs. The server treats an
     * absent Accept as {@code *}/{@code *}.
     */
    protected Response postCommandWithoutAccept(final String path,
                                                final String contentType,
                                                final UUID userId,
                                                final String body) {
        final org.springframework.web.client.RestTemplate noAccept = new org.springframework.web.client.RestTemplate();
        noAccept.setErrorHandler(new org.springframework.web.client.DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(final org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        noAccept.getInterceptors().add((request, payloadBytes, execution) -> {
            request.getHeaders().remove(org.springframework.http.HttpHeaders.ACCEPT);
            return execution.execute(request, payloadBytes);
        });
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));
        if (userId != null) {
            headers.set("CJSCPPUID", userId.toString());
        }
        final HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return toLegacyResponse(noAccept.exchange(URI.create(BASE_URL + path), HttpMethod.POST, entity, String.class));
    }

    /**
     * Wraps a Spring {@link ResponseEntity} as a JAX-RS {@link Response}, preserving status,
     * body, content-type and headers — so the existing legacy test assertions
     * ({@code response.getStatus()}, {@code response.readEntity(String.class)}, etc.) continue
     * to compile and behave the same way.
     *
     * <p>Returns a {@link LegacyResponse} so callers can use {@code readEntity(Class)}, which
     * the standard {@link Response.ResponseBuilder} build only supports on inbound (client)
     * responses — not on the outbound {@link org.glassfish.jersey.message.internal.OutboundJaxrsResponse}
     * a builder produces. This kept the legacy IT call-sites unchanged.</p>
     */
    public static Response toLegacyResponse(final ResponseEntity<String> response) {
        return new LegacyResponse(response);
    }

    /**
     * Hand-rolled {@link Response} that exposes the original Spring response's body via
     * {@link #readEntity(Class)} as well as {@link #getEntity()}, so legacy tests that use
     * either pattern keep working without rewrites.
     */
    public static final class LegacyResponse extends Response {
        private final ResponseEntity<String> spring;
        private final MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        private final MultivaluedMap<String, String> stringHeaders = new MultivaluedHashMap<>();

        public LegacyResponse(final ResponseEntity<String> spring) {
            this.spring = spring;
            spring.getHeaders().forEach((name, values) -> {
                for (final String v : values) {
                    headers.add(name, v);
                    stringHeaders.add(name, v);
                }
            });
        }

        @Override public int getStatus() { return spring.getStatusCode().value(); }
        @Override public StatusType getStatusInfo() { return Response.Status.fromStatusCode(getStatus()); }
        @Override public Object getEntity() { return spring.getBody(); }

        @SuppressWarnings("unchecked")
        @Override public <T> T readEntity(final Class<T> entityType) {
            if (entityType == String.class || entityType == Object.class) {
                return (T) spring.getBody();
            }
            if (entityType == byte[].class) {
                return (T) (spring.getBody() == null ? new byte[0] : spring.getBody().getBytes(StandardCharsets.UTF_8));
            }
            throw new UnsupportedOperationException("LegacyResponse.readEntity only supports String/byte[]; got " + entityType);
        }
        @Override public <T> T readEntity(final jakarta.ws.rs.core.GenericType<T> entityType) { return readEntity((Class<T>) entityType.getRawType()); }
        @Override public <T> T readEntity(final Class<T> entityType, final java.lang.annotation.Annotation[] annotations) { return readEntity(entityType); }
        @Override public <T> T readEntity(final jakarta.ws.rs.core.GenericType<T> entityType, final java.lang.annotation.Annotation[] annotations) { return readEntity((Class<T>) entityType.getRawType()); }

        @Override public boolean hasEntity() { return spring.getBody() != null; }
        @Override public boolean bufferEntity() { return true; }
        @Override public void close() { /* no-op */ }

        @Override public jakarta.ws.rs.core.MediaType getMediaType() {
            final MediaType ct = spring.getHeaders().getContentType();
            return ct == null ? null : jakarta.ws.rs.core.MediaType.valueOf(ct.toString());
        }
        @Override public java.util.Locale getLanguage() { return null; }
        @Override public int getLength() { return spring.getBody() == null ? -1 : spring.getBody().length(); }
        @Override public java.util.Set<String> getAllowedMethods() { return java.util.Collections.emptySet(); }
        @Override public java.util.Map<String, jakarta.ws.rs.core.NewCookie> getCookies() { return java.util.Collections.emptyMap(); }
        @Override public jakarta.ws.rs.core.EntityTag getEntityTag() { return null; }
        @Override public java.util.Date getDate() { return null; }
        @Override public java.util.Date getLastModified() { return null; }
        @Override public java.net.URI getLocation() { return null; }
        @Override public java.util.Set<jakarta.ws.rs.core.Link> getLinks() { return java.util.Collections.emptySet(); }
        @Override public boolean hasLink(final String relation) { return false; }
        @Override public jakarta.ws.rs.core.Link getLink(final String relation) { return null; }
        @Override public jakarta.ws.rs.core.Link.Builder getLinkBuilder(final String relation) { return null; }

        @Override public MultivaluedMap<String, Object> getMetadata() { return headers; }
        @Override public MultivaluedMap<String, String> getStringHeaders() { return stringHeaders; }
        @Override public String getHeaderString(final String name) {
            final java.util.List<String> values = stringHeaders.get(name);
            return values == null || values.isEmpty() ? null : String.join(",", values);
        }
    }

    protected String createUrlFromParam(final Map<String, Object> queryParam) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Object> e : queryParam.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(java.net.URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

}
