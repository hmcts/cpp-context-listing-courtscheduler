package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

public class ProvisionalBookingIT extends AbstractIT {

    private final String RELATIVE_PATH = "/provisionalBooking";

    @Test
    void shouldCreateProvisionalHearingSlot() throws SQLException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        String provisionalBookingPayload = getPayload("courtscheduler.create.provisional.booking.json");
        provisionalBookingPayload = provisionalBookingPayload.replace("COURTSCHEDULER_ID", courtScheduleId);

        final Response response = postCommand(RELATIVE_PATH, "application/vnd.courtscheduler.create.provisional.booking+json", USER_ID, provisionalBookingPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
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

        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        String provisionalBooking = getPayload("courtscheduler.get.provisional.booking.json");
        provisionalBooking = provisionalBooking.replace("BOOKING_ID", bookingId);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> map = mapper.readValue(provisionalBooking, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_PATH, "application/vnd.courtscheduler.get.provisional.booking+json", USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
    }

}
