package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.request;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_OK;
import static uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils.stubPingFor;
import static uk.gov.justice.services.common.http.HeaderConstants.ID;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;
import uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

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
    private static final String QUERY_RELATIVE_URL_ROTA_COURTROOMSESSIONALLOCATIONS = "/referencedata-service/query/api/rest/referencedata/courtroom-session-allocations";
    private static final String ROTA_COURTROOMSESSIONALLOCATIONS_QUERY_MEDIA_TYPE = "application/vnd.referencedata.query.courtroom-session-allocations+json";
    private static final String QUERY_RELATIVE_URL_ROTA_JUDICIARIES = "/referencedata-service/query/api/rest/referencedata/judiciaries";
    private static final String ROTA_JUDICIARIES_QUERY_MEDIA_TYPE = "application/vnd.reference-data.judiciaries+json";

    public static void setupLoggedInUsersPermissionQueryStub(final String userId) {
        reset();
        stubPingFor("usersgroups-service");

        stubFor(get(urlPathEqualTo("/usersgroups-service/query/api/rest/usersgroups/users/logged-in-user/permissions"))
                .willReturn(aResponse().withStatus(OK.getStatusCode())
                        .withHeader("ID", userId)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPayload("usersgroups.user-permissions.json"))));
    }

    public static void setupUserAsSystemUser(String userId) {
        InternalEndpointMockUtils.stubPingFor("usersgroups-service");
        stubFor(get(urlPathEqualTo("/usersgroups-service/query/api/rest/usersgroups/users/" + userId + "/groups"))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader(ID, randomUUID().toString())
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPayload("stub-data/usersgroups.get-groups-by-user.json"))));
    }

    public static void stubGetReferenceDataRotaBusinessTypes(final String responsePath) {
        final String urlPath = QUERY_RELATIVE_URL_BUSINESS_TYPE;
        final String fullPayload = getPayload(responsePath);
        
        // Stub for requests with typeCode parameter
        stubFor(get(urlPathEqualTo(urlPath))
                .withQueryParam("typeCode",equalTo("TRL"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", ROTA_BUSINESS_TYPES_QUERY_MEDIA_TYPE)
                                .withBody("{\n" +
                                        "          \"rotaBusinessTypes\": [\n" +
                                        "            {\n" +
                                        "              \"id\": \"c9bb572b-2769-4da6-a41b-c8d7f15fc4a8\",\n" +
                                        "              \"seqNum\": 10,\n" +
                                        "              \"typeCode\": \"TRL\",\n" +
                                        "              \"typeDescription\": \"TRL\",\n" +
                                        "              \"slot\": false,\n" +
                                        "              \"duration\": true,\n" +
                                        "              \"validFrom\": \"2019-01-01\",\n" +
                                        "              \"validTo\": \"2019-12-31\"\n" +
                                        "            }\n" +
                                        "          ]\n" +
                                        "        }")));

        // Stub for requests without typeCode parameter
        stubFor(get(urlPathEqualTo(urlPath))
                .atPriority(2)
                .willReturn(aResponse()
                        .withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", ROTA_BUSINESS_TYPES_QUERY_MEDIA_TYPE)
                        .withBody(fullPayload)));
    }

    public static void stubGetReferenceCourtRooms(final String responsePath) {
        final String urlPath = QUERY_RELATIVE_URL_ROTA_COURTROOMS;
        stubFor(get(urlPathEqualTo(urlPath))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", ROTA_COURTROOMS_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
    }

    public static void stubGetReferenceDataCourtRoomSessionAllocations(final String responsePath) {
        final String urlPath = QUERY_RELATIVE_URL_ROTA_COURTROOMSESSIONALLOCATIONS;
        stubFor(get(urlPathEqualTo(urlPath))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", ROTA_COURTROOMSESSIONALLOCATIONS_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
    }

    public static void stubGetReferenceDataJudiciaries(final String responsePath) {
        final String urlPath = QUERY_RELATIVE_URL_ROTA_JUDICIARIES;
        stubFor(get(urlPathEqualTo(urlPath))
                .willReturn(aResponse().withStatus(SC_OK)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader("Content-Type", ROTA_JUDICIARIES_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
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
