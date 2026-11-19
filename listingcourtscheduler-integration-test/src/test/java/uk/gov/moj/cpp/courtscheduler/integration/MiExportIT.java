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

import jakarta.json.JsonObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;


class MiExportIT extends AbstractIT {
    private static final String MI_DATA_QUERY_PAYLOAD = "courtscheduler.export.mi_data_query.json";
    private static final String FROM_DATE_2 = "FROM_DATE";
    private static final String TO_DATE_2 = "TO_DATE";


    @Test
    void shouldExportCourtSchedules() throws SQLException, JsonProcessingException {
        final LocalDate fromDate = LocalDate.now().minusDays(1);
        final LocalDate toDate = LocalDate.now().plusDays(1);
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId);

        String exportMiDataRequestParams = getPayload(MI_DATA_QUERY_PAYLOAD);
        exportMiDataRequestParams = exportMiDataRequestParams.replace(FROM_DATE_2, fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace(TO_DATE_2, toDate.toString());

        final Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/mi/court_schedules",
                "application/vnd.courtscheduler.export.court_schedule+json", SYSTEM_USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonArray("courtSchedules").get(0)
                .asJsonObject().getString("id"), is(courtScheduleId));
    }

    /**
     * SPRDT-1370 (C2-14). The MI export reads rows, not reference data: a session persisted under
     * a business type the reference data no longer carries still exports, with its raw code.
     */
    @Test
    void shouldExportCourtSchedulesWhosePersistedBusinessTypeHasBeenRetired() throws SQLException, JsonProcessingException {
        final LocalDate fromDate = LocalDate.now().minusDays(1);
        final LocalDate toDate = LocalDate.now().plusDays(1);
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId);
        expected.setBusinessType("FWT");
        String exportMiDataRequestParams = getPayload(MI_DATA_QUERY_PAYLOAD);
        exportMiDataRequestParams = exportMiDataRequestParams.replace(FROM_DATE_2, fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace(TO_DATE_2, toDate.toString());
        final Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });
        final RequestParams requestParams = getRequestParams("/mi/court_schedules",
                "application/vnd.courtscheduler.export.court_schedule+json", SYSTEM_USER_ID, map);
        databaseSeeder.insertCourtSchedule(expected);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject exported = stringToJsonObjectConverter.convert(tempResponseData.getPayload())
                .getJsonArray("courtSchedules").get(0).asJsonObject();
        assertThat(exported.getString("id"), is(courtScheduleId));
        assertThat(exported.getString("rota_business_type"), is("FWT"));
    }

    @Test
    void shouldExportCourtScheduleJudiciaries() throws Exception {
        final LocalDate fromDate = LocalDate.now().minusDays(1);
        final LocalDate toDate = LocalDate.now().plusDays(1);
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId);
        final CourtScheduleJudiciary courtScheduleJudiciary = RANDOM.nextObject(CourtScheduleJudiciary.class);
        final CourtScheduleJudiciaryKey courtScheduleJudiciaryId = courtScheduleJudiciary.getId();
        courtScheduleJudiciaryId.setCourtScheduleId(courtScheduleId);

        String exportMiDataRequestParams = getPayload(MI_DATA_QUERY_PAYLOAD);
        exportMiDataRequestParams = exportMiDataRequestParams.replace(FROM_DATE_2, fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace(TO_DATE_2, toDate.toString());

        final Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/mi/court_schedule_judiciaries",
                "application/vnd.courtscheduler.export.court_schedule_judiciary+json", SYSTEM_USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonArray("courtScheduleJudiciaries").get(0)
                .asJsonObject().getString("court_schedule_id"), is(courtScheduleId));
    }

    @Test
    void shouldExportAllocatedListings() throws Exception {
        final LocalDate fromDate = LocalDate.now().minusDays(1);
        final LocalDate toDate = LocalDate.now().plusDays(1);
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule expected = RANDOM.nextObject(CourtSchedule.class);
        expected.setCourtScheduleId(courtScheduleId);
        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtScheduleId);

        String exportMiDataRequestParams = getPayload(MI_DATA_QUERY_PAYLOAD);
        exportMiDataRequestParams = exportMiDataRequestParams.replace(FROM_DATE_2, fromDate.toString());
        exportMiDataRequestParams = exportMiDataRequestParams.replace(TO_DATE_2, toDate.toString());

        final Map<String, Object> map = mapper.readValue(exportMiDataRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/mi/allocated_listings",
                "application/vnd.courtscheduler.export.allocated_listings+json", SYSTEM_USER_ID, map);

        databaseSeeder.insertCourtSchedule(expected);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonArray("allocatedListings").get(0)
                .asJsonObject().getString("court_schedule_id"), is(courtScheduleId));
    }


}
