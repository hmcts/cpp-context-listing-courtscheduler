package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils.stubPingFor;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.WiremockTestHelper.waitForStubToBeReady;

import java.util.UUID;

public class StubUtil {
    private static final String HOST = getHost();
    private static final int PORT = 8080;

    private static final String CONTENT_TYPE_QUERY_PERMISSION = "application/vnd.usersgroups.get-logged-in-user-permissions+json";
    private static final String USER_DETAILS_URL = "/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user";
    private static final String USER_DETAILS_MEDIA_TYPE = "application/vnd.usersgroups.logged-in-user-details+json";

    private static final String REFERENCE_DATA_SERVICE_NAME = "referencedata-service";

    private static final String QUERY_RELATIVE_URL_BUSINESS_TYPE = "/referencedata-service/query/api/rest/referencedata/rota-business-types";

    private static final String ROTA_BUSINESS_TYPES_QUERY_MEDIA_TYPE = "application/vnd.referencedata.query.rota-business-types+json";
    private static final String QUERY_RELATIVE_URL_ROTA_COURTROOMS = "/referencedata-service/query/api/rest/referencedata/cp-rota-courtroom-mappings";
    private static final String ROTA_COURTROOMS_QUERY_MEDIA_TYPE = "application/vnd.referencedata.query.cp-rota-courtroom-mappings+json";

    public static void setupLoggedInUsersPermissionQueryStub(final String userId) {
        reset();
        stubPingFor("usersgroups-service");

        stubFor(get(urlPathEqualTo("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader("ID", userId)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPayload("usersgroups.user-permissions.json"))));

        waitForStubToBeReady("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions", CONTENT_TYPE_QUERY_PERMISSION);
    }

    public static void stubGetReferenceDataRotaBusinessTypes(final String responsePath) {
        final String urlPath = QUERY_RELATIVE_URL_BUSINESS_TYPE;
        stubFor(get(urlPathEqualTo(urlPath))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", ROTA_BUSINESS_TYPES_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));

        waitForStubToBeReady(urlPath, ROTA_BUSINESS_TYPES_QUERY_MEDIA_TYPE);
    }

    public static void stubGetReferenceCourtRooms(final String responsePath) {
        final String urlPath = QUERY_RELATIVE_URL_ROTA_COURTROOMS;
        stubFor(get(urlPathEqualTo(urlPath))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", ROTA_COURTROOMS_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));

        waitForStubToBeReady(urlPath, ROTA_COURTROOMS_QUERY_MEDIA_TYPE);
    }


    public static void stubGetUserDetails(final String userId, final String organisationId, final String fileName) {
        stubPingFor("usersgroups-service");

        final String payload = getPayload(fileName)
                .replace("USER_ID", userId)
                .replace("ORGANISATION_ID", organisationId);

        stubPingFor("usersgroups-service");

        stubFor(get(urlPathEqualTo(USER_DETAILS_URL))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader("CPPID", UUID.randomUUID().toString())
                        .withHeader(CONTENT_TYPE, USER_DETAILS_MEDIA_TYPE)
                        .withBody(payload)));

    }
}
