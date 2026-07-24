package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.lang.String.valueOf;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.UTC_ZONE;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


class HearingIdIT extends AbstractIT {

    private static final String RELATIVE_URL = "/hearingslots";

    @Test
    void shouldRetrieveSinglePageHearingIds() throws Exception {
        final LocalDate today = LocalDate.now();
        final LocalDate sessionDate = today.minusDays(5);
        final CourtSchedule courtSchedule1 = createCourtSchedule(sessionDate, "COURT-SCHEDULE-1", "HOUSE-1");
        databaseSeeder.insertCourtSchedule(courtSchedule1);

        final CourtSchedule courtSchedule2 = createCourtSchedule(sessionDate, "COURT-SCHEDULE-2", "HOUSE-2");
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        final LocalDate sessionDate1 = today.minusDays(3);
        final CourtSchedule courtSchedule3 = createCourtSchedule(sessionDate1, "COURT-SCHEDULE-3", "HOUSE-3");
        databaseSeeder.insertCourtSchedule(courtSchedule3);

        List<String> expHearingIds = new ArrayList<>();
        final String hearingId1 = randomUUID().toString();
        final LocalDateTime hearing1StartTime = sessionDate.atTime(17, 0);
        final AllocatedListing allocateListing1 =
                createAllocateListing("1", "BOOKING-1", courtSchedule1.getCourtScheduleId(), hearingId1, hearing1StartTime);
        databaseSeeder.insertAllocatedListing(allocateListing1);
        expHearingIds.add(hearingId1);

        final String hearingId2 = randomUUID().toString();
        final LocalDateTime hearing2StartTime = sessionDate.atTime(18, 0);
        final AllocatedListing allocateListing2 =
                createAllocateListing("2", "BOOKING-2", courtSchedule1.getCourtScheduleId(), hearingId2, hearing2StartTime);
        databaseSeeder.insertAllocatedListing(allocateListing2);
        expHearingIds.add(hearingId2);

        final String hearingId3 = randomUUID().toString();
        final LocalDateTime hearing3StartTime = sessionDate1.atTime(9, 0);
        final AllocatedListing allocateListing3 =
                createAllocateListing("3", "BOOKING-3", courtSchedule2.getCourtScheduleId(), hearingId3, hearing3StartTime);
        databaseSeeder.insertAllocatedListing(allocateListing3);
        expHearingIds.add(hearingId3);

        final String hearingId4 = randomUUID().toString();
        final LocalDateTime hearing4StartTime = sessionDate1.atTime(11, 0);
        final AllocatedListing allocateListing4 =
                createAllocateListing("4", "BOOKING-4", courtSchedule3.getCourtScheduleId(), hearingId4, hearing4StartTime);
        databaseSeeder.insertAllocatedListing(allocateListing4);
        expHearingIds.add(hearingId4);

        String hearingIdsReq = getPayload("courtscheduler.get.hearing.slots.json");
        hearingIdsReq = hearingIdsReq.replace("PANEL", "ADULT");
        hearingIdsReq = hearingIdsReq.replace("OU_CODE", "BA123");
        hearingIdsReq = hearingIdsReq.replace("COURT_SESSION", "AM");
        final LocalDate startDate = today.minusDays(10);
        hearingIdsReq = hearingIdsReq.replace("SESSION_START_DATE", startDate.toString());
        hearingIdsReq = hearingIdsReq.replace("SESSION_END_DATE", today.minusDays(1).toString());
        hearingIdsReq = hearingIdsReq.replace("\"pageSize\": \"1\"", "\"pageSize\": \"10\"");

        Map<String, Object> map = new ObjectMapper().readValue(hearingIdsReq, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertEquals(OK.getStatusCode(), tempResponseData.getStatus().getStatusCode());

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        assertThat(jsonObject.getInt("results"), is(4));
        assertThat(jsonObject.getInt("pageCount"), is(1));

        JsonArray hearingIds = jsonObject.getJsonArray("hearingIds");
        assertThat(hearingIds.getJsonObject(0).getString("hearingId"), is(expHearingIds.get(0)));
        assertThat(hearingIds.getJsonObject(1).getString("hearingId"), is(expHearingIds.get(1)));
        assertThat(hearingIds.getJsonObject(2).getString("hearingId"), is(expHearingIds.get(2)));
        assertThat(hearingIds.getJsonObject(3).getString("hearingId"), is(expHearingIds.get(3)));

        hearingIds.forEach(each -> {
            assertThat(each.asJsonObject().getString("courtScheduleId"), startsWith("COURT-SCHEDULE-"));
            assertThat(each.asJsonObject().getString("hearingDate"), is(notNullValue()));
            assertThat(each.asJsonObject().getInt("hearingDayCount"), is(1));
            assertThat(each.asJsonObject().getInt("hearingDayPosition"), is(1));
        });

        final Instant exactHearingInstant = hearing3StartTime.toInstant(ZoneOffset.UTC);
        checkExactStartTimeQuery(map, exactHearingInstant, hearingId3);
        final Instant exactHearingInstantWithMillis = hearing3StartTime.toInstant(ZoneOffset.UTC).plusSeconds(50).plusMillis(999);
        checkExactStartTimeQuery(map, exactHearingInstantWithMillis, hearingId3);
    }

    @Test
    void shouldRetrieveMultiPageHearingIds() throws Exception {
        List<String> expHearingIds = new ArrayList<>();
        LocalDate today = LocalDate.now();
        int numOfHearings = 18;
        for (int idx = 1; idx <= numOfHearings; idx++) {
            LocalDate sessionDate = today.minusDays(numOfHearings - idx);
            CourtSchedule courtSchedule = createCourtSchedule(sessionDate, "COURT-SCHEDULE-" + idx, "HOUSE-" + idx);
            databaseSeeder.insertCourtSchedule(courtSchedule);

            String hearingId = randomUUID().toString();
            LocalDateTime hearingStartTime = sessionDate.atTime(11, 0);
            AllocatedListing allocateListing =
                    createAllocateListing(valueOf(idx), "BOOKING-" + idx, courtSchedule.getCourtScheduleId(), hearingId, hearingStartTime);
            databaseSeeder.insertAllocatedListing(allocateListing);
            expHearingIds.add(hearingId);
        }

        String hearingIdsReq = getPayload("courtscheduler.get.hearing.slots.json");
        hearingIdsReq = hearingIdsReq.replace("PANEL", "ADULT");
        hearingIdsReq = hearingIdsReq.replace("OU_CODE", "BA123");
        hearingIdsReq = hearingIdsReq.replace("COURT_SESSION", "AM");
        hearingIdsReq = hearingIdsReq.replace("SESSION_START_DATE", today.minusDays(numOfHearings).toString());
        hearingIdsReq = hearingIdsReq.replace("SESSION_END_DATE", today.toString());

        final ObjectMapper objMapper = new ObjectMapper();
        Map<String, Object> paramsMap = objMapper.readValue(hearingIdsReq, new TypeReference<>() {
        });
        RequestParams requestParams = getRequestParams(paramsMap);
        ResponseData responseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertEquals(OK.getStatusCode(), responseData.getStatus().getStatusCode());

        JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertThat(jsonObject.getInt("results"), is(numOfHearings));
        assertThat(jsonObject.getInt("pageCount"), is(2));

        JsonArray hearingIds = jsonObject.getJsonArray("hearingIds");
        int defaultPageSize = 10;
        for (int idx = 0; idx < defaultPageSize; idx++) {
            assertThat(hearingIds.getJsonObject(idx).getString("hearingId"), is(expHearingIds.get(idx)));
            assertThat(hearingIds.getJsonObject(idx).getString("courtScheduleId"), startsWith("COURT-SCHEDULE-"));
            assertThat(hearingIds.getJsonObject(idx).getString("hearingDate"), is(notNullValue()));
            assertThat(hearingIds.getJsonObject(idx).getInt("hearingDayCount"), is(1));
            assertThat(hearingIds.getJsonObject(idx).getInt("hearingDayPosition"), is(1));

        }

        paramsMap.put("pageNumber:", 2);
        requestParams = getRequestParams(paramsMap);
        responseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertEquals(OK.getStatusCode(), responseData.getStatus().getStatusCode());

        jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertThat(jsonObject.getInt("pageCount"), is(2));


        hearingIds = jsonObject.getJsonArray("hearingIds");
        for (int idx = numOfHearings; idx < numOfHearings - defaultPageSize; idx++) {
            assertThat(hearingIds.getString(idx), is(expHearingIds.get(idx))); // we never hit here. To be fixed
        }
    }

    @Test
    void shouldFilterHearingIdsByStatus() throws Exception {
        final LocalDate today = LocalDate.now();
        final LocalDate sessionDate = today.minusDays(5);

        final CourtSchedule draftCourtSchedule = createCourtSchedule(sessionDate, "COURT-SCHEDULE-DRAFT", "HOUSE-DRAFT");
        draftCourtSchedule.setIsDraft(true);
        databaseSeeder.insertCourtSchedule(draftCourtSchedule);

        final CourtSchedule finalCourtSchedule = createCourtSchedule(sessionDate, "COURT-SCHEDULE-FINAL", "HOUSE-FINAL");
        finalCourtSchedule.setIsDraft(false);
        databaseSeeder.insertCourtSchedule(finalCourtSchedule);

        final String draftHearingId = randomUUID().toString();
        final LocalDateTime draftHearingStartTime = sessionDate.atTime(9, 0);
        final AllocatedListing draftAllocatedListing =
                createAllocateListing("1", "BOOKING-DRAFT", draftCourtSchedule.getCourtScheduleId(), draftHearingId, draftHearingStartTime);
        databaseSeeder.insertAllocatedListing(draftAllocatedListing);

        final String finalHearingId = randomUUID().toString();
        final LocalDateTime finalHearingStartTime = sessionDate.atTime(10, 0);
        final AllocatedListing finalAllocatedListing =
                createAllocateListing("2", "BOOKING-FINAL", finalCourtSchedule.getCourtScheduleId(), finalHearingId, finalHearingStartTime);
        databaseSeeder.insertAllocatedListing(finalAllocatedListing);

        String hearingIdsReq = getPayload("courtscheduler.get.hearing.slots.json");
        hearingIdsReq = hearingIdsReq.replace("PANEL", "ADULT");
        hearingIdsReq = hearingIdsReq.replace("OU_CODE", "BA123");
        hearingIdsReq = hearingIdsReq.replace("COURT_SESSION", "AM");
        final LocalDate startDate = today.minusDays(10);
        hearingIdsReq = hearingIdsReq.replace("SESSION_START_DATE", startDate.toString());
        hearingIdsReq = hearingIdsReq.replace("SESSION_END_DATE", today.minusDays(1).toString());

        final Map<String, Object> map = new ObjectMapper().readValue(hearingIdsReq, new TypeReference<>() {
        });

        // status=FINAL -> only the non-draft (FINAL) hearing
        map.put("status", "FINAL");
        RequestParams requestParams = getRequestParams(map);
        ResponseData responseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();
        assertEquals(OK.getStatusCode(), responseData.getStatus().getStatusCode());
        JsonObject jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertThat(jsonObject.getInt("results"), is(1));
        JsonArray hearingIds = jsonObject.getJsonArray("hearingIds");
        assertThat(hearingIds.getJsonObject(0).getString("hearingId"), is(finalHearingId));

        // status=DRAFT -> only the DRAFT hearing
        map.put("status", "DRAFT");
        requestParams = getRequestParams(map);
        responseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();
        assertEquals(OK.getStatusCode(), responseData.getStatus().getStatusCode());
        jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertThat(jsonObject.getInt("results"), is(1));
        hearingIds = jsonObject.getJsonArray("hearingIds");
        assertThat(hearingIds.getJsonObject(0).getString("hearingId"), is(draftHearingId));

        // status absent -> BOTH draft and non-draft hearings (ordered by court_house_name: HOUSE-DRAFT < HOUSE-FINAL)
        map.remove("status");
        requestParams = getRequestParams(map);
        responseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();
        assertEquals(OK.getStatusCode(), responseData.getStatus().getStatusCode());
        jsonObject = stringToJsonObjectConverter.convert(responseData.getPayload());
        assertThat(jsonObject.getInt("results"), is(2));
        hearingIds = jsonObject.getJsonArray("hearingIds");
        assertThat(hearingIds.getJsonObject(0).getString("hearingId"), is(draftHearingId));
        assertThat(hearingIds.getJsonObject(1).getString("hearingId"), is(finalHearingId));
    }

    private CourtSchedule createCourtSchedule(LocalDate sessionDate,
                                              String courtScheduleId,
                                              String courtHouseName) {
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setPanel("ADULT");
        courtSchedule.setOuCode("BA123");
        courtSchedule.setCourtSession("AM");
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setCourtHouseName(courtHouseName);
        courtSchedule.setBusinessType("BUSS");
        courtSchedule.setActive(true);

        return courtSchedule;
    }

    private AllocatedListing createAllocateListing(String id,
                                                   String bookingId,
                                                   String courtScheduleId,
                                                   String hearingId, LocalDateTime hearingStartTime) {
        AllocatedListing allocatedListing = new AllocatedListing();
        allocatedListing.setId(id);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setCourtRoomId(1);
        allocatedListing.setHearingStartTime(Date.from(hearingStartTime.atZone(UTC_ZONE).toInstant()));
        allocatedListing.setDuration(120);
        allocatedListing.setOucode("BA124");
        allocatedListing.setRotaBusinessType("BUSS");

        return allocatedListing;
    }

    private RequestParams getRequestParams(final Map<String, Object> map) {
        return getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.ids+json", SYSTEM_USER_ID, map);
    }

    private void checkExactStartTimeQuery(final Map<String, Object> map, final Instant exactHearingInstant, final String hearingId3) {
        map.put("exactHearingStartDateTime", exactHearingInstant.toString());
        final RequestParams requestParamsWithStartDateTime = getRequestParams(map);
        final ResponseData tempResponseDataWithStartDateTime = poll(requestParamsWithStartDateTime).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertEquals(OK.getStatusCode(), tempResponseDataWithStartDateTime.getStatus().getStatusCode());

        JsonObject jsonObjectWithStartDateTime = stringToJsonObjectConverter.convert(tempResponseDataWithStartDateTime.getPayload());
        assertThat(jsonObjectWithStartDateTime.getInt("results"), is(1));
        assertThat(jsonObjectWithStartDateTime.getInt("pageCount"), is(1));
        JsonArray hearingIdsWithStartDateTime = jsonObjectWithStartDateTime.getJsonArray("hearingIds");
        assertThat(hearingIdsWithStartDateTime.getJsonObject(0).getString("hearingId"), is(hearingId3));
    }
}
