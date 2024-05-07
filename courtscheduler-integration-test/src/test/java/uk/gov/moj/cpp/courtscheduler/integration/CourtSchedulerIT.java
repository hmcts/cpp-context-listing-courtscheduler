package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.UUID.fromString;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.http.HeaderConstants;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.rest.RestClient;

import java.util.UUID;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;



class CourtSchedulerIT {

    private static final String URL = "http://" + getHost() + ":8080/courtscheduler-api/rest/courtscheduler/courtschedule";

    private static final UUID USER_ID = fromString("bb593957-08a8-4d41-a5c1-7674d38d4f43");

    private final RestClient restClient = new RestClient();
    private final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
    protected static final RestClient REST_CLIENT = new RestClient();

    @BeforeAll
    public static void setUp() {
        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
    }

    @Test
    void shouldCreateCourtSchedule() {
//        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        final String createCourtSchedulePayload = getPayload("create-court-schedule.json");

        final Response response = postCommand(URL, "application/vnd.courtscheduler.create+json", createCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }


    private MultivaluedHashMap<String, Object> headers() {
        final MultivaluedHashMap<String, Object> headers = new MultivaluedHashMap<>();
        headers.putSingle(HeaderConstants.USER_ID, USER_ID);
        return headers;
    }

    public Response postCommand(final String url, final String contentType, final String requestPayload) {
        final RequestParams requestParams = requestParams(url, contentType)
                .withHeader(HeaderConstants.USER_ID, USER_ID)
                .build();

        return REST_CLIENT.postCommand(requestParams.getUrl(), requestParams.getMediaType(), requestPayload, requestParams.getHeaders());
    }
}
