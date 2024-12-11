package uk.gov.moj.cpp.courtscheduler.integration;

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
import java.util.ArrayList;
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
    void testHearingIdsRetrieval() throws Exception {
        final String panel1 = "ADULT";
        final String panel2 = "YOUNG";
        final String oucodeL2Code = "BA124-L2";
        final String ouCode = "BA124";
        final String courtSession = "AM";

        final CourtSchedule courtSchedule1 = RANDOM.nextObject(CourtSchedule.class);
        final String courtScheduleId1 = randomUUID().toString();
        courtSchedule1.setCourtScheduleId(courtScheduleId1);
        courtSchedule1.setPanel(panel1);
        courtSchedule1.setSessionDate(LocalDate.now());
        courtSchedule1.setOperationalUnit(oucodeL2Code);
        courtSchedule1.setOuCode(ouCode);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule1);

        final CourtSchedule courtSchedule2 = RANDOM.nextObject(CourtSchedule.class);
        final String courtScheduleId2 = randomUUID().toString();
        courtSchedule2.setCourtScheduleId(courtScheduleId2);
        courtSchedule2.setPanel(panel2);
        courtSchedule2.setSessionDate(LocalDate.now());
        courtSchedule2.setOperationalUnit(oucodeL2Code);
        courtSchedule2.setOuCode(ouCode);
        courtSchedule2.setCourtSession(courtSession);
        courtSchedule2.setActive(true);
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        final List<String> expHearingIds = new ArrayList<>();
        final String hearingId1 = randomUUID().toString();
        expHearingIds.add(hearingId1);
        final String bookingId1 = randomUUID().toString();
        final AllocatedListing allocatedListing1 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing1.setCourtScheduleId(courtScheduleId1);
        allocatedListing1.setHearingId(hearingId1);
        allocatedListing1.setBookingId(bookingId1);
        databaseSeeder.insertAllocatedListing(allocatedListing1);

        final String hearingId2 = randomUUID().toString();
        expHearingIds.add(hearingId2);
        final String bookingId2 = randomUUID().toString();
        final AllocatedListing allocatedListing2 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing2.setCourtScheduleId(courtScheduleId1);
        allocatedListing2.setHearingId(hearingId2);
        allocatedListing2.setBookingId(bookingId2);
        databaseSeeder.insertAllocatedListing(allocatedListing2);

        String bookingId3 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        AllocatedListing allocatedListing3 = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing3.setCourtScheduleId(courtScheduleId2);
        allocatedListing3.setHearingId(hearingId3);
        allocatedListing3.setBookingId(bookingId3);
        databaseSeeder.insertAllocatedListing(allocatedListing3);

        String hearingIdsReq = getPayload("courtscheduler.get.hearing.slots.json");
        LocalDate fromDate = courtSchedule1.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule1.getSessionDate().plusDays(1);

        hearingIdsReq = hearingIdsReq.replace("PANEL", panel1);
        hearingIdsReq = hearingIdsReq.replace("OU_CODE", ouCode);
        hearingIdsReq = hearingIdsReq.replace("SESSION_START_DATE", fromDate.toString());
        hearingIdsReq = hearingIdsReq.replace("SESSION_END_DATE", toDate.toString());
        hearingIdsReq = hearingIdsReq.replace("\"pageSize\": \"1\"", "\"pageSize\": \"10\"");

        Map<String, Object> map = new ObjectMapper().readValue(hearingIdsReq, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.ids+json", USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).until();

        assertEquals(OK.getStatusCode(), tempResponseData.getStatus().getStatusCode());

        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        assertThat(jsonObject.getInt("results"), is(2));
        assertThat(jsonObject.getInt("pageCount"), is(1));

        JsonArray hearingIds = jsonObject.getJsonArray("hearingIds");
        sort(expHearingIds);
        assertThat(hearingIds.getString(0), is(expHearingIds.get(0)));
        assertThat(hearingIds.getString(1), is(expHearingIds.get(1)));
    }
}
