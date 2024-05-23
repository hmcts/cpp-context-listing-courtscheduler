package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.fromString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.collections.MapUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;

import uk.gov.justice.services.common.http.HeaderConstants;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.benas.randombeans.EnhancedRandomBuilder;
import io.github.benas.randombeans.api.EnhancedRandom;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


class HearingSlotIT {

    private static final String URL = "http://" + getHost() + ":8080/courtscheduler-api/rest/courtscheduler/hearingslots";
    private static final UUID USER_ID = fromString("bb593957-08a8-4d41-a5c1-7674d38d4f43");
    private static final EnhancedRandom RANDOM = new EnhancedRandomBuilder()
            .maxStringLength(5)
            .build();
    protected static final RestClient REST_CLIENT = new RestClient();
    private final DatabaseSeeder databaseSeeder = new DatabaseSeeder();
    @BeforeAll
    public static void setUp() {
        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
    }
    @BeforeEach
    public void cleanTheDatabase() throws Exception {
        databaseSeeder.cleanDb();
    }

    @Test
    void shouldUpdateHearingSlot() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
        String hearingId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        ProvisionalBooking provisionalBooking = RANDOM.nextObject(ProvisionalBooking.class);
        provisionalBooking.setProvisionalBookingKey(new ProvisionalBookingKey(courtSchedule, bookingId));
        databaseSeeder.insertProvisionalBooking(provisionalBooking);

        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        String updateHearingSlotsPayload = getPayload("courtscheduler.update.hearing.slots.json");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_ID", hearingId);

        final Response response = postCommand(URL, "application/vnd.courtscheduler.update.hearing.slots+json", updateHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldRetrieveHearingSlot() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
        String hearingId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        final CourtScheduleJudiciaryKey courtScheduleJudiciaryKey = random(CourtScheduleJudiciaryKey.class);
        courtScheduleJudiciaryKey.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        courtScheduleJudiciaryKey.setJudiciaryId(courtSchedule.getCourtScheduleId());
        courtScheduleJudiciary.setId(courtScheduleJudiciaryKey);
        courtScheduleJudiciary.setCourtListingProfileId(courtScheduleJudiciary.getCourtListingProfileId());
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams
                (StringUtils.EMPTY, "application/vnd.courtscheduler.get.hearing.slots+json", USER_ID.toString(), map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
    }

    public Response postCommand(final String url, final String contentType, final String requestPayload) {
        final RequestParams requestParams = requestParams(url, contentType)
                .withHeader(HeaderConstants.USER_ID, USER_ID)
                .build();

        return REST_CLIENT.postCommand(requestParams.getUrl(), requestParams.getMediaType(), requestPayload, requestParams.getHeaders());
    }

    private static RequestParams getRequestParams(final String path, final String contentType, final String userId, final Map<String, Object> queryParams) {
        final String url = (isEmpty(queryParams)) ? URL + path : (URL + path + "?" + createUrlFromParam(queryParams));
        RequestParamsBuilder requestParamsBuilder = RequestParamsBuilder.requestParams(url, contentType);
        if (isNoneBlank(userId)) {
            requestParamsBuilder = requestParamsBuilder.withHeader(HeaderConstants.USER_ID, userId);
        }
        return requestParamsBuilder.build();
    }

    private static String createUrlFromParam(final Map<String, Object> queryParam) {
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
