package uk.gov.moj.cpp.courtscheduler.integration;

import io.github.benas.randombeans.EnhancedRandomBuilder;
import io.github.benas.randombeans.api.EnhancedRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.http.HeaderConstants;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.UUID;

import static java.util.UUID.fromString;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;


class HearingSlotIT {

    private static final String URL = "http://" + getHost() + ":8080/courtscheduler-api/rest/courtscheduler/hearingslots";

    private static final UUID USER_ID = fromString("bb593957-08a8-4d41-a5c1-7674d38d4f43");

    private static final EnhancedRandom RANDOM = new EnhancedRandomBuilder()
            .maxStringLength(5)
            .build();

    private final RestClient restClient = new RestClient();
    private final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
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
