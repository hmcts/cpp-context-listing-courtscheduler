package uk.gov.moj.cpp.courtscheduler.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.benas.randombeans.EnhancedRandomBuilder;
import io.github.benas.randombeans.api.EnhancedRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.justice.services.common.http.HeaderConstants;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.RequestParamsBuilder;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.justice.services.test.utils.core.rest.RestClient;
import uk.gov.moj.cpp.courtscheduler.integration.utils.DatabaseSeeder;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import javax.json.JsonObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static java.util.UUID.fromString;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.collections.MapUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupLoggedInUsersPermissionQueryStub;


class MiExportIT {

    private ObjectMapper mapper = new ObjectMapper();

    private static final String URL = "http://" + getHost() + ":8080/courtscheduler-api/rest/courtscheduler";
    private static final UUID USER_ID = fromString("bb593957-08a8-4d41-a5c1-7674d38d4f43");
    private static final EnhancedRandom RANDOM = new EnhancedRandomBuilder()
            .maxStringLength(5)
            .build();
    protected static final RestClient REST_CLIENT = new RestClient();
    private final DatabaseSeeder databaseSeeder = new DatabaseSeeder();

    private final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();

    private final JsonObjectToObjectConverter jsonObjectToObjectConverter = new JsonObjectToObjectConverter(mapper);

    @BeforeAll
    public static void setUp() {
        setupLoggedInUsersPermissionQueryStub(USER_ID.toString());
    }

    @BeforeEach
    public void cleanTheDatabase() throws Exception {
        databaseSeeder.cleanDb();
    }

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
                "application/vnd.courtscheduler.export.court_schedule+json", USER_ID.toString(), map);

        databaseSeeder.insertCourtSchedule(expected);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonObject("courtSchedules")
                .getJsonArray("courtSchedules").get(0)
                .asJsonObject().getString("courtScheduleId"), is(courtScheduleId));
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
                "application/vnd.courtscheduler.export.court_schedule_judiciary+json", USER_ID.toString(), map);

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
                "application/vnd.courtscheduler.export.allocated_listings+json", USER_ID.toString(), map);

        databaseSeeder.insertCourtSchedule(expected);
        databaseSeeder.insertAllocatedListing(allocatedListing);

        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());

        assertThat(jsonObject.getJsonObject("allocatedListings")
                .getJsonArray("allocatedListings").get(0)
                .asJsonObject().getString("courtScheduleId"), is(courtScheduleId));
    }


    private static RequestParams getRequestParams(final String path, final String contentType, final String userId, final Map<String, Object> queryParams) {
        final String url = (isEmpty(queryParams)) ? URL + path : (URL + path + "?" + createUrlFromParam(queryParams));
        RequestParamsBuilder requestParamsBuilder = RequestParamsBuilder.requestParams(url, contentType);
        if (isNoneBlank(userId)) {
            requestParamsBuilder = requestParamsBuilder.withHeader(HeaderConstants.USER_ID, userId);
        }
        return requestParamsBuilder.build();
    }

    private static String createUrlFromParam(final Map<String, Object> queryParam) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Object> e : queryParam.entrySet()) {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue().toString(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
