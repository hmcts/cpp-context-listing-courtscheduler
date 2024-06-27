package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;


import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;


class CourtSchedulerIT extends AbstractIT {

    private static final String RELATIVE_URL = "/courtschedule";

    @Test
    void shouldCreateCourtSchedule() {
      //  stubGetReferenceDataRotaBusinessTypeByTypeCode("referencedata.rota-business-types.json","Type1");
        final String createCourtSchedulePayload = getPayload("create-court-schedule.json");

        final Response response = postCommand(RELATIVE_URL, "application/vnd.courtscheduler.create+json", USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldCreateOrUpdateCourtSchedule() {
        final String createCourtSchedulePayload = getPayload("create-court-schedule-multiple-session.json");

        final Response response = postCommand(RELATIVE_URL, "application/vnd.courtscheduler.create+json", USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldUpdateCourtSchedule() throws SQLException {
        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId.toString());
        databaseSeeder.insertCourtSchedule(expected);

        String updateCourtSchedulePayload = getPayload("update-court-schedule.json");
        String changedCourtHouseId = UUID.randomUUID().toString();
        String changedCourtRoomId = UUID.randomUUID().toString();
        String changedBusinessType = RANDOM.nextObject(String.class);
        String changedSessionType = "AM";
        String changedSessionDate = RANDOM.nextObject(LocalDate.class).toString();
        String changedPanel = "YOUTH";
        Integer availableDuration = RANDOM.nextInt();
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_SCHEDULE_ID", expected.getCourtScheduleId());
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_HOUSE_ID", changedCourtHouseId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_HOUSE_ID", changedCourtHouseId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("COURT_ROOM_ID", changedCourtRoomId);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("BUSINESS_TYPE", changedBusinessType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_TYPE", changedSessionType);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("SESSION_DATE", changedSessionDate);
        updateCourtSchedulePayload = updateCourtSchedulePayload.replace("PANEL", changedPanel);

        final Response response = putCommand(RELATIVE_URL, "application/vnd.courtscheduler.update+json", USER_ID, updateCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldGetCourtSchedules() throws SQLException, JsonProcessingException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types.json");

        UUID courtScheduleId = UUID.randomUUID();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        LocalDate fromDate = expected.getSessionDate().minusDays(1);
        LocalDate toDate = expected.getSessionDate().plusDays(1);

        expected.setCourtScheduleId(courtScheduleId.toString());
        databaseSeeder.insertCourtSchedule(expected);

        String getCourtScheduleRequestParams = getPayload("courtscheduler.get.court_schedule_query.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_CENTRE_ID", expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_ROOM_ID", expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("BUSINESS_TYPE", expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_END_DATE", toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_SIZE", "10");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_NUMBER", "1");

        Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get+json", USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonArray("courtSchedules").getJsonObject(0).getJsonArray("sessions").getJsonObject(0).getString("courtScheduleId"), is(expected.getCourtScheduleId()));
    }

    @Test
    void shouldRemoveCourtSchedule() throws Exception {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
        String deleteHearingSlotsPayload = getPayload("courtscheduler.delete-sessions.json");
        deleteHearingSlotsPayload = deleteHearingSlotsPayload.replace("COURT_SCHEDULE_ID", courtScheduleId);

        final Response response = patchCommand(RELATIVE_URL, "application/vnd.courtscheduler.delete+json", USER_ID, deleteHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }
}
