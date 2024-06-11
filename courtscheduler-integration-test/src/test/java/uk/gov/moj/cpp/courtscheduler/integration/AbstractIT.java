package uk.gov.moj.cpp.courtscheduler.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.benas.randombeans.EnhancedRandomBuilder;
import io.github.benas.randombeans.api.EnhancedRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.http.HeaderConstants;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder;
import uk.gov.moj.cpp.courtscheduler.integration.utils.RestClientExtender;

import javax.ws.rs.core.Response;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static java.util.UUID.fromString;
import static org.apache.commons.collections.MapUtils.isEmpty;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;

public abstract class AbstractIT {
    protected final String BASE_URL = "http://" + getHost() + ":8080/courtscheduler-api/rest/courtscheduler";
    protected static final UUID USER_ID = fromString("bb593957-08a8-4d41-a5c1-7674d38d4f43");
    protected static final EnhancedRandom RANDOM = new EnhancedRandomBuilder()
            .maxStringLength(5)
            .build();
    protected static final RestClientExtender REST_CLIENT = new RestClientExtender();
    protected final DatabaseSeeder databaseSeeder = new DatabaseSeeder();

    @BeforeAll
    public static void setUp() {
        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
    }

    @BeforeEach
    public void cleanTheDatabase() throws Exception {
        databaseSeeder.cleanDb();
    }

    protected ObjectMapper mapper = new ObjectMapper();

    protected final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    protected final JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(mapper);

    protected Response postCommand(final String path, final String contentType, final UUID userId, final String requestPayload) {

        final RequestParams requestParams = requestParams(BASE_URL + path, contentType)
                .withHeader(HeaderConstants.USER_ID, userId)
                .build();

        return REST_CLIENT.postCommand(requestParams.getUrl(), requestParams.getMediaType(), requestPayload, requestParams.getHeaders());
    }

    protected Response deleteCommand(final String path, final String contentType, final UUID userId) {

        final RequestParams requestParams = requestParams(BASE_URL + path, contentType)
                .withHeader(HeaderConstants.USER_ID, userId)
                .build();

        return REST_CLIENT.deleteCommand(requestParams.getUrl(), requestParams.getMediaType(), requestParams.getHeaders());
    }

    protected Response putCommand(final String path, final String contentType, final UUID userId, final String requestPayload) {

        final RequestParams requestParams = requestParams(BASE_URL + path, contentType)
                .withHeader(HeaderConstants.USER_ID, userId)
                .build();

        return REST_CLIENT.putCommand(requestParams.getUrl(), requestParams.getMediaType(), requestPayload, requestParams.getHeaders());
    }

    protected RequestParams getRequestParams(final String path, final String contentType, final UUID userId, final Map<String, Object> queryParams) {
        final String url = (isEmpty(queryParams)) ? BASE_URL + path : (BASE_URL + path + "?" + createUrlFromParam(queryParams));
        RequestParamsBuilder requestParamsBuilder = RequestParamsBuilder.requestParams(url, contentType);
        requestParamsBuilder = requestParamsBuilder.withHeader(HeaderConstants.USER_ID, userId);
        return requestParamsBuilder.build();
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
