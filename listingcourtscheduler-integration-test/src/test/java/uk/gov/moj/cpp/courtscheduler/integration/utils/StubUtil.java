package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import org.apache.http.HttpHeaders;

/**
 * Re-platformed in place: was a static-method WireMock helper using
 * {@code uk.gov.justice.service.wiremock.testutil.InternalEndpointMockUtils} and the
 * embedded WireMock from the Justice Services framework. Now talks to the dockerised
 * WireMock at {@code wiremock.baseUrl} ({@code http://localhost:8189} by default).
 *
 * <p>All public method names + signatures preserved so the legacy IT classes don't need
 * to be rewritten. URL paths use the canonical {@code /referencedata-query-api/...}
 * routing matching {@link uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService}.</p>
 */
public class StubUtil {

    private static final String WIREMOCK_BASE_URL =
            System.getProperty("wiremock.baseUrl", "http://localhost:8189");

    static {
        // WireMock.findAll / WireMock.verify (static methods) use a global "defaultInstance"
        // that defaults to localhost:8080.  Align it with our configured server so that
        // request-count queries in tests hit the right WireMock process.
        WireMock.configureFor(extractHost(WIREMOCK_BASE_URL), extractPort(WIREMOCK_BASE_URL));
    }

    private static final WireMock CLIENT = WireMock.create()
            .scheme(WIREMOCK_BASE_URL.startsWith("https") ? "https" : "http")
            .host(extractHost(WIREMOCK_BASE_URL))
            .port(extractPort(WIREMOCK_BASE_URL))
            .build();

    private static final String REFERENCEDATA_BASE = "/referencedata-query-api/query/api/rest/referencedata";
    private static final String USERSGROUPS_BASE   = "/usersgroups-query-api/query/api/rest/usersgroups";

    private static final String USERS_PERMISSIONS_PATH = USERSGROUPS_BASE + "/users/logged-in-user/permissions";
    private static final String USERS_GROUPS_PATH      = USERSGROUPS_BASE + "/users";

    private static final String QUERY_RELATIVE_URL_BUSINESS_TYPE        = REFERENCEDATA_BASE + "/rota-business-types";
    private static final String QUERY_RELATIVE_URL_ROTA_COURTROOMS      = REFERENCEDATA_BASE + "/cp-rota-courtroom-mappings";
    private static final String QUERY_RELATIVE_URL_SESSION_ALLOCATIONS  = REFERENCEDATA_BASE + "/courtroom-session-allocations";
    private static final String QUERY_RELATIVE_URL_JUDICIARIES          = REFERENCEDATA_BASE + "/judiciaries";
    private static final String QUERY_RELATIVE_URL_JUDICIARY_SPECIALISMS = REFERENCEDATA_BASE + "/judiciary-specialisms";
    private static final String QUERY_RELATIVE_URL_CP_COURTROOMS        = REFERENCEDATA_BASE + "/courtrooms";

    private static final String ROTA_BUSINESS_TYPES_QUERY_MEDIA_TYPE        = "application/vnd.referencedata.query.rota-business-types+json";
    private static final String ROTA_COURTROOMS_QUERY_MEDIA_TYPE            = "application/vnd.referencedata.query.cp-rota-courtroom-mappings+json";
    private static final String SESSION_ALLOCATIONS_QUERY_MEDIA_TYPE        = "application/vnd.referencedata.query.courtroom-session-allocations+json";
    private static final String JUDICIARIES_QUERY_MEDIA_TYPE                = "application/vnd.reference-data.judiciaries+json";
    private static final String JUDICIARY_SPECIALISMS_QUERY_MEDIA_TYPE      = "application/vnd.referencedata.query.judiciary-specialisms+json";
    private static final String CP_COURTROOMS_QUERY_MEDIA_TYPE              = "application/vnd.referencedata.ou-courtrooms+json";
    private static final String USER_PERMISSIONS_MEDIA_TYPE                 = "application/vnd.usersgroups.get-logged-in-user-permissions+json";

    public static void setupLoggedInUsersPermissionQueryStub(final String userId) {
        CLIENT.resetMappings();

        // The auth filter (cp-auth-rules-filter:2.0.0) calls
        // GET /usersgroups-query-api/.../users/logged-in-user/permissions with the
        // request's CJSCPPUID — once for the test user, and again for SYSTEM_USER_ID
        // on calls that the legacy IT classes drive as the system user. Register
        // BOTH stubs so the second lookup doesn't 404.
        CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(USERS_PERMISSIONS_PATH))
                .withHeader("CJSCPPUID", WireMock.equalTo(userId))
                .atPriority(2)
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("ID", userId)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, USER_PERMISSIONS_MEDIA_TYPE)
                        .withBody(getPayload("usersgroups.user-permissions.json"))));
    }

    public static void setupUserAsSystemUser(final String userId) {
        // System user: SYSTEM_USERS group, plus COURT_SCHEDULE CREATE/READ permissions
        // — mirrors what the production usersgroups-query-api grants the system principal.
        // System-only rules in the .drl match on group membership; permissioned rules
        // (e.g. judiciary endpoints) match on hasPermission, so the system user needs
        // both paths for the legacy IT classes that drive every endpoint with SYSTEM_USER_ID.
        CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(USERS_PERMISSIONS_PATH))
                .withHeader("CJSCPPUID", WireMock.equalTo(userId))
                .atPriority(2)
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("ID", userId)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, USER_PERMISSIONS_MEDIA_TYPE)
                        .withBody("{\"groups\":[{\"groupId\":\"g-sys\",\"groupName\":\"SYSTEM_USERS\",\"prosecutingAuthority\":null}],"
                                + "\"switchableRoles\":[],"
                                + "\"permissions\":["
                                + "{\"permissionId\":\"p-sys-1\",\"object\":\"CourtSchedule\",\"action\":\"Create\",\"description\":\"\"},"
                                + "{\"permissionId\":\"p-sys-2\",\"object\":\"CourtSchedule\",\"action\":\"View\",\"description\":\"\"}"
                                + "]}")));
        // Legacy /users/{userId}/groups path also kept for any direct callers.
        CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(USERS_GROUPS_PATH + "/" + userId + "/groups"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("CPPID", randomUUID().toString())
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(getPayload("stub-data/usersgroups.get-groups-by-user.json"))));
    }

    public static StubMapping stubGetReferenceDataRotaBusinessTypes(final String responsePath) {
        return CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(QUERY_RELATIVE_URL_BUSINESS_TYPE))
                .withQueryParam("jurisdiction", WireMock.equalTo("ALL"))
                .atPriority(2)
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, ROTA_BUSINESS_TYPES_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
    }

    public static StubMapping stubGetReferenceCourtRooms(final String responsePath) {
        return CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(QUERY_RELATIVE_URL_ROTA_COURTROOMS))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, ROTA_COURTROOMS_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
    }

    public static StubMapping stubGetReferenceDataCourtRoomSessionAllocations(final String responsePath) {
        return CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(QUERY_RELATIVE_URL_SESSION_ALLOCATIONS))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, SESSION_ALLOCATIONS_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
    }

    public static StubMapping stubGetReferenceDataJudiciaries(final String responsePath) {
        return CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(QUERY_RELATIVE_URL_JUDICIARIES))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, JUDICIARIES_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
    }

    public static StubMapping stubGetReferenceDataJudiciarySpecialisms(final String responsePath) {
        return CLIENT.register(WireMock.get(WireMock.urlPathMatching(QUERY_RELATIVE_URL_JUDICIARY_SPECIALISMS + ".*"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, JUDICIARY_SPECIALISMS_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
    }

    public static StubMapping stubGetCpCourtRooms(final String responsePath) {
        return CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(QUERY_RELATIVE_URL_CP_COURTROOMS))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, CP_COURTROOMS_QUERY_MEDIA_TYPE)
                        .withBody(getPayload(responsePath))));
    }

    public static StubMapping stubGetUserDetails(final String userId, final String organisationId, final String fileName) {
        final String payload = getPayload(fileName)
                .replace("USER_ID", userId)
                .replace("ORGANISATION_ID", organisationId);
        return CLIENT.register(WireMock.get(WireMock.urlPathEqualTo(USERSGROUPS_BASE + "/users/logged-in-user"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/vnd.usersgroups.logged-in-user-details+json")
                        .withBody(payload)));
    }

    public static int countRequests(final RequestPatternBuilder pattern) {
        return WireMock.findAll(pattern).size();
    }

    private static final String LISTING_COMMAND_HEARINGS_PATH = "/listing-command-api/command/api/rest/listing/hearings";
    private static final String CHANGE_JUDICIARY_FOR_HEARINGS_MEDIA_TYPE =
            "application/vnd.listing.command.change-judiciary-for-hearings+json";

    /** Stubs the listing command API to accept change-judiciary-for-hearings commands with 202. */
    public static StubMapping stubChangeJudiciaryForHearingsCommand() {
        return CLIENT.register(WireMock.post(WireMock.urlPathEqualTo(LISTING_COMMAND_HEARINGS_PATH))
                .willReturn(WireMock.aResponse().withStatus(202)));
    }

    /** Counts the change-judiciary-for-hearings commands WireMock has received (across the whole run). */
    public static int countChangeJudiciaryForHearingsRequests() {
        return countRequests(WireMock.postRequestedFor(WireMock.urlPathEqualTo(LISTING_COMMAND_HEARINGS_PATH))
                .withHeader(HttpHeaders.CONTENT_TYPE, WireMock.equalTo(CHANGE_JUDICIARY_FOR_HEARINGS_MEDIA_TYPE)));
    }

    /** Counts the change-judiciary-for-hearings commands whose body contains the given text (e.g. a hearing ID). */
    public static int countChangeJudiciaryForHearingsRequestsContaining(final String bodySubstring) {
        return countRequests(WireMock.postRequestedFor(WireMock.urlPathEqualTo(LISTING_COMMAND_HEARINGS_PATH))
                .withRequestBody(WireMock.containing(bodySubstring)));
    }

    /**
     * Counts the change-judiciary-for-hearings commands that carry the given hearing ID in the
     * {@code hearings} array AND match the shape listing's JSON schema requires:
     * {@code judiciary[0].judicialRoleType} must be an object with a {@code judiciaryType} field
     * (a bare-string judicialRoleType is rejected by the listing command API), and
     * {@code judiciaryAssignmentSource} must be {@code "AUTO"}.
     */
    public static int countSchemaShapedChangeJudiciaryForHearingsRequestsFor(final String hearingId) {
        return countRequests(WireMock.postRequestedFor(WireMock.urlPathEqualTo(LISTING_COMMAND_HEARINGS_PATH))
                .withRequestBody(WireMock.matchingJsonPath(format("$.hearings[?(@ == '%s')]", hearingId)))
                .withRequestBody(WireMock.matchingJsonPath("$.judiciary[0].judicialRoleType.judiciaryType"))
                .withRequestBody(WireMock.matchingJsonPath("$[?(@.judiciaryAssignmentSource == 'AUTO')]")));
    }

    private static String extractHost(final String url) {
        final String stripped = url.replaceFirst("^[a-z]+://", "");
        final int colon = stripped.indexOf(':');
        final int slash = stripped.indexOf('/');
        final int end = (colon >= 0 && (slash < 0 || colon < slash))
                ? colon : (slash >= 0 ? slash : stripped.length());
        return stripped.substring(0, end);
    }

    private static int extractPort(final String url) {
        final String stripped = url.replaceFirst("^[a-z]+://", "");
        final int colon = stripped.indexOf(':');
        if (colon < 0) return url.startsWith("https") ? 443 : 80;
        final int slash = stripped.indexOf('/', colon);
        final String portStr = slash > 0 ? stripped.substring(colon + 1, slash) : stripped.substring(colon + 1);
        return Integer.parseInt(portStr);
    }
}
