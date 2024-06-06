package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


class HearingSlotIT extends AbstractIT {

    private static final String RELATIVE_URL = "/hearingslots";

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
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("COURT_SCHEDULE_ID", courtScheduleId);

        final Response response = postCommand(RELATIVE_URL, "application/vnd.courtscheduler.update.hearing.slots+json", USER_ID, updateHearingSlotsPayload);

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
        Map<String, Object> map = mapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
    }

    @Test
    void shouldRemoveHearingSlot() throws Exception {
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

        final Response response = deleteCommand(format("%s/%s", RELATIVE_URL, hearingId), "application/vnd.courtscheduler.remove.hearing.slots+json", USER_ID);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }
}
