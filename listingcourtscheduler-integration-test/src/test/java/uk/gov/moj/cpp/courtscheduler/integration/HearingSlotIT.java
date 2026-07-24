package uk.gov.moj.cpp.courtscheduler.integration;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.lang.String.format;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.json.Json.createReader;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.RestPoller.poll;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.*;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.integration.utils.RequestParams;
import uk.gov.moj.cpp.courtscheduler.integration.utils.ResponseData;
import uk.gov.moj.cpp.courtscheduler.domain.rota.PanelTypes;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import java.io.StringReader;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HearingSlotIT extends AbstractIT {

    private static final String RELATIVE_URL = "/hearingslots";

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldUpdateHearingSlot() throws SQLException {
        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String hearingId = randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
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
        String respPayload = response.readEntity(String.class);
        JsonObject jsonObject = createReader(new StringReader(respPayload)).readObject();
        assertThat(jsonObject.getJsonArray("schedules").size(), is(0));
    }

    @Test
    void shouldListHearingSlotsWhenRequestedHearingTimeOutsideCourtScheduleSessionTime() throws Exception {

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
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setCourtSession("AD");
        courtSchedule.setMaxDuration(180);
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "17:00"));
        courtSchedule.setIsOverbookingAllowed(true);
        courtSchedule.setTotalBookedMorning(0);
        databaseSeeder.insertCourtSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule));

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
        courtSchedule2.setSupportAdSplit(false);
        courtSchedule2.setCourtSession("AD");
        courtSchedule2.setMaxDuration(180);
        courtSchedule2.setSessionDate(courtSchedule.getSessionDate().plusDays(2));
        courtSchedule2.setSessionStartTime(combineDateAndTime(courtSchedule2.getSessionDate(), "10:00"));
        courtSchedule2.setSessionEndTime(combineDateAndTime(courtSchedule2.getSessionDate(), "17:00"));
        courtSchedule2.setIsOverbookingAllowed(true);
        courtSchedule2.setTotalBookedMorning(0);
        databaseSeeder.insertCourtSchedule(courtSchedule2);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule2));

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
        courtSchedule3.setSupportAdSplit(false);
        courtSchedule3.setCourtSession("AD");
        courtSchedule3.setMaxDuration(360);
        courtSchedule3.setSessionDate(courtSchedule.getSessionDate().plusDays(31));
        courtSchedule3.setSessionStartTime(combineDateAndTime(courtSchedule3.getSessionDate(), "10:00"));
        courtSchedule3.setSessionEndTime(combineDateAndTime(courtSchedule3.getSessionDate(), "17:00"));
        courtSchedule3.setIsOverbookingAllowed(true);
        courtSchedule3.setTotalBookedMorning(0);
        databaseSeeder.insertCourtSchedule(courtSchedule3);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule3));

        String updateHearingSlotsPayload = getPayload("courtscheduler.list.hearings-in-court-sessions.json");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_ID_1", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("COURT_SCHEDULE_ID_1_1", "1771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_START_TIME_1_1", toLocalDateTimeString(courtSchedule.getSessionDate().minusDays(1).atTime(10,0)));
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("\"DURATION_1_1\"", "20");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_ID_2", "6771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("COURT_SCHEDULE_ID_2_1", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_START_TIME_2_1", toLocalDateTimeString(courtSchedule2.getSessionDate().minusDays(1).atTime(11,0)));
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("COURT_SCHEDULE_ID_2_2", "2771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("\"DURATION_2_2\"", "30");

        final Response response = putCommand("/list/hearingslots", "application/vnd.courtscheduler.list.hearings-in-court-sessions+json", SYSTEM_USER_ID, updateHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        JsonArray jsonArray = jsonObject.getJsonArray("hearings");
        assertThat(jsonArray.size(), is(3));
        jsonArray.forEach( hearing ->
                {final JsonObject hearingJson = (JsonObject) hearing ;
                    if (hearingJson.getString("courtScheduleId").equals("1771a96b-1c5a-45d1-b647-1bec5212cafc")) {
                        assertThat(hearingJson.getString("hearingId"), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
                        assertThat(hearingJson.getString("hearingStartTime"), is(toResponseDateString(courtSchedule.getSessionStartTime())));
                        assertThat(hearingJson.getInt("duration"), is(20));
                        JsonArray judiciaries = hearingJson.getJsonArray("judiciaries");
                        assertThat(judiciaries.size(), is(1));
                        judiciaries.forEach(courtScheduleJudiciary ->
                                {
                                    final JsonObject scheduleJudiciary = (JsonObject) courtScheduleJudiciary;
                                    assertThat(scheduleJudiciary.getString("judiciaryId"), is("1771a96b-1c5a-45d1-b647-1bec5212cafc"));
                                }
                        );
                    }
                    if (hearingJson.getString("courtScheduleId").equals("5771a96b-1c5a-45d1-b647-1bec5212cafc")) {
                        assertThat(hearingJson.getString("hearingId"), is("6771a96b-1c5a-45d1-b647-1bec5212cafc"));
                        assertThat(hearingJson.getString("hearingStartTime"), is(toResponseDateString(courtSchedule2.getSessionStartTime())));
                        assertThat(hearingJson.getInt("duration"), is(20));
                        JsonArray judiciaries = hearingJson.getJsonArray("judiciaries");
                        assertThat(judiciaries.size(), is(1));
                        judiciaries.forEach(courtScheduleJudiciary ->
                                {
                                    final JsonObject scheduleJudiciary = (JsonObject) courtScheduleJudiciary;
                                    assertThat(scheduleJudiciary.getString("judiciaryId"), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
                                }
                        );
                    }
                    if (hearingJson.getString("courtScheduleId").equals("2771a96b-1c5a-45d1-b647-1bec5212cafc")) {
                        assertThat(hearingJson.getString("hearingId"), is("6771a96b-1c5a-45d1-b647-1bec5212cafc"));
//                        assertThat(hearingJson.getInt("duration"), is(1));
                        assertThat(hearingJson.getString("hearingStartTime"), is(toResponseDateString(courtSchedule3.getSessionStartTime())));
                        assertThat(hearingJson.getInt("duration"), is(30));
                        JsonArray judiciaries = hearingJson.getJsonArray("judiciaries");
                        assertThat(judiciaries.size(), is(1));
                        judiciaries.forEach(courtScheduleJudiciary ->
                                {
                                    final JsonObject scheduleJudiciary = (JsonObject) courtScheduleJudiciary;
                                    assertThat(scheduleJudiciary.getString("judiciaryId"), is("2771a96b-1c5a-45d1-b647-1bec5212cafc"));
                                }
                        );
                    }
                }
        );
    }

    @Test
    void shouldRetrieveHearingSlot() throws Exception {
        final String courtSession = AM_SESSION;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();

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
        courtSchedule.setIsDraft(false);
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(9);
        courtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());
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

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject) jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        // Wire-contract pin: cross-context consumers (cpp-context-listing, cpp-apitests) read the
        // legacy bean-convention names — these flags must not serialize as isDraft/isOverbookingAllowed.
        assertThat(hearingSlotJsonObject.containsKey("draft"), is(true));
        assertThat(hearingSlotJsonObject.containsKey("overbookingAllowed"), is(true));
        assertThat(hearingSlotJsonObject.containsKey("isDraft"), is(false));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(4));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),9,30)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),10,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),10,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(2));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),11,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),11,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),12,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),12,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),12,30))));
            }
        });
    }

    @Test
    void shouldRetrieveHearingSlotWhenMaxSlotReachedButOverbookingAllowedForSlotBased() throws Exception {
        final String courtSession = AM_SESSION;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();
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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        courtSchedule.setIsOverbookingAllowed(true);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(0);
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
    }

    @Test
    void shouldRetrieveHearingSlotForDurationBased() throws Exception {
        final String courtSession = AM_SESSION;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();
        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String hearingId = randomUUID().toString();

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCourtSession(courtSession);
        courtSchedule.setPanel(PanelTypes.YOUTH.name());
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(false);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxDuration(120);
        courtSchedule.setAvailableDuration(60);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId, bookingId, "10:00", 60);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots_with_duration.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("DURATION", "60");

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject)jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
    }

    @Test
    void shouldRetrieveHearingSlotMatchingHearingStartTimeInRequest() throws Exception {
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
        courtSchedule.setIsDraft(false);
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        courtSchedule.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId, bookingId, "10:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId2, bookingId2, "10:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId3, bookingId3, "11:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId4, bookingId4, "12:00", 1);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.with-hearingstarttime.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);
        String hearingStartTime = "2025-01-03T09:30:00.000Z";

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("HEARING_START_TIME", hearingStartTime);

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
    }

    @Test
    void shouldReturnBadRequestForRetrieveHearingSlotWithInvalidHearingStartTime() throws Exception {
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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId, bookingId, "10:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId2, bookingId2, "10:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId3, bookingId3, "11:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId4, bookingId4, "12:00", 1);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.with-hearingstarttime.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);
        String hearingStartTime = "2025-01-03 09:30:00";

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("HEARING_START_TIME", hearingStartTime);

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(BAD_REQUEST.getStatusCode()));
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
        courtSchedule.setSessionDate(getRandomFutureDateWithinNextYear());
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "17:30"));
        courtSchedule.setNationalBreakTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
        courtSchedule.setIsOverbookingAllowed(true);
        courtSchedule.setSupportAdSplit(false);
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

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject) jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(7));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
        });
    }

    @Test
    void shouldRetrieveAllDaySplitWithBookings() throws Exception {

        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String bookingId2 = randomUUID().toString();
        String bookingId3 = randomUUID().toString();
        String bookingId4 = randomUUID().toString();
        String hearingId = randomUUID().toString();
        String hearingId2 = randomUUID().toString();
        String hearingId3 = randomUUID().toString();
        String hearingId4 = randomUUID().toString();
        LocalDate sessionDate = getRandomFutureDateWithinNextYear();
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
        courtScheduleWithSplit.setSessionStartTime(combineDateAndTime(courtScheduleWithSplit.getSessionDate(), "00:01"));
        courtScheduleWithSplit.setSessionEndTime(combineDateAndTime(courtScheduleWithSplit.getSessionDate(), "23:59"));
        courtScheduleWithSplit.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
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
        courtScheduleWithoutSplit.setSessionStartTime(combineDateAndTime(courtScheduleWithoutSplit.getSessionDate(), "00:01"));
        courtScheduleWithoutSplit.setSessionEndTime(combineDateAndTime(courtScheduleWithoutSplit.getSessionDate(), "23:59"));
        courtScheduleWithoutSplit.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        databaseSeeder.insertCourtSchedule(courtScheduleWithoutSplit);

        final CourtScheduleJudiciary courtScheduleJudiciaryWithoutSplit = createJudiciaryForSchedule(courtScheduleWithoutSplit);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryWithoutSplit);

        createAllocatedListingsAndInsert(courtScheduleId2, sessionDate, hearingId5, bookingId5, "00:01", 20);
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
        JsonObject hearingSlotWithSplit = null;
        JsonObject hearingSlotWithoutSplit = null;
        for (JsonValue value : hearingSlotsJsonArray) {
            if (value.getValueType() == JsonValue.ValueType.OBJECT) {
                JsonObject obj = value.asJsonObject();
                if (obj.getString("courtScheduleId").equals(courtScheduleId)) {
                    hearingSlotWithSplit = obj;
                } else if (obj.getString("courtScheduleId").equals(courtScheduleId2)) {
                    hearingSlotWithoutSplit = obj;
                }
            }
        }

        final JsonArray slotStartTimesJsonArrayWithoutSplit = hearingSlotWithoutSplit.getJsonArray("slotStartTimes");
        final JsonArray slotStartTimesJsonArrayWithSplit = hearingSlotWithSplit.getJsonArray("slotStartTimes");


        assertThat(slotStartTimesJsonArrayWithoutSplit.size(), is(23));
        assertThat(slotStartTimesJsonArrayWithSplit.size(), is(2));

        slotStartTimesJsonArrayWithoutSplit.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),0,1)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(20));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),1,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),11,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(30));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),12,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),14,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(20));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),15,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),15,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(10));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),16,0))));
            }
        });

        slotStartTimesJsonArrayWithSplit.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),0,1)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(50));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),13,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),14,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(30));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),23,59))));
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

        final LocalDate sessionDateForAdultPanelSession = getRandomFutureDateWithinNextYear();
        final LocalDate sessionDateForYouthPanelSession = sessionDateForAdultPanelSession.plusDays(11);
        final CourtSchedule courtScheduleYouth = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleYouth.setCourtScheduleId(courtScheduleIdForYouth);
        courtScheduleYouth.setOuCode("");
        courtScheduleYouth.setCourtSession(courtSession);
        courtScheduleYouth.setPanel(PanelTypes.YOUTH.name());
        courtScheduleYouth.setSessionDate(sessionDateForYouthPanelSession);
        courtScheduleYouth.setOuCode(ouCode);
        courtScheduleYouth.setIsOverbookingAllowed(true);
        courtScheduleYouth.setMaxSlots(RANDOM.nextInt(100));
        courtScheduleYouth.setAvailableSlots(RANDOM.nextInt(100));
        courtScheduleYouth.setSlotBased(true);
        databaseSeeder.insertCourtSchedule(courtScheduleYouth);

        final CourtSchedule courtScheduleAdult = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleAdult.setCourtScheduleId(courtScheduleIdForAdult);
        courtScheduleAdult.setCourtSession(courtSession);
        courtScheduleAdult.setPanel(PanelTypes.ADULT.name());
        courtScheduleAdult.setSessionDate(sessionDateForAdultPanelSession);
        courtScheduleAdult.setOuCode(ouCode);
        courtScheduleAdult.setIsOverbookingAllowed(true);
        courtScheduleAdult.setMaxSlots(RANDOM.nextInt(100));
        courtScheduleAdult.setAvailableSlots(RANDOM.nextInt(100));
        courtScheduleAdult.setSlotBased(true);
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

        final Map<String, Object> requestParamMap = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

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
        courtScheduleAMSession.setIsOverbookingAllowed(true);
        courtScheduleAMSession.setIsDraft(false);
        courtScheduleAMSession.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(courtScheduleAMSession);

        final CourtSchedule courtSchedulePMSession = RANDOM.nextObject(CourtSchedule.class);
        courtSchedulePMSession.setCourtScheduleId(courtScheduleIdForPM);
        courtSchedulePMSession.setCourtSession(PM_SESSION);
        courtSchedulePMSession.setPanel(PanelTypes.ADULT.name());
        courtSchedulePMSession.setSessionDate(sessionDateForPMSession);
        courtSchedulePMSession.setOuCode(ouCode);
        courtSchedulePMSession.setIsOverbookingAllowed(true);
        courtSchedulePMSession.setIsDraft(false);
        courtSchedulePMSession.setJurisdiction(MAGISTRATES.getJurisdiction());
        databaseSeeder.insertCourtSchedule(courtSchedulePMSession);

        final CourtSchedule courtScheduleADSession = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleADSession.setCourtScheduleId(courtScheduleIdForAD);
        courtScheduleADSession.setCourtSession(ALL_DAY);
        courtScheduleADSession.setPanel(PanelTypes.ADULT.name());
        courtScheduleADSession.setSessionDate(sessionDateForADSession);
        courtScheduleADSession.setOuCode(ouCode);
        courtScheduleADSession.setIsOverbookingAllowed(true);
        courtScheduleADSession.setIsDraft(false);
        courtScheduleADSession.setJurisdiction(MAGISTRATES.getJurisdiction());
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

        final Map<String, Object> requestParamMap = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

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

    @Test
    void shouldRemoveHearingSlot() throws Exception {
        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String hearingId = randomUUID().toString();
        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
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

    @Test
    void shouldSearchAndBookHearingSlotForPolice() throws Exception {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomNumber(1501);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setBusinessType("YFL");
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxAdMorningDuration(180);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule));

        CourtSchedule courtSchedule2 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId("5771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule2.setOuCode("B40IM00");
        courtSchedule2.setCourtRoomNumber(1501);
        courtSchedule2.setBusinessType("NGAP");
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
        courtSchedule2.setMaxDuration(360);
        courtSchedule2.setSessionDate(LocalDate.of(2025, 4, 5));
        courtSchedule2.setSessionStartTime(combineDateAndTime(courtSchedule2.getSessionDate(), "10:00"));
        courtSchedule2.setSessionEndTime(combineDateAndTime(courtSchedule2.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule2);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule2));

        CourtSchedule courtSchedule3 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule3.setCourtScheduleId("2771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule3.setOuCode("B40IM00");
        courtSchedule3.setCourtRoomNumber(1501);
        courtSchedule3.setBusinessType("GAP");
        courtSchedule3.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule3.setCourtRoomId("87b6ea2a-9d81-3a47-884d-306419431065");
        courtSchedule3.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule3.setPanel(PanelTypes.YOUTH.name());
        courtSchedule3.setSlotBased(true);
        courtSchedule3.setMaxSlots(0);
        courtSchedule3.setSupportAdSplit(true);
        courtSchedule3.setCourtSession("PM");
        courtSchedule3.setMaxAdMorningDuration(180);
        courtSchedule3.setMaxAdAfternoonDuration(180);
        courtSchedule3.setMaxDuration(360);
        courtSchedule3.setSessionDate(LocalDate.of(2025, 5, 5));
        courtSchedule3.setSessionStartTime(combineDateAndTime(courtSchedule3.getSessionDate(), "14:00"));
        courtSchedule3.setSessionEndTime(combineDateAndTime(courtSchedule3.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule3);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule3));

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots_For_Police.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T11:00:00.000Z");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("IS_POLICE", "true");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertThat(hearingSlots.get("hearingId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtScheduleId").toString().replaceAll("^\"|\"$", ""), is("1771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtRoomId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafb"));
        assertThat(hearingSlots.get("hearingStartTime").toString().replaceAll("^\"|\"$", ""), is("2025-05-13T11:00:00Z"));
        assertThat(hearingSlots.get("duration").toString().replaceAll("^\"|\"$", ""), is("20"));
        assertThat(hearingSlots.get("duration").toString().replaceAll("^\"|\"$", ""), is("20"));
        JsonArray jsonArray = hearingSlots.getJsonArray("judiciaries");
        assertThat(jsonArray.size(), is(1));
        jsonArray.forEach(courtScheduleJudiciary ->
                {
                    final JsonObject scheduleJudiciary = (JsonObject) courtScheduleJudiciary;
                    assertThat(scheduleJudiciary.getString("judiciaryId"), is("1771a96b-1c5a-45d1-b647-1bec5212cafc"));
                }
        );
    }

    @Test
    void shouldReturnBadRequestWithMissingDurationInSearchAndBookHearingSlot() throws SQLException, JsonProcessingException {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomNumber(1501);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setBusinessType("ENF");
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxAdMorningDuration(120);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T10:00:00Z");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(BAD_REQUEST.getStatusCode()));
    }

    @Test
    void shouldSearchAndBookHearingSlotForNonPolice() throws SQLException, JsonProcessingException {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomNumber(1501);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setBusinessType("ENF");
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxAdMorningDuration(120);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setSessionDate(LocalDate.of(2025, 5, 14));
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
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
        courtSchedule2.setSessionStartTime(combineDateAndTime(courtSchedule2.getSessionDate(), "10:00"));
        courtSchedule2.setSessionEndTime(combineDateAndTime(courtSchedule2.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        CourtSchedule courtSchedule3 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule3.setCourtScheduleId("2771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule3.setOuCode("B01LY00");
        courtSchedule3.setCourtRoomNumber(1501);
        courtSchedule3.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule3.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule3.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule3.setPanel(PanelTypes.ADULT.name());
        courtSchedule3.setSlotBased(true);
        courtSchedule3.setMaxSlots(0);
        courtSchedule3.setBusinessType("NCFL");
        courtSchedule3.setSupportAdSplit(true);
        courtSchedule3.setCourtSession("PM");
        courtSchedule3.setMaxAdMorningDuration(180);
        courtSchedule3.setMaxAdAfternoonDuration(180);
        courtSchedule3.setMaxDuration(360);
        courtSchedule3.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule3.setSessionStartTime(combineDateAndTime(courtSchedule3.getSessionDate(), "14:00"));
        courtSchedule3.setSessionEndTime(combineDateAndTime(courtSchedule3.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule3);

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T15:00:00.000Z");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertThat(hearingSlots.get("hearingId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtScheduleId").toString().replaceAll("^\"|\"$", ""), is("2771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtRoomId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafb"));
        assertThat(hearingSlots.get("hearingStartTime").toString().replaceAll("^\"|\"$", ""), is("2025-05-13T15:00:00Z"));
        assertThat(hearingSlots.get("duration").toString().replaceAll("^\"|\"$", ""), is("20"));
    }

    @Test
    void shouldNotReturnCourtScheduleForNonPoliceIfCourtRoomNotAvailable() throws SQLException, JsonProcessingException {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomNumber(1601);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("6771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setBusinessType("ENF");
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxAdMorningDuration(120);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T09:00:00Z");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertEquals("{\"hearingSlots\":{}}", jsonObject.toString());
        assertFalse(hearingSlots.containsKey("CourtScheduleId"));
    }

    @Test
    void shouldSearchAndBookHearingSlotForNonPolice_WithoutAdjustingHearingStarttime_ShouldReturnEmptyResponse() throws SQLException, JsonProcessingException {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomNumber(1501);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setBusinessType("ENF");
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxAdMorningDuration(120);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setSessionDate(LocalDate.of(2025, 5, 14));
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
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
        courtSchedule2.setSessionStartTime(combineDateAndTime(courtSchedule2.getSessionDate(), "10:00"));
        courtSchedule2.setSessionEndTime(combineDateAndTime(courtSchedule2.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        CourtSchedule courtSchedule3 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule3.setCourtScheduleId("2771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule3.setOuCode("B01LY00");
        courtSchedule3.setCourtRoomNumber(1501);
        courtSchedule3.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule3.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule3.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule3.setPanel(PanelTypes.ADULT.name());
        courtSchedule3.setSlotBased(true);
        courtSchedule3.setBusinessType("NCFL");
        courtSchedule3.setMaxSlots(0);
        courtSchedule3.setSupportAdSplit(true);
        courtSchedule3.setCourtSession("PM");
        courtSchedule3.setMaxAdMorningDuration(180);
        courtSchedule3.setMaxAdAfternoonDuration(180);
        courtSchedule3.setMaxDuration(360);
        courtSchedule3.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule3.setSessionStartTime(combineDateAndTime(courtSchedule3.getSessionDate(), "14:00"));
        courtSchedule3.setSessionEndTime(combineDateAndTime(courtSchedule3.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule3);

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T09:00:00Z");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertEquals("{\"hearingSlots\":{}}", jsonObject.toString());
        assertTrue(hearingSlots.isEmpty());
    }

    @Test
    void shouldSearchAndBookHearingSlotForNonPolice_WithNoNCFLBusinessType_ShouldReturnEmptyResponse() throws SQLException, JsonProcessingException {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomNumber(1501);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setBusinessType("REM");
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxAdMorningDuration(120);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setSessionDate(LocalDate.of(2025, 5, 14));
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T09:00:00Z");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertTrue(hearingSlots.isEmpty());
    }

    @Test
    void shouldSearchAndBookHearingSlotDurationBasedForNonPolice() throws SQLException, JsonProcessingException {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomNumber(1501);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setBusinessType("NCFL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxSlots(0);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxAdMorningDuration(120);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setMaxDuration(120);
        courtSchedule.setAvailableDuration(0);
        courtSchedule.setSessionDate(LocalDate.of(2025, 5, 14));
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
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
        courtSchedule2.setSessionStartTime(combineDateAndTime(courtSchedule2.getSessionDate(), "10:00"));
        courtSchedule2.setSessionEndTime(combineDateAndTime(courtSchedule2.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        CourtSchedule courtSchedule3 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule3.setCourtScheduleId("2771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule3.setOuCode("B01LY00");
        courtSchedule3.setCourtRoomNumber(1501);
        courtSchedule3.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule3.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule3.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule3.setPanel(PanelTypes.ADULT.name());
        courtSchedule3.setSlotBased(true);
        courtSchedule3.setMaxSlots(0);
        courtSchedule3.setSupportAdSplit(true);
        courtSchedule3.setCourtSession("PM");
        courtSchedule3.setMaxAdMorningDuration(180);
        courtSchedule3.setMaxAdAfternoonDuration(180);
        courtSchedule3.setMaxDuration(360);
        courtSchedule3.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule3.setSessionStartTime(combineDateAndTime(courtSchedule3.getSessionDate(), "14:00"));
        courtSchedule3.setSessionEndTime(combineDateAndTime(courtSchedule3.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule3);

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-14");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-14T10:00:00.000Z");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertThat(hearingSlots.get("hearingId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtScheduleId").toString().replaceAll("^\"|\"$", ""), is("1771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtRoomId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafb"));
        assertThat(hearingSlots.get("hearingStartTime").toString().replaceAll("^\"|\"$", ""), is("2025-05-14T10:00:00Z"));
        assertThat(hearingSlots.get("duration").toString().replaceAll("^\"|\"$", ""), is("20"));
    }

    @Test
    void shouldSearchAndBookHearingSlotByBusinessTypePreferenceOrderForPolice() throws SQLException, JsonProcessingException {

        CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomNumber(1501);
        courtSchedule.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule.setPanel(PanelTypes.ADULT.name());
        courtSchedule.setBusinessType("DAFL");
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setSupportAdSplit(false);
        courtSchedule.setCourtSession("AM");
        courtSchedule.setMaxAdMorningDuration(180);
        courtSchedule.setMaxAdAfternoonDuration(0);
        courtSchedule.setMaxDuration(180);
        courtSchedule.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        CourtSchedule courtSchedule2 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId("2771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule2.setOuCode("B01LY00");
        courtSchedule2.setCourtRoomNumber(1501);
        courtSchedule2.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule2.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule2.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule2.setPanel(PanelTypes.ADULT.name());
        courtSchedule2.setBusinessType("REM");
        courtSchedule2.setSlotBased(false);
        courtSchedule2.setMaxSlots(2);
        courtSchedule2.setSupportAdSplit(false);
        courtSchedule2.setCourtSession("AM");
        courtSchedule2.setMaxAdMorningDuration(180);
        courtSchedule2.setMaxAdAfternoonDuration(0);
        courtSchedule2.setMaxDuration(180);
        courtSchedule2.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule2.setSessionStartTime(combineDateAndTime(courtSchedule2.getSessionDate(), "10:00"));
        courtSchedule2.setSessionEndTime(combineDateAndTime(courtSchedule2.getSessionDate(), "13:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        CourtSchedule courtSchedule3 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule3.setCourtScheduleId("3771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule3.setOuCode("B01LY00");
        courtSchedule3.setCourtRoomNumber(1501);
        courtSchedule3.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule3.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule3.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule3.setPanel(PanelTypes.YOUTH.name());
        courtSchedule3.setBusinessType("NGAP");
        courtSchedule3.setSlotBased(false);
        courtSchedule3.setMaxSlots(0);
        courtSchedule3.setSupportAdSplit(false);
        courtSchedule3.setCourtSession("AM");
        courtSchedule3.setMaxAdMorningDuration(180);
        courtSchedule3.setMaxAdAfternoonDuration(0);
        courtSchedule3.setMaxDuration(180);
        courtSchedule3.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule3.setSessionStartTime(combineDateAndTime(courtSchedule3.getSessionDate(), "10:00"));
        courtSchedule3.setSessionEndTime(combineDateAndTime(courtSchedule3.getSessionDate(), "13:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule3);

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots_For_Police.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T10:00:00.000Z");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("IS_POLICE", "true");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertThat(hearingSlots.get("hearingId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtScheduleId").toString().replaceAll("^\"|\"$", ""), is("1771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtRoomId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafb"));
        assertThat(hearingSlots.get("hearingStartTime").toString().replaceAll("^\"|\"$", ""), is("2025-05-13T10:00:00Z"));
        assertThat(hearingSlots.get("duration").toString().replaceAll("^\"|\"$", ""), is("20"));
    }

    @Test
    void shouldRetrieveHearingSlotForAllDayWithSplitSupport() throws Exception {
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();
        String courtScheduleId = randomUUID().toString();


        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCourtSession(AM_SESSION);
        courtSchedule.setPanel(PanelTypes.YOUTH.name());
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(180);
        courtSchedule.setAvailableDuration(180);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        String courtScheduleId2 = randomUUID().toString();


        final CourtSchedule courtSchedule2 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId(courtScheduleId2);
        courtSchedule2.setCourtSession(PM_SESSION);
        courtSchedule2.setPanel(PanelTypes.YOUTH.name());
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setOuCode("B40IM00");
        courtSchedule2.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "14:00"));
        courtSchedule2.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "17:00"));
        courtSchedule2.setIsOverbookingAllowed(false);
        courtSchedule2.setSlotBased(false);
        courtSchedule2.setMaxDuration(180);
        courtSchedule2.setAvailableDuration(180);
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        final CourtScheduleJudiciary courtScheduleJudiciary2 = createJudiciaryForSchedule(courtSchedule2);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary2);

        String courtScheduleId3 = randomUUID().toString();

        final CourtSchedule courtScheduleWithSplit = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleWithSplit.setCourtScheduleId(courtScheduleId3);
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
        courtScheduleWithSplit.setSessionStartTime(combineDateAndTime(courtScheduleWithSplit.getSessionDate(), "10:00"));
        courtScheduleWithSplit.setSessionEndTime(combineDateAndTime(courtScheduleWithSplit.getSessionDate(), "17:00"));
        courtScheduleWithSplit.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));

        databaseSeeder.insertCourtSchedule(courtScheduleWithSplit);
        final CourtScheduleJudiciary courtScheduleJudiciaryAD = createJudiciaryForSchedule(courtScheduleWithSplit);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryAD);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", "AD");

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject)jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtScheduleWithSplit.getCourtScheduleId()));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(2));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),10,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),13,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),14,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),17,0))));
            }
        });
    }

    @Test
    void shouldRetrieveHearingSlotForAllDayWithoutSplitSupport() throws Exception {
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();
        String courtScheduleId = randomUUID().toString();

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCourtSession(AM_SESSION);
        courtSchedule.setPanel(PanelTypes.YOUTH.name());
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(false);
        courtSchedule.setMaxDuration(180);
        courtSchedule.setAvailableDuration(180);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        String courtScheduleId2 = randomUUID().toString();


        final CourtSchedule courtSchedule2 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId(courtScheduleId2);
        courtSchedule2.setCourtSession(PM_SESSION);
        courtSchedule2.setPanel(PanelTypes.YOUTH.name());
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setOuCode("B40IM00");
        courtSchedule2.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "14:00"));
        courtSchedule2.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "17:00"));
        courtSchedule2.setIsOverbookingAllowed(false);
        courtSchedule2.setSlotBased(false);
        courtSchedule2.setMaxDuration(180);
        courtSchedule2.setAvailableDuration(180);
        databaseSeeder.insertCourtSchedule(courtSchedule2);

        final CourtScheduleJudiciary courtScheduleJudiciary2 = createJudiciaryForSchedule(courtSchedule2);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary2);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        String courtScheduleId4 = randomUUID().toString();

        final CourtSchedule courtScheduleWithoutSplit = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleWithoutSplit.setCourtScheduleId(courtScheduleId4);
        courtScheduleWithoutSplit.setSlotBased(false);
        courtScheduleWithoutSplit.setMaxSlots(0);
        courtScheduleWithoutSplit.setMaxDuration(360);
        courtScheduleWithoutSplit.setAvailableSlots(0);
        courtScheduleWithoutSplit.setAvailableDuration(360);
        courtScheduleWithoutSplit.setMaxAdMorningDuration(0);
        courtScheduleWithoutSplit.setMaxAdAfternoonDuration(0);
        courtScheduleWithoutSplit.setCourtSession("AD");
        courtScheduleWithoutSplit.setSupportAdSplit(false);
        courtScheduleWithoutSplit.setPanel(PanelTypes.ADULT.name());
        courtScheduleWithoutSplit.setOuCode("B40IM00");
        courtScheduleWithoutSplit.setSessionDate(sessionDate);
        courtScheduleWithoutSplit.setSessionStartTime(combineDateAndTime(courtScheduleWithoutSplit.getSessionDate(), "10:00"));
        courtScheduleWithoutSplit.setSessionEndTime(combineDateAndTime(courtScheduleWithoutSplit.getSessionDate(), "17:00"));
        courtScheduleWithoutSplit.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        courtScheduleWithoutSplit.setHasHearingsBooked(false);
        databaseSeeder.insertCourtSchedule(courtScheduleWithoutSplit);

        final CourtScheduleJudiciary courtScheduleJudiciaryWithoutSplit = createJudiciaryForSchedule(courtScheduleWithoutSplit);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryWithoutSplit);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", PanelTypes.ADULT.name());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", "AD");

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject)jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtScheduleWithoutSplit.getCourtScheduleId()));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(6));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),10,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),11,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),11,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),12,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),12,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),13,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),14,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),15,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),15,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),16,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),16,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),17,0))));
            }
        });
    }

    @Test
    void shouldRetrieveHearingSlotForAllDayWithoutSplitSupportForEdgeCase() throws Exception {

        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();
        String courtScheduleId = randomUUID().toString();

        final CourtSchedule courtScheduleWithoutSplit = RANDOM.nextObject(CourtSchedule.class);
        courtScheduleWithoutSplit.setCourtScheduleId(courtScheduleId);
        courtScheduleWithoutSplit.setSlotBased(false);
        courtScheduleWithoutSplit.setMaxSlots(0);
        courtScheduleWithoutSplit.setMaxDuration(360);
        courtScheduleWithoutSplit.setAvailableSlots(0);
        courtScheduleWithoutSplit.setAvailableDuration(360);
        courtScheduleWithoutSplit.setMaxAdMorningDuration(0);
        courtScheduleWithoutSplit.setMaxAdAfternoonDuration(0);
        courtScheduleWithoutSplit.setCourtSession("AD");
        courtScheduleWithoutSplit.setSupportAdSplit(false);
        courtScheduleWithoutSplit.setPanel(PanelTypes.ADULT.name());
        courtScheduleWithoutSplit.setOuCode("B40IM00");
        courtScheduleWithoutSplit.setSessionDate(sessionDate);
        courtScheduleWithoutSplit.setSessionStartTime(combineDateAndTime(courtScheduleWithoutSplit.getSessionDate(), "00:01"));
        courtScheduleWithoutSplit.setSessionEndTime(combineDateAndTime(courtScheduleWithoutSplit.getSessionDate(), "23:59"));
        courtScheduleWithoutSplit.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        courtScheduleWithoutSplit.setHasHearingsBooked(false);
        databaseSeeder.insertCourtSchedule(courtScheduleWithoutSplit);

        LocalDate fromDate = courtScheduleWithoutSplit.getSessionDate().minusDays(1);
        LocalDate toDate = courtScheduleWithoutSplit.getSessionDate().plusDays(1);

        final CourtScheduleJudiciary courtScheduleJudiciaryWithoutSplit = createJudiciaryForSchedule(courtScheduleWithoutSplit);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryWithoutSplit);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", PanelTypes.ADULT.name());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtScheduleWithoutSplit.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", "AD");

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject)jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtScheduleWithoutSplit.getCourtScheduleId()));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(23));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithoutSplit.getSessionDate(),0,1)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithoutSplit.getSessionDate(),1,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithoutSplit.getSessionDate(),11,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithoutSplit.getSessionDate(),12,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithoutSplit.getSessionDate(),14,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithoutSplit.getSessionDate(),15,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithoutSplit.getSessionDate(),23,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithoutSplit.getSessionDate(),23,59))));
            }
        });
    }

    @Test
    void shouldRetrieveHearingSlotForAllDayWithSplitSupportForEdgeCase() throws Exception {
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();
        String courtScheduleId = randomUUID().toString();

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
        courtScheduleWithSplit.setSessionStartTime(combineDateAndTime(courtScheduleWithSplit.getSessionDate(), "00:01"));
        courtScheduleWithSplit.setSessionEndTime(combineDateAndTime(courtScheduleWithSplit.getSessionDate(), "23:59"));
        courtScheduleWithSplit.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));

        databaseSeeder.insertCourtSchedule(courtScheduleWithSplit);
        final CourtScheduleJudiciary courtScheduleJudiciaryAD = createJudiciaryForSchedule(courtScheduleWithSplit);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciaryAD);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtScheduleWithSplit.getSessionDate().minusDays(1);
        LocalDate toDate = courtScheduleWithSplit.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtScheduleWithSplit.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtScheduleWithSplit.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", "AD");

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject)jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtScheduleWithSplit.getCourtScheduleId()));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(2));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),0,1)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),13,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),14,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtScheduleWithSplit.getSessionDate(),23,59))));
            }
        });
    }

    @Test
    void shouldRetrieveHearingSlotForEdgeCase1() throws Exception {
        final String courtSession = AM_SESSION;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();
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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "00:01"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:00"));
        courtSchedule.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));

        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(9);
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
        assertThat(slotStartTimesJsonArray.size(), is(12));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),0,1)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),1,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),10,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(2));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),11,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),11,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(2));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),12,0))));
            }
        });
    }

    @Test
    void shouldRetrieveHearingSlotForEdgeCaseAsSessionEndTimeSlotCount() throws Exception {

        final String courtSession = PM_SESSION;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();

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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "13:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "17:00"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(9);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId, bookingId, "13:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId2, bookingId2, "13:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId3, bookingId3, "15:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId4, bookingId4, "17:00", 1);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        final JsonObject hearingSlotJsonObject = (JsonObject) jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        final JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(4));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),13,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(2));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),13,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(2));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),15,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),17,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
            }
        });
    }

    @Test
    void shouldCountBookingsAtSessionBoundaryInCorrectSlot() throws Exception {

        final String courtSession = AM_SESSION;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();

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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "07:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "10:00"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(9);
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId, bookingId, "07:15", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId2, bookingId2, "09:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId4, bookingId4, "09:59", 1);
        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId3, bookingId3, "10:00", 1);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {
        });

        RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        ResponseData tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        JsonObject hearingSlotJsonObject = (JsonObject) jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        JsonArray slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(3));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),7,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),8,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),9,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(3));
            }
        });

        databaseSeeder.updateSessionEndTime(courtScheduleId, combineDateAndTime(courtSchedule.getSessionDate(), "10:30"));

        requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        tempResponseData = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(tempResponseData.getStatus().getStatusCode(), is(OK.getStatusCode()));
        jsonObject = stringToJsonObjectConverter.convert(tempResponseData.getPayload());
        hearingSlotJsonObject = (JsonObject) jsonObject.getJsonArray("hearingSlots").get(0);
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        slotStartTimesJsonArray = hearingSlotJsonObject.getJsonArray("slotStartTimes");
        assertThat(slotStartTimesJsonArray.size(), is(4));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),7,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),8,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),9,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(2));
            }else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),10,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
            }
        });
    }

    @Test
    void shouldRetrieveHearingSlotForEdgeCase2() throws Exception {
        final String courtSession = AM_SESSION;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();
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
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "00:01"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "11:59"));
        courtSchedule.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(9);
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
        assertThat(slotStartTimesJsonArray.size(), is(12));
        slotStartTimesJsonArray.forEach(slotStartTime -> {
            final JsonObject slotStartTimeJsonObject = (JsonObject) slotStartTime;
            if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),0,1)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),1,0))));
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),11,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(1));
                assertThat(slotStartTimeJsonObject.getString("sessionEndTime"), is(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),11,59))));
            }
        });
    }

    @Test
    void shouldNotIncludeMinHearingTimeAndMaxHearingTimeInGetHearingSlotsResponse() throws Exception {
        final String courtSession = AM_SESSION;
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();

        String courtScheduleId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String hearingId = randomUUID().toString();

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCourtSession(courtSession);
        courtSchedule.setPanel(PanelTypes.YOUTH.name());
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setSessionStartTime(combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setSlotBased(true);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setAvailableSlots(9);
        // Note: MinHearingTime and MaxHearingTime are calculated in the domain layer
        // and should not be present in the API response
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        createAllocatedListingsAndInsert(courtScheduleId, sessionDate, hearingId, bookingId, "10:00", 1);

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
        final JsonObject hearingSlotJsonObject = (JsonObject) jsonObject.getJsonArray("hearingSlots").get(0);

        // Verify that MinHearingTime and MaxHearingTime are not present in the response
        assertFalse(hearingSlotJsonObject.containsKey("minHearingTime"), "minHearingTime should not be present in the response");
        assertFalse(hearingSlotJsonObject.containsKey("maxHearingTime"), "maxHearingTime should not be present in the response");

        // Verify other expected fields are present
        assertThat(hearingSlotJsonObject.getString("courtScheduleId"), is(courtSchedule.getCourtScheduleId()));
        assertThat(hearingSlotJsonObject.getString("ouCode"), is(courtSchedule.getOuCode()));
        assertThat(hearingSlotJsonObject.getString("panel"), is(courtSchedule.getPanel()));
    }

    @Test
    void shouldFindClosestCourtScheduleForPolice_WhenSessionStartTimeIsNull_AndBusinessTypesMatch() throws Exception {
        // given - Create multiple court schedules with same business type but different start times
        CourtSchedule courtSchedule1 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule1.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule1.setOuCode("B01LY00");
        courtSchedule1.setCourtRoomNumber(1501);
        courtSchedule1.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule1.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule1.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule1.setPanel(PanelTypes.ADULT.name());
        courtSchedule1.setBusinessType("YFL"); // Same business type as schedule2
        courtSchedule1.setSlotBased(true);
        courtSchedule1.setMaxSlots(10);
        courtSchedule1.setSupportAdSplit(true);
        courtSchedule1.setCourtSession("PM");
        courtSchedule1.setMaxAdMorningDuration(180);
        courtSchedule1.setMaxAdAfternoonDuration(0);
        courtSchedule1.setMaxDuration(120);
        courtSchedule1.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule1.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule1.getSessionDate(), "14:00")); // 14:00 - further from 08:00
        courtSchedule1.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule1.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule1);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule1));

        CourtSchedule courtSchedule2 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId("2771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule2.setOuCode("B01LY00");
        courtSchedule2.setCourtRoomNumber(1501);
        courtSchedule2.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule2.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule2.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule2.setPanel(PanelTypes.ADULT.name());
        courtSchedule2.setBusinessType("YFL"); // Same business type as schedule1
        courtSchedule2.setSlotBased(true);
        courtSchedule2.setMaxSlots(10);
        courtSchedule2.setSupportAdSplit(true);
        courtSchedule2.setCourtSession("AM");
        courtSchedule2.setMaxAdMorningDuration(180);
        courtSchedule2.setMaxAdAfternoonDuration(0);
        courtSchedule2.setMaxDuration(120);
        courtSchedule2.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule2.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule2.getSessionDate(), "10:00")); // 10:00 - closer to 08:00
        courtSchedule2.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule2.getSessionDate(), "13:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule2);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule2));

        CourtSchedule courtSchedule3 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule3.setCourtScheduleId("3771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule3.setOuCode("B01LY00");
        courtSchedule3.setCourtRoomNumber(1501);
        courtSchedule3.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule3.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule3.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule3.setPanel(PanelTypes.ADULT.name());
        courtSchedule3.setBusinessType("TRFL"); // Different business type
        courtSchedule3.setSlotBased(true);
        courtSchedule3.setMaxSlots(10);
        courtSchedule3.setSupportAdSplit(true);
        courtSchedule3.setCourtSession("AM");
        courtSchedule3.setMaxAdMorningDuration(180);
        courtSchedule3.setMaxAdAfternoonDuration(0);
        courtSchedule3.setMaxDuration(120);
        courtSchedule3.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule3.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule3.getSessionDate(), "10:00")); // 09:00 - closest to 08:00 but different business type
        courtSchedule3.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule3.getSessionDate(), "12:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule3);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule3));

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots_For_Police.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T08:00:00.000Z"); // Requested time 08:00
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("IS_POLICE", "true");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertThat(hearingSlots.get("hearingId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
        // Should return courtSchedule2 (10:00) as it's closer to 08:00 than courtSchedule1 (14:00) and has same business type
        assertThat(hearingSlots.get("courtScheduleId").toString().replaceAll("^\"|\"$", ""), is("2771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtRoomId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafb"));
        assertThat(hearingSlots.get("hearingStartTime").toString().replaceAll("^\"|\"$", ""), is("2025-05-13T09:00:00Z"));
        assertThat(hearingSlots.get("duration").toString().replaceAll("^\"|\"$", ""), is("20"));
    }

    @Test
    void shouldFindClosestCourtScheduleForPolice_WhenSessionStartTimeIsNull_AndBusinessTypesDiffer() throws Exception {
        // given - Create multiple court schedules with different business types
        CourtSchedule courtSchedule1 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule1.setCourtScheduleId("1771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule1.setOuCode("B01LY00");
        courtSchedule1.setCourtRoomNumber(1501);
        courtSchedule1.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule1.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule1.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule1.setPanel(PanelTypes.ADULT.name());
        courtSchedule1.setBusinessType("YFL"); // Different business type from schedule2
        courtSchedule1.setSlotBased(true);
        courtSchedule1.setMaxSlots(10);
        courtSchedule1.setSupportAdSplit(true);
        courtSchedule1.setCourtSession("AM");
        courtSchedule1.setMaxAdMorningDuration(180);
        courtSchedule1.setMaxAdAfternoonDuration(0);
        courtSchedule1.setMaxDuration(120);
        courtSchedule1.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule1.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule1.getSessionDate(), "14:00")); // 14:00 - further from 08:00
        courtSchedule1.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule1.getSessionDate(), "17:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule1);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule1));

        CourtSchedule courtSchedule2 = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId("2771a96b-1c5a-45d1-b647-1bec5212cafc");
        courtSchedule2.setOuCode("B01LY00");
        courtSchedule2.setCourtRoomNumber(1501);
        courtSchedule2.setCourtRoomName("Luton Magistrates's Court");
        courtSchedule2.setCourtRoomId("5771a96b-1c5a-45d1-b647-1bec5212cafb");
        courtSchedule2.setCourtHouseId("785339c1-af71-3322-a55b-ba255e0db1c2");
        courtSchedule2.setPanel(PanelTypes.ADULT.name());
        courtSchedule2.setBusinessType("TRFL"); // Different business type from schedule1
        courtSchedule2.setSlotBased(true);
        courtSchedule2.setMaxSlots(10);
        courtSchedule2.setSupportAdSplit(true);
        courtSchedule2.setCourtSession("AM");
        courtSchedule2.setMaxAdMorningDuration(180);
        courtSchedule2.setMaxAdAfternoonDuration(0);
        courtSchedule2.setMaxDuration(120);
        courtSchedule2.setSessionDate(LocalDate.of(2025, 5, 13));
        courtSchedule2.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule2.getSessionDate(), "10:00")); // 10:00 - closer to 08:00
        courtSchedule2.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule2.getSessionDate(), "12:00"));
        databaseSeeder.insertCourtSchedule(courtSchedule2);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule2));

        String searchAndBookHearingSlotsRequestParams = getPayload("courtscheduler.search.book.hearing.slots_For_Police.json");

        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_CENTRE_ID", "785339c1-af71-3322-a55b-ba255e0db1c2");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("COURT_ROOM_ID", "5771a96b-1c5a-45d1-b647-1bec5212cafb");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_DATE", "2025-05-13");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_SESSION-DATE-SEARCH-CUT-OFF", "2025-05-18");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("HEARING_START_TIME", "2025-05-13T08:00:00.000Z"); // Requested time 08:00
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("DURATION_IN_MINUTES", "20");
        searchAndBookHearingSlotsRequestParams = searchAndBookHearingSlotsRequestParams.replace("IS_POLICE", "true");

        final Map<String, Object> requestParamMap = objectMapper.readValue(searchAndBookHearingSlotsRequestParams, new TypeReference<>() {
        });

        final RequestParams requestParams = getRequestParams("/searchlist/hearingslots", "application/vnd.courtscheduler.search.book.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus(), is(OK));
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonObject hearingSlots = jsonObject.getJsonObject("hearingSlots");
        assertThat(hearingSlots.get("hearingId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafc"));
        // Should return courtSchedule1 (first in list) as business types are different, so no time comparison
        assertThat(hearingSlots.get("courtScheduleId").toString().replaceAll("^\"|\"$", ""), is("2771a96b-1c5a-45d1-b647-1bec5212cafc"));
        assertThat(hearingSlots.get("courtRoomId").toString().replaceAll("^\"|\"$", ""), is("5771a96b-1c5a-45d1-b647-1bec5212cafb"));
        assertThat(hearingSlots.get("hearingStartTime").toString().replaceAll("^\"|\"$", ""), is("2025-05-13T09:00:00Z"));
        assertThat(hearingSlots.get("duration").toString().replaceAll("^\"|\"$", ""), is("20"));
    }

    private void createAllocatedListingsAndInsert(final String courtScheduleId, final LocalDate sessionDate, final String hearingId, final
    String bookingId, final String time, final Integer duration) throws SQLException {
        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setDuration(duration);
        allocatedListing.setHearingStartTime(combineDateAndTime(sessionDate, time));
        databaseSeeder.insertAllocatedListing(allocatedListing);
    }

    private void createAllocatedListingsAndInsert(final String courtScheduleIdForAM, final String hearingId, final String bookingId) throws SQLException {
        final AllocatedListing allocatedListingForAMSession = RANDOM.nextObject(AllocatedListing.class);
        allocatedListingForAMSession.setId(randomUUID().toString());
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
    void shouldFilterCourtSchedulesByOptionalParamsIsSlotBased() throws SQLException, JsonProcessingException {
        // given
        final LocalDate sessionDate = LocalDate.of(2024, 4, 15);
        final String ouCode = "B01LY00";
        final String panel = "ADULT";

        // Create three slot-based court schedules with same OU code and panel but different court rooms and business types
        final CourtSchedule matchingSchedule1 = createSlotBasedCourtSchedule(ouCode, panel, sessionDate, "CR01", "TRF");
        final CourtSchedule matchingSchedule2 = createSlotBasedCourtSchedule(ouCode, panel, sessionDate, "CR01", "GAP");
        final CourtSchedule differentCourtRoom = createSlotBasedCourtSchedule(ouCode, panel, sessionDate, "CR02", "TRF");

        databaseSeeder.insertCourtSchedule(matchingSchedule1);
        databaseSeeder.insertCourtSchedule(matchingSchedule2);
        databaseSeeder.insertCourtSchedule(differentCourtRoom);

        // when - search with isSlotBased=true and courtRoomId=CR01
        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", panel);
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", sessionDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", sessionDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", ouCode);
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", "AM");

        Map<String, Object> requestParamMap = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        // Add slot-based specific parameters
        requestParamMap.put("isSlotBased", true);
        requestParamMap.put("courtRoomId", "CR01");

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, requestParamMap);

        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        // then
        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));

        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        JsonArray hearingSlots = jsonObject.getJsonArray("hearingSlots");

        // Should return 2 results (both schedules with court room CR01 but different business types)
        assertThat(hearingSlots.size(), is(2));

        // Verify both results have court room CR01
        for (int i = 0; i < hearingSlots.size(); i++) {
            JsonObject courtSchedule = hearingSlots.getJsonObject(i);
            assertThat(courtSchedule.getString("courtRoomId"), is("CR01"));
            assertThat(courtSchedule.getBoolean("slotBased"), is(true));
            assertThat(courtSchedule.getString("ouCode"), is(ouCode));
            assertThat(courtSchedule.getString("panel"), is(panel));
        }
    }

    private CourtSchedule createSlotBasedCourtSchedule(String ouCode, String panel, LocalDate sessionDate, String courtRoomId, String businessType) {
        CourtSchedule schedule = random(CourtSchedule.class);
        schedule.setCourtScheduleId(UUID.randomUUID().toString());
        schedule.setSlotBased(true);
        schedule.setOuCode(ouCode);
        schedule.setCourtRoomId(courtRoomId);
        schedule.setBusinessType(businessType);
        schedule.setSessionDate(sessionDate);
        schedule.setCourtSession("AM");
        schedule.setActive(true);
        schedule.setCourtRoomNumber(1);
        schedule.setCourtHouseName("Test Court House");
        schedule.setCourtRoomName("Test Court Room " + courtRoomId);
        schedule.setOperationalUnit(ouCode);
        schedule.setPanel(panel);
        schedule.setMaxSlots(10);
        schedule.setMaxDuration(240);
        schedule.setAvailableSlots(10);
        schedule.setAvailableDuration(240);
        schedule.setCourtHouseId("CH" + ouCode);
        schedule.setSupportAdSplit(false);
        schedule.setIsOverbookingAllowed(false);
        schedule.setMaxAdMorningDuration(0);
        schedule.setMaxAdAfternoonDuration(0);
        return schedule;
    }
}
