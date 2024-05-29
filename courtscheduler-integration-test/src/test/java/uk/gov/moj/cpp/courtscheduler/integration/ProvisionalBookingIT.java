package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.UUID.fromString;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder.requestParams;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;

import uk.gov.justice.services.common.http.HeaderConstants;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.sql.SQLException;
import java.util.UUID;

import javax.ws.rs.core.Response;

import io.github.benas.randombeans.EnhancedRandomBuilder;
import io.github.benas.randombeans.api.EnhancedRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProvisionalBookingIT {

    private static final String URL = "http://" + getHost() + ":8080/courtscheduler-api/rest/courtscheduler/provisionalBooking";
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
    void shouldCreateProvisionalHearingSlot() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        String provisionalBookingPayload = getPayload("courtscheduler.create.provisional.booking.json");
        provisionalBookingPayload = provisionalBookingPayload.replace("COURTSCHEDULER_ID", courtScheduleId);

        final Response response = postCommand(URL, "application/vnd.courtscheduler.create.provisional.booking+json", provisionalBookingPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    public Response postCommand(final String url, final String contentType, final String requestPayload) {
        final RequestParams requestParams = requestParams(url, contentType)
                .withHeader(HeaderConstants.USER_ID, USER_ID)
                .build();

        return REST_CLIENT.postCommand(requestParams.getUrl(), requestParams.getMediaType(), requestPayload, requestParams.getHeaders());
    }
}
