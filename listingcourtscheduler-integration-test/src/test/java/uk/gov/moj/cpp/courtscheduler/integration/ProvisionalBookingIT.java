package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

public class ProvisionalBookingIT extends AbstractIT {

    private final String RELATIVE_PATH = "/provisionalBooking";

    @Test
    void shouldCreateProvisionalHearingSlot() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);


        String provisionalBookingPayload = getPayload("courtscheduler.create.provisional.booking.json");
        provisionalBookingPayload = provisionalBookingPayload.replace("COURTSCHEDULER_ID", courtScheduleId);

        final Response response = postCommand(RELATIVE_PATH, "application/vnd.courtscheduler.create.provisional.booking+json", SYSTEM_USER_ID, provisionalBookingPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        String responseString = response.readEntity(String.class); // Ensure to read the entity as String
        JSONObject responseJson = new JSONObject(responseString);
        assertThat(responseJson.get("bookingId"), notNullValue());
    }

    /**
     * Backward-compatibility guard: a legacy WildFly client posted to this endpoint with
     * {@code Accept: application/json} (the legacy default response type — the RAML declared no
     * response media type for provisional booking). The migrated OpenAPI declares
     * {@code produces: application/vnd.courtscheduler.create.provisional.booking.response+json},
     * so with strict content negotiation a specific {@code Accept: application/json} must NOT be
     * rejected with 406. (The happy-path test above doesn't catch this — it sends no Accept, so
     * RestTemplate uses {@code *}/{@code *}, which matches any produces.)
     */
    @Test
    void shouldAcceptLegacyApplicationJsonAcceptHeader() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        String provisionalBookingPayload = getPayload("courtscheduler.create.provisional.booking.json")
                .replace("COURTSCHEDULER_ID", courtScheduleId);

        final Response response = postCommandWithAccept(
                RELATIVE_PATH,
                "application/vnd.courtscheduler.create.provisional.booking+json",
                "application/json",
                SYSTEM_USER_ID,
                provisionalBookingPayload);

        assertThat("legacy Accept: application/json must not be rejected with 406 Not Acceptable",
                response.getStatus(), is(not(NOT_ACCEPTABLE.getStatusCode())));
        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    /**
     * Mirrors the real production caller — cpp-context-hearing's
     * {@code ProvisionalBookingService.bookSlots} (Apache HttpClient) sends
     * {@code Content-Type: …create.provisional.booking+json}, {@code CJSCPPUID}, and
     * NO {@code Accept} header. This is the traffic that actually hits the endpoint, so it
     * must return 200 (an absent Accept is treated as {@code *}/{@code *}).
     */
    @Test
    void shouldAcceptHearingCallerWithNoAcceptHeader() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        String provisionalBookingPayload = getPayload("courtscheduler.create.provisional.booking.json")
                .replace("COURTSCHEDULER_ID", courtScheduleId);

        final Response response = postCommandWithoutAccept(
                RELATIVE_PATH,
                "application/vnd.courtscheduler.create.provisional.booking+json",
                SYSTEM_USER_ID,
                provisionalBookingPayload);

        assertThat("real hearing caller sends no Accept header and must not be rejected",
                response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldRetrieveProvisionalBooking() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();


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

        final ProvisionalSlot provisionalSlot = new ProvisionalSlot(courtSchedule.getCourtScheduleId(), "2020-01-01T11:00:00.000Z");
        databaseSeeder.bookSlots(List.of(provisionalSlot), bookingId);

        String provisionalBooking = getPayload("courtscheduler.get.provisional.booking.json");
        provisionalBooking = provisionalBooking.replace("BOOKING_ID", bookingId);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(provisionalBooking, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_PATH, "application/vnd.courtscheduler.get.provisional.booking+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
    }

}
