package uk.gov.moj.cpp.courtscheduler.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import javax.json.JsonObject;
import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;


class CourtSchedulerIT extends AbstractIT {

    private static final String RELATIVE_URL = "/courtschedule";

    @Test
    void shouldCreateCourtSchedule() {
        final String createCourtSchedulePayload = getPayload("create-court-schedule.json");

        final Response response = postCommand(RELATIVE_URL, "application/vnd.courtscheduler.create+json", USER_ID, createCourtSchedulePayload);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldGetCourtSchedules() throws SQLException, JsonProcessingException {
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        LocalDate fromDate = expected.getSessionDate().minusDays(1);
        LocalDate toDate = expected.getSessionDate().plusDays(1);

        expected.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(expected);

        String getCourtScheduleRequestParams = getPayload("courtscheduler.get.court_schedule_query.json");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_CENTRE_ID", expected.getCourtHouseId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("COURT_ROOM_ID", expected.getCourtRoomId());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("BUSINESS_TYPE", expected.getBusinessType());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("SESSION_END_DATE", toDate.toString());
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_SIZE", "1");
        getCourtScheduleRequestParams = getCourtScheduleRequestParams.replace("PAGE_NUMBER", "10");

        Map<String, Object> map = mapper.readValue(getCourtScheduleRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get+json", USER_ID, map);


        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

    }
}
