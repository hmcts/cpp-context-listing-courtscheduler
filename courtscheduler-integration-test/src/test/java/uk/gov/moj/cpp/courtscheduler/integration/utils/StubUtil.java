package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils.stubPingFor;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.WiremockTestHelper.waitForStubToBeReady;

import java.util.UUID;

public class StubUtil {

    private static final String CONTENT_TYPE_QUERY_PERMISSION = "application/vnd.usersgroups.get-logged-in-user-permissions+json";
    private static final String USER_DETAILS_URL = "/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user";
    private static final String USER_DETAILS_MEDIA_TYPE = "application/vnd.usersgroups.logged-in-user-details+json";

    public static void setupLoggedInUsersPermissionQueryStub(final String userId) {
        stubPingFor("usersgroups-service");

        stubFor(get(urlPathEqualTo("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader("ID", userId)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPayload("usersgroups.user-permissions.json"))));

        waitForStubToBeReady("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions", CONTENT_TYPE_QUERY_PERMISSION);
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
