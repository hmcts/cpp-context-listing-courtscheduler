package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.UUID.fromString;
import static org.apache.commons.collections.MapUtils.isEmpty;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.*;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.http.HeaderConstants;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.justice.services.test.utils.core.rest.ResteasyClientBuilderFactory;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseReader;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.benas.randombeans.EnhancedRandomBuilder;
import io.github.benas.randombeans.api.EnhancedRandom;
import io.github.benas.randombeans.api.Randomizer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestDurationExtension.class)
public abstract class AbstractIT extends RestClient {
    protected final String BASE_URL = "http://" + getHost() + ":8080/listingcourtscheduler-api/rest/courtscheduler";
    protected static final UUID USER_ID = fromString("bb593957-08a8-4d41-a5c1-7674d38d4f43");
    protected static final UUID SYSTEM_USER_ID = fromString("8e035a94-437d-4f7f-af63-150ccb549bde");
    protected static final Random random = new Random();
    protected static final EnhancedRandom RANDOM = new EnhancedRandomBuilder()
            .maxStringLength(5)
            .randomize(int.class, (Randomizer<Integer>) () -> random.nextInt(500))
            .randomize(Integer.class, (Randomizer<Integer>) () -> random.nextInt(500))
            .randomize(long.class, (Randomizer<Long>) () -> (long) random.nextInt(500))
            .build();
    protected final DatabaseSeeder databaseSeeder = new DatabaseSeeder();
    protected final DatabaseReader databaseReader = new DatabaseReader();

    // Set timezone to UTC as early as possible
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @BeforeAll
    public static void setUp() {
        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        setupUserAsSystemUser(SYSTEM_USER_ID.toString());
        stubGetReferenceDataCourtRoomSessionAllocations("referencedata.rota-courtroom-sessionallocations.json");
        stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");
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

    protected ObjectMapper mapper = new ObjectMapper();
    protected final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    protected Response postCommand(final String path, final String contentType, final UUID userId, final String requestPayload) {

        final RequestParams requestParams = requestParams(BASE_URL + path, contentType)
                .withHeader(HeaderConstants.USER_ID, userId)
                .build();

        return super.postCommand(requestParams.getUrl(), requestParams.getMediaType(), requestPayload, requestParams.getHeaders());
    }

    protected Response deleteCommand(final String path, final String contentType, final UUID userId) {
        final RequestParams requestParams = requestParams(BASE_URL + path, contentType)
                .withHeader(HeaderConstants.USER_ID, userId)
                .build();

        return super.deleteCommand(requestParams.getUrl(), requestParams.getMediaType(), requestParams.getHeaders());
    }

    protected RequestParams getRequestParams(final String path, final String contentType, final UUID userId, final Map<String, Object> queryParams) {
        final String url = (isEmpty(queryParams)) ? BASE_URL + path : (BASE_URL + path + "?" + createUrlFromParam(queryParams));
        RequestParamsBuilder requestParamsBuilder = RequestParamsBuilder.requestParams(url, contentType);
        requestParamsBuilder = requestParamsBuilder.withHeader(HeaderConstants.USER_ID, userId);
        return requestParamsBuilder.build();
    }

    protected Response putCommand(final String path, final String contentType, final UUID userId, final String requestPayload) {

        final RequestParams requestParams = requestParams(BASE_URL + path, contentType)
                .withHeader(HeaderConstants.USER_ID, userId)
                .build();

        Entity<String> entity = Entity.entity(requestPayload, MediaType.valueOf(requestParams.getMediaType()));
        return ResteasyClientBuilderFactory.clientBuilder().build().target(requestParams.getUrl()).request().headers(requestParams.getHeaders()).put(entity);
    }

    public Response putCommand(final String url, final String contentType, final String requestPayload, final MultivaluedMap<String, Object> headers) {
        Entity<String> entity = Entity.entity(requestPayload, MediaType.valueOf(contentType));
        Response response = ResteasyClientBuilderFactory.clientBuilder().build().target(url).request().headers(headers).put(entity);
        return response;
    }

    protected String createUrlFromParam(final Map<String, Object> queryParam) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Object> e : queryParam.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}