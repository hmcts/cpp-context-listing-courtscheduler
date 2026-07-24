package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import jakarta.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;


class MiExportIT extends AbstractIT {

    @Test
    void shouldExportCourtSchedules() throws SQLException, JsonProcessingException {
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId);

        String exportMiDataRequestParams = getPayload("courtscheduler.export.mi_data_query.json");
        exportMiDataRequestParams = exportMiDataRequestParams.replace("FROM_DATE", fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace("TO_DATE", toDate.toString());

        Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/mi/court_schedules",
                "application/vnd.courtscheduler.export.court_schedule+json", SYSTEM_USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonArray("courtSchedules").get(0)
                .asJsonObject().getString("id"), is(courtScheduleId));
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
                "application/vnd.courtscheduler.export.court_schedule_judiciary+json", SYSTEM_USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonArray("courtScheduleJudiciaries").get(0)
                .asJsonObject().getString("court_schedule_id"), is(courtScheduleId));
    }

    @Test
    void shouldExportAllocatedListings() throws Exception {
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        String courtScheduleId = UUID.randomUUID().toString();
        CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId);
        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtScheduleId);

        String exportMiDataRequestParams = getPayload("courtscheduler.export.mi_data_query.json");
        exportMiDataRequestParams = exportMiDataRequestParams.replace("FROM_DATE", fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace("TO_DATE", toDate.toString());

        Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/mi/allocated_listings",
                "application/vnd.courtscheduler.export.allocated_listings+json", SYSTEM_USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonArray("allocatedListings").get(0)
                .asJsonObject().getString("court_schedule_id"), is(courtScheduleId));
    }


}
