package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.utils.CourtScheduleIdGenerator;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import javax.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;


class MiExportIT extends AbstractIT {

    @Test
    void shouldExportCourtSchedules() throws SQLException, JsonProcessingException {
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(CourtScheduleIdGenerator.getCourtScheduleId(expected.getCourtRoomId(), expected.getSessionDate(), expected.getCourtSession(), expected.getBusinessType()));

        String exportMiDataRequestParams = getPayload("courtscheduler.export.mi_data_query.json");
        exportMiDataRequestParams = exportMiDataRequestParams.replace("FROM_DATE", fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace("TO_DATE", toDate.toString());

        Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/mi/court_schedules",
                "application/vnd.courtscheduler.export.court_schedule+json", USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonObject("courtSchedules")
                .getJsonArray("courtSchedules").get(0)
                .asJsonObject().getString("courtScheduleId"), is(expected.getCourtScheduleId()));
    }

    @Test
    void shouldExportCourtScheduleJudiciaries() throws Exception {
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId);
        CourtScheduleJudiciary courtScheduleJudiciary = RANDOM.nextObject(CourtScheduleJudiciary.class);
        CourtScheduleJudiciaryKey courtScheduleJudiciaryId = courtScheduleJudiciary.getId();
        courtScheduleJudiciaryId.setCourtScheduleId(courtScheduleId);

        String exportMiDataRequestParams = getPayload("courtscheduler.export.mi_data_query.json");
        exportMiDataRequestParams = exportMiDataRequestParams.replace("FROM_DATE", fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace("TO_DATE", toDate.toString());

        Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/mi/court_schedule_judiciaries",
                "application/vnd.courtscheduler.export.court_schedule_judiciary+json", USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonObject("courtScheduleJudiciaries")
                .getJsonArray("courtScheduleJudiciaries").get(0)
                .asJsonObject().getString("courtScheduleId"), is(courtScheduleId));
    }

    @Test
    void shouldExportAllocatedListings() throws Exception {
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId);
        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setCourtScheduleId(courtScheduleId);

        String exportMiDataRequestParams = getPayload("courtscheduler.export.mi_data_query.json");
        exportMiDataRequestParams = exportMiDataRequestParams.replace("FROM_DATE", fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace("TO_DATE", toDate.toString());

        Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/mi/allocated_listings",
                "application/vnd.courtscheduler.export.allocated_listings+json", USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonObject("allocatedListings")
                .getJsonArray("allocatedListings").get(0)
                .asJsonObject().getString("courtScheduleId"), is(courtScheduleId));
    }


}
