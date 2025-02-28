package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.Collections.sort;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupUserAsSystemUser;

import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


class HearingIdIT extends AbstractIT {

    private static final String RELATIVE_URL = "/hearingslots";

    @BeforeAll
    static void setupSystemUser() {
        setupUserAsSystemUser(USER_ID.toString());
    }

    @Test
    void shouldFindHearingIds() throws Exception {
        final LocalDate today = LocalDate.now();
        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtScheduleId("COURT-SCHEDULE-1");
        courtSchedule1.setPanel("ADULT");
        courtSchedule1.setOuCode("BA123");
        courtSchedule1.setCourtSession("AM");
        final LocalDate sessionDate = today.minusDays(5);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setCourtHouseName("HOUSE-1");
        courtSchedule1.setBusinessType("BUSS");
        courtSchedule1.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule1);

        final CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId("COURT-SCHEDULE-2");
        courtSchedule2.setPanel("ADULT");
        courtSchedule2.setOuCode("BA123");
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setCourtHouseName("HOUSE-2");
        courtSchedule2.setCourtSession("AM");
        courtSchedule2.setBusinessType("BUSS");
        courtSchedule2.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        final CourtSchedule courtSchedule3 = random(CourtSchedule.class);
        courtSchedule3.setCourtScheduleId("COURT-SCHEDULE-3");
        courtSchedule3.setPanel("ADULT");
        courtSchedule3.setOuCode("BA123");
        final LocalDate sessionDate1 = today.minusDays(3);
        courtSchedule3.setSessionDate(sessionDate1);
        courtSchedule3.setActive(true);
        courtSchedule3.setCourtSession("AM");
        courtSchedule3.setBusinessType("BUSS");
        databaseSeeder.insertCourtSchedule(courtSchedule3);

        List<String> expHearingIds = new ArrayList<>();
        final String hearingId1 = randomUUID().toString();
        final LocalDateTime hearing1StartTime = sessionDate.atTime(17, 0);
        databaseSeeder.insertAllocatedListing(createAllocateListing("1", "BOOKING-1", "COURT-SCHEDULE-1", hearingId1, hearing1StartTime));
        final String hearingId2 = randomUUID().toString();
        final LocalDateTime hearing2StartTime = sessionDate.atTime(11, 0);
        databaseSeeder.insertAllocatedListing(createAllocateListing("2", "BOOKING-2", "COURT-SCHEDULE-1", hearingId2, hearing2StartTime));
        expHearingIds.add(hearingId2);
        expHearingIds.add(hearingId1);

        final String hearingId3 = randomUUID().toString();
        final LocalDateTime hearing3StartTime = sessionDate1.atTime(9, 0);
        databaseSeeder.insertAllocatedListing(createAllocateListing("3", "BOOKING-3", "COURT-SCHEDULE-2", hearingId3, hearing3StartTime));

        final String hearingId4 = randomUUID().toString();
        final LocalDateTime hearing4StartTime = sessionDate1.atTime(11, 0);
        databaseSeeder.insertAllocatedListing(createAllocateListing("4", "BOOKING-4", "COURT-SCHEDULE-3", hearingId4, hearing4StartTime));
        expHearingIds.add(hearingId3);
        expHearingIds.add(hearingId4);


        String hearingIdsReq = getPayload("courtscheduler.get.hearing.slots.json");
        hearingIdsReq = hearingIdsReq.replace("PANEL", "ADULT");
        hearingIdsReq = hearingIdsReq.replace("OU_CODE", "BA123");
        hearingIdsReq = hearingIdsReq.replace("SESSION_START_DATE", today.minusDays(10).toString());
        hearingIdsReq = hearingIdsReq.replace("SESSION_END_DATE", today.minusDays(1).toString());
        hearingIdsReq = hearingIdsReq.replace("\"pageSize\": \"1\"", "\"pageSize\": \"10\"");

        Map<String, Object> map = new ObjectMapper().readValue(hearingIdsReq, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.ids+json", USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertEquals(OK.getStatusCode(), tempResponseData.getStatus().getStatusCode());

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        assertThat(jsonObject.getInt("results"), is(4));
        assertThat(jsonObject.getInt("pageCount"), is(1));

        JsonArray hearingIds = jsonObject.getJsonArray("hearingIds");
        assertThat(hearingIds.getString(0), is(expHearingIds.get(0)));
        assertThat(hearingIds.getString(1), is(expHearingIds.get(1)));
        assertThat(hearingIds.getString(2), is(expHearingIds.get(2)));
        assertThat(hearingIds.getString(3), is(expHearingIds.get(3)));
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
        allocatedListing.setHearingStartTime(Date.from(hearingStartTime.atZone(ZoneId.of("Europe/London")).toInstant()));
        allocatedListing.setDuration(120);
        allocatedListing.setOucode("BA124");
        allocatedListing.setRotaBusinessType("BUSS");

        return allocatedListing;
    }
}
