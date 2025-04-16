package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static io.smallrye.common.constraint.Assert.assertTrue;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static uk.gov.justice.services.test.utils.core.http.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.test.utils.core.http.RequestParams;
import uk.gov.justice.services.test.utils.core.http.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.rota.PanelTypes;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;


class HearingSlotIT extends AbstractIT {

    private static final String RELATIVE_URL = "/hearingslots";

    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Test
    void shouldUpdateHearingSlot() throws SQLException {
        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String hearingId = randomUUID().toString();
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

        String updateHearingSlotsPayload = getPayload("courtscheduler.update.hearing.slots.json");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_ID", hearingId);
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("COURT_SCHEDULE_ID", courtScheduleId);

        final Response response = putCommand(RELATIVE_URL, "application/vnd.courtscheduler.update.hearing.slots+json", SYSTEM_USER_ID, updateHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldUpdateRequestedListHearingSlots() throws SQLException {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setCourtRoomNumber(1501);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("87b6ea2a-9d81-3a47-884d-306419431065");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxSlots(0);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession("AD");
        courtSchedule.setMaxAdMorningDuration(180);
        courtSchedule.setMaxAdAfternoonDuration(180);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setSessionDate(LocalDate.of(2025, 4, 3));
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        CourtSchedule courtSchedule2 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId("5771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule2.setOuCode("B40IM00");
        courtSchedule2.setCourtRoomNumber(1501);
        courtSchedule2.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule2.setCourtRoomId("87b6ea2a-9d81-3a47-884d-306419431065");
        courtSchedule2.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule2.setPanel(PanelTypes.YOUTH.name());
        courtSchedule2.setSlotBased(true);
        courtSchedule2.setMaxSlots(2);
        courtSchedule2.setSupportAdSplit(true);
        courtSchedule2.setCourtSession("AD");
        courtSchedule2.setMaxAdMorningDuration(180);
        courtSchedule2.setMaxAdAfternoonDuration(180);
        courtSchedule2.setMaxDuration(0);
        courtSchedule2.setSessionDate(LocalDate.of(2025, 4, 5));
        courtSchedule2.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule2.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        CourtSchedule courtSchedule3 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule3.setCourtScheduleId("2771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule3.setOuCode("B40IM00");
        courtSchedule3.setCourtRoomNumber(1501);
        courtSchedule3.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule3.setCourtRoomId("87b6ea2a-9d81-3a47-884d-306419431065");
        courtSchedule3.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule3.setPanel(PanelTypes.YOUTH.name());
        courtSchedule3.setSlotBased(true);
        courtSchedule3.setMaxSlots(0);
        courtSchedule3.setSupportAdSplit(true);
        courtSchedule3.setCourtSession("AD");
        courtSchedule3.setMaxAdMorningDuration(180);
        courtSchedule3.setMaxAdAfternoonDuration(180);
        courtSchedule3.setMaxDuration(360);
        courtSchedule3.setSessionDate(LocalDate.of(2025, 5, 5));
        courtSchedule3.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule3.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule3);

        String updateHearingSlotsPayload = getPayload("courtscheduler.list.hearings-in-court-sessions.json");

        final Response response = putCommand("/list/hearingslots", "application/vnd.courtscheduler.list.hearings-in-court-sessions+json", SYSTEM_USER_ID, updateHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        JsonArray jsonArray = jsonObject.getJsonArray("hearings");
        assertThat(jsonArray.size(), is(3));
        jsonArray.stream().forEach( hearing ->
                {final JsonObject hearingJson = (JsonObject) hearing ;
                    if (hearingJson.getString("courtScheduleId").equals("1771a96b-1c5a-45d1-b647-1bec5212cafc")) {
                        assertThat(hearingJson.getString("hearingId"), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
                        assertThat(hearingJson.getString("sessionStartTime"), is("2025-04-03T09:00:00Z"));
                        assertThat(hearingJson.getInt("duration"), is(20));
                    }
                    if (hearingJson.getString("courtScheduleId").equals("5771a96b-1c5a-45d1-b647-1bec5212cafc")) {
                        assertThat(hearingJson.getString("hearingId"), is("6771a96b-1c5a-45d1-b647-1bec5212cafc"));
                        assertThat(hearingJson.getString("sessionStartTime"), is("2025-04-03T09:00:00Z"));
                        assertThat(hearingJson.getInt("duration"), is(1));
                    }
                    if (hearingJson.getString("courtScheduleId").equals("2771a96b-1c5a-45d1-b647-1bec5212cafc")) {
                        assertThat(hearingJson.getString("hearingId"), is("6771a96b-1c5a-45d1-b647-1bec5212cafc"));
                        assertThat(hearingJson.getString("sessionStartTime"), is("2025-04-03T09:00:00Z"));
                        assertThat(hearingJson.getInt("duration"), is(1));
                    }
                }
        );
    }

    @Test
    void shouldRetrieveHearingSlot() throws Exception {
        final String courtSession = AM_SESSION;
        final LocalDate sessionDate = LocalDate.of(2025, 1, 3);
        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String bookingId2 = randomUUID().toString();
        String bookingId3 = randomUUID().toString();
        String bookingId4 = randomUUID().toString();
        String hearingId = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String hearingId4 = randomUUID().toString();

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCourtSession(courtSession);
        courtSchedule.setPanel(PanelTypes.YOUTH.name());
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId, bookingId, "10:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId2, bookingId2, "10:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId3, bookingId3, "11:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId4, bookingId4, "12:00", 1);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject)jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(4));
        slotStartTimesJsonArray.stream().forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals("2025-01-03T09:30:00.000Z")) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is("2025-01-03T10:00:00.000Z"));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals("2025-01-03T10:00:00.000Z")) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(2));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is("2025-01-03T11:00:00.000Z"));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals("2025-01-03T11:00:00.000Z")) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is("2025-01-03T12:00:00.000Z"));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals("2025-01-03T12:00:00.000Z")) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is("2025-01-03T12:30:00.000Z"));
            }
        });
    }

    @Test
    void shouldRetrieveHearingSlotsForNonAllocated() throws Exception {
        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        final String courtSession = ALL_DAY;
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCourtSession(courtSession);
        courtSchedule.setPanel(PanelTypes.YOUTH.name());
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setSessionDate(LocalDate.of(2025, 1, 3));
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "10:30"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "17:30"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject)jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(8));
        slotStartTimesJsonArray.stream().forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
        });
    }

    private void createAllocatedListingsAndInsertWithZone(String courtScheduleId, String hearingId, String bookingId, String localTime, int duration) throws Exception {
        ZoneId zoneId = ZoneId.of("Europe/London");
        LocalDate date = LocalDate.now().plusDays(1);
        ZonedDateTime londonZdt = date.atTime(LocalTime.parse(localTime)).atZone(zoneId);
        ZonedDateTime utcZdt = londonZdt.withZoneSameInstant(ZoneOffset.UTC);

        String utcTime = utcZdt.toLocalTime().toString();
        createAllocatedListingsAndInsert(courtScheduleId, hearingId, bookingId, utcTime, duration);
    }

    @Test
    void shouldRetrieveAllDaySplitWithBookings() throws Exception {

        LocalDate sessionDate = LocalDate.now().plusDays(1);

        ZoneId zoneId = ZoneId.of("Europe/London");
        ZonedDateTime startZdt = sessionDate.atTime(0, 1).atZone(zoneId);
        ZonedDateTime endZdt = sessionDate.atTime(23, 59).atZone(zoneId);

        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String bookingId2 = randomUUID().toString();
        String bookingId3 = randomUUID().toString();
        String bookingId4 = randomUUID().toString();
        String hearingId = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String hearingId4 = randomUUID().toString();
        LocalDate sessionDate = LocalDate.of(2025, 1, 3);
        final CourtSchedule courtScheduleWithSplit = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleWithSplit.setCourtScheduleId(courtScheduleId);
        courtScheduleWithSplit.setSlotBased(false);
        courtScheduleWithSplit.setMaxSlots(0);
        courtScheduleWithSplit.setMaxDuration(0);
        courtScheduleWithSplit.setAvailableSlots(0);
        courtScheduleWithSplit.setAvailableDuration(0);
        courtScheduleWithSplit.setMaxAdMorningDuration(100);
        courtScheduleWithSplit.setMaxAdAfternoonDuration(50);
        courtScheduleWithSplit.setCourtSession("AD");
        courtScheduleWithSplit.setSupportAdSplit(true);
        courtScheduleWithSplit.setPanel(PanelTypes.YOUTH.name());
        courtScheduleWithSplit.setOuCode("B40IM00");
        courtScheduleWithSplit.setSessionDate(sessionDate);
        courtScheduleWithSplit.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleWithSplit.getSessionDate(), "00:01"));
        courtScheduleWithSplit.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleWithSplit.getSessionDate(), "23:59"));
        databaseSeeder.insertCourtSchedule(courtScheduleWithSplit);
        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtScheduleWithSplit);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId, bookingId, "10:00", 20);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId2, bookingId2, "11:00", 30);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId3, bookingId3, "14:00", 20);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId4, bookingId4, "15:00", 10);

        String courtScheduleId2 = randomUUID().toString();
        String bookingId5 = randomUUID().toString();
        String bookingId6 = randomUUID().toString();
        String bookingId7 = randomUUID().toString();
        String bookingId8 = randomUUID().toString();
        String hearingId5 = randomUUID().toString();
        String hearingId6 = randomUUID().toString();
        String hearingId7 = randomUUID().toString();
        String hearingId8 = randomUUID().toString();

        final CourtSchedule courtScheduleWithoutSplit = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleWithoutSplit.setCourtScheduleId(courtScheduleId2);
        courtScheduleWithoutSplit.setSlotBased(false);
        courtScheduleWithoutSplit.setMaxSlots(0);
        courtScheduleWithoutSplit.setMaxDuration(100);
        courtScheduleWithoutSplit.setAvailableSlots(0);
        courtScheduleWithoutSplit.setAvailableDuration(20);
        courtScheduleWithoutSplit.setMaxAdMorningDuration(0);
        courtScheduleWithoutSplit.setMaxAdAfternoonDuration(0);
        courtScheduleWithoutSplit.setCourtSession("AD");
        courtScheduleWithoutSplit.setSupportAdSplit(false);
        courtScheduleWithoutSplit.setPanel(PanelTypes.YOUTH.name());
        courtScheduleWithoutSplit.setOuCode("B40IM00");
        courtScheduleWithoutSplit.setSessionDate(sessionDate);
        courtScheduleWithoutSplit.setSessionStartTime(DateUtils.combineDateAndTime(courtScheduleWithoutSplit.getSessionDate(), "00:01"));
        courtScheduleWithoutSplit.setSessionEndTime(DateUtils.combineDateAndTime(courtScheduleWithoutSplit.getSessionDate(), "23:59"));
        databaseSeeder.insertCourtSchedule(courtScheduleWithoutSplit);

        final CourtScheduleJudiciary courtScheduleJudiciaryWithoutSplit = createJudiciaryForSchedule(courtScheduleWithoutSplit);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryWithoutSplit);

        createAllocatedListingsAndInsert(courtScheduleId2, sessionDate, hearingId5, bookingId5, "10:00", 20);
        createAllocatedListingsAndInsert(courtScheduleId2, sessionDate, hearingId6, bookingId6, "11:00", 30);
        createAllocatedListingsAndInsert(courtScheduleId2, sessionDate, hearingId7, bookingId7, "14:00", 20);
        createAllocatedListingsAndInsert(courtScheduleId2, sessionDate, hearingId8, bookingId8, "15:00", 10);


        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtScheduleWithSplit.getSessionDate().minusDays(1);
        LocalDate toDate = courtScheduleWithSplit.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtScheduleWithSplit.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtScheduleWithSplit.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtScheduleWithSplit.getCourtSession());

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        //both courtScheduleId and courtScheduleId2 should be in hearingSlots array
        final JsonArray hearingSlotsJsonArray = jsonObject.getJsonArray("hearingSlots");
        assertThat(hearingSlotsJsonArray.size(), is(2));
        final List<String> courtScheduleIdsInResponsePayload = hearingSlotsJsonArray.stream()
                .map(hearingSlot -> ((JsonObject) hearingSlot).getString("courtScheduleId"))
                .toList();
        assertTrue(courtScheduleIdsInResponsePayload.contains(courtScheduleId));
        assertTrue(courtScheduleIdsInResponsePayload.contains(courtScheduleId2));
        final JsonArray slotStartTimesJsonArray = hearingSlotsJsonArray.getJsonObject(0).getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(24));
        slotStartTimesJsonArray.stream().forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(sessionDate + "T09:30:00.000Z")) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(sessionDate + "T10:00:00.000Z"));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(sessionDate + "T10:00:00.000Z")) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(20));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(sessionDate + "T11:00:00.000Z"));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(sessionDate + "T11:00:00.000Z")) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(30));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(sessionDate + "T12:00:00.000Z"));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(sessionDate + "T12:00:00.000Z")) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(sessionDate + "T13:00:00.000Z"));
            }
        });

    }

    @Test
    void shouldRetrieveHearingSlotForBothPanels() throws Exception {
        final String courtScheduleIdForYouth = randomUUID().toString();
        final String courtScheduleIdForAdult = randomUUID().toString();

        final String bookingId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final String ouCode = "B40IM00";
        final String courtSession = AM_SESSION;

        final LocalDate sessionDateForAdultPanelSession = LocalDate.of(2025, 1, 3);
        final LocalDate sessionDateForYouthPanelSession = LocalDate.of(2025, 1, 14);
        final CourtSchedule courtScheduleYouth = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleYouth.setCourtScheduleId(courtScheduleIdForYouth);
        courtScheduleYouth.setOuCode("");
        courtScheduleYouth.setCourtSession(courtSession);
        courtScheduleYouth.setPanel(PanelTypes.YOUTH.name());
        courtScheduleYouth.setSessionDate(sessionDateForYouthPanelSession);
        courtScheduleYouth.setOuCode(ouCode);
        databaseSeeder.insertCourtSchedule(courtScheduleYouth);

        final CourtSchedule courtScheduleAdult = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleAdult.setCourtScheduleId(courtScheduleIdForAdult);
        courtScheduleAdult.setCourtSession(courtSession);
        courtScheduleAdult.setPanel(PanelTypes.ADULT.name());
        courtScheduleAdult.setSessionDate(sessionDateForAdultPanelSession);
        courtScheduleAdult.setOuCode(ouCode);
        databaseSeeder.insertCourtSchedule(courtScheduleAdult);

        final CourtScheduleJudiciary courtScheduleJudiciaryForYouth = createJudiciaryForSchedule(courtScheduleYouth);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryForYouth);
        final CourtScheduleJudiciary courtScheduleJudiciaryForAdult = createJudiciaryForSchedule(courtScheduleAdult);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryForAdult);

        createAllocatedListingsAndInsert(courtScheduleIdForYouth, hearingId, bookingId);
        createAllocatedListingsAndInsert(courtScheduleIdForAdult, hearingId, bookingId);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        final LocalDate fromDate = sessionDateForAdultPanelSession.minusDays(1);
        final LocalDate toDate = sessionDateForYouthPanelSession.plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", "ADULT,YOUTH");
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", ouCode);
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);

        final Map<String, Object> requestParamMap = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonArray hearingSlotsJsonArray = jsonObject.getJsonArray("hearingSlots");
        assertThat(hearingSlotsJsonArray.size(), is(2));
        final List<String> courtScheduleIdsInResponsePayload = hearingSlotsJsonArray.stream()
                .map(hearingSlot -> ((JsonObject) hearingSlot).getString("courtScheduleId"))
                .toList();
        assertTrue(courtScheduleIdsInResponsePayload.contains(courtScheduleIdForYouth));
        assertTrue(courtScheduleIdsInResponsePayload.contains(courtScheduleIdForAdult));
    }

    @Test
    void shouldRetrieveHearingSlotForDifferentCourtSessions() throws Exception {
        final String courtScheduleIdForAM = randomUUID().toString();
        final String courtScheduleIdForPM = randomUUID().toString();
        final String courtScheduleIdForAD = randomUUID().toString();

        final String bookingId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final String ouCode = "B40IM00";

        final LocalDate sessionDateForPMSession = LocalDate.now().plusDays(1);
        final LocalDate sessionDateForAMSession = LocalDate.now().plusDays(5);
        final LocalDate sessionDateForADSession = LocalDate.now().plusDays(3);
        final CourtSchedule courtScheduleAMSession = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleAMSession.setCourtScheduleId(courtScheduleIdForAM);
        courtScheduleAMSession.setCourtSession(AM_SESSION);
        courtScheduleAMSession.setPanel(PanelTypes.YOUTH.name());
        courtScheduleAMSession.setSessionDate(sessionDateForAMSession);
        courtScheduleAMSession.setOuCode(ouCode);
        databaseSeeder.insertCourtSchedule(courtScheduleAMSession);

        final CourtSchedule courtSchedulePMSession = RANDOM.nextObject(CourtSchedule.class);
        courtSchedulePMSession.setCourtScheduleId(courtScheduleIdForPM);
        courtSchedulePMSession.setCourtSession(PM_SESSION);
        courtSchedulePMSession.setPanel(PanelTypes.ADULT.name());
        courtSchedulePMSession.setSessionDate(sessionDateForPMSession);
        courtSchedulePMSession.setOuCode(ouCode);
        databaseSeeder.insertCourtSchedule(courtSchedulePMSession);

        final CourtSchedule courtScheduleADSession = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleADSession.setCourtScheduleId(courtScheduleIdForAD);
        courtScheduleADSession.setCourtSession(ALL_DAY);
        courtScheduleADSession.setPanel(PanelTypes.ADULT.name());
        courtScheduleADSession.setSessionDate(sessionDateForADSession);
        courtScheduleADSession.setOuCode(ouCode);
        databaseSeeder.insertCourtSchedule(courtScheduleADSession);

        final CourtScheduleJudiciary courtScheduleJudiciaryForAM = createJudiciaryForSchedule(courtScheduleAMSession);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryForAM);
        final CourtScheduleJudiciary courtScheduleJudiciaryForPM = createJudiciaryForSchedule(courtSchedulePMSession);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryForPM);
        final CourtScheduleJudiciary courtScheduleJudiciaryForAD = createJudiciaryForSchedule(courtScheduleADSession);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryForAD);

        createAllocatedListingsAndInsert(courtScheduleIdForAM, hearingId, bookingId);
        createAllocatedListingsAndInsert(courtScheduleIdForPM, hearingId, bookingId);
        createAllocatedListingsAndInsert(courtScheduleIdForAD, hearingId, bookingId);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        final LocalDate fromDate = sessionDateForPMSession.minusDays(1);
        final LocalDate toDate = sessionDateForAMSession.plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", "ADULT,YOUTH");
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", "AM,PM,AD");
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", ouCode);
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());

        final Map<String, Object> requestParamMap = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonArray hearingSlotsJsonArray = jsonObject.getJsonArray("hearingSlots");
        assertThat(hearingSlotsJsonArray.size(), is(3));
        final List<String> courtScheduleIdsInResponsePayload = hearingSlotsJsonArray.stream()
                        .map(hearingSlot -> ((JsonObject) hearingSlot).getString("courtScheduleId"))
                                .toList();
        assertTrue(courtScheduleIdsInResponsePayload.contains(courtScheduleIdForAM));
        assertTrue(courtScheduleIdsInResponsePayload.contains(courtScheduleIdForPM));
        assertTrue(courtScheduleIdsInResponsePayload.contains(courtScheduleIdForAD));
    }


    private void createAllocatedListingsAndInsert(final String courtScheduleId,final LocalDate sessionDate, final String hearingId, final String bookingId, final String time, final Integer duration) throws SQLException {
        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setDuration(duration);
        allocatedListing.setHearingStartTime(DateUtils.combineDateAndTime(sessionDate, time));
        databaseSeeder.insertAllocatedListing(allocatedListing);
    }

    private void createAllocatedListingsAndInsert(final String courtScheduleIdForAM, final String hearingId, final String bookingId) throws SQLException {
        final AllocatedListing allocatedListingForAMSession = RANDOM.nextObject(AllocatedListing.class);
        allocatedListingForAMSession.setCourtScheduleId(courtScheduleIdForAM);
        allocatedListingForAMSession.setHearingId(hearingId);
        allocatedListingForAMSession.setBookingId(bookingId);
        databaseSeeder.insertAllocatedListing(allocatedListingForAMSession);
    }

    private static CourtScheduleJudiciary createJudiciaryForSchedule(final CourtSchedule courtSchedule) {
        final CourtScheduleJudiciary courtScheduleJudiciaryForYouth = random(CourtScheduleJudiciary.class);
        final CourtScheduleJudiciaryKey courtScheduleJudiciaryKey = random(CourtScheduleJudiciaryKey.class);
        courtScheduleJudiciaryKey.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        courtScheduleJudiciaryKey.setJudiciaryId(courtSchedule.getCourtScheduleId());
        courtScheduleJudiciaryForYouth.setId(courtScheduleJudiciaryKey);
        courtScheduleJudiciaryForYouth.setCourtListingProfileId(courtScheduleJudiciaryForYouth.getCourtListingProfileId());
        return courtScheduleJudiciaryForYouth;
    }

    @Test
    void shouldRemoveHearingSlot() throws Exception {
        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String hearingId = randomUUID().toString();
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


        final Response response = deleteCommand(format("%s/%s", RELATIVE_URL, hearingId), "application/vnd.courtscheduler.remove.hearing.slots+json", SYSTEM_USER_ID);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }
}
