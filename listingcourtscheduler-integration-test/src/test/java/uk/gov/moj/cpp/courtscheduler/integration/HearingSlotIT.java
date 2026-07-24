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

    private static final String MAGS_ACCEPT = "application/vnd.courtscheduler.mags.search.and.book+json";

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

        String updateHearingSlotsPayload = getPayload("courtscheduler.list.hearings-in-sessions.json");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_ID_1", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("COURT_SCHEDULE_ID_1_1", "1771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_START_TIME_1_1", toLocalDateTimeString(courtSchedule.getSessionDate().minusDays(1).atTime(10,0)));
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("\"DURATION_1_1\"", "20");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_ID_2", "6771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("COURT_SCHEDULE_ID_2_1", "5771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("HEARING_START_TIME_2_1", toLocalDateTimeString(courtSchedule2.getSessionDate().minusDays(1).atTime(11,0)));
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("COURT_SCHEDULE_ID_2_2", "2771a96b-1c5a-45d1-b647-1bec5212cafc");
        updateHearingSlotsPayload = updateHearingSlotsPayload.replace("\"DURATION_2_2\"", "30");

        final Response response = postCommand("/hearings", "application/vnd.courtscheduler.list.hearings-in-sessions+json", SYSTEM_USER_ID, updateHearingSlotsPayload);

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

    // ─── F3: available_duration_mins must not drift when the same hearing is re-listed ──────

    @Test
    void shouldNotLeakAvailableDurationWhenSameHearingIsReListedInSession() throws Exception {
        // Every listing enrichment pass re-lists the hearing into its sessions
        // (list.hearings-in-sessions). Before the F3 fix the re-list deleted the hearing's
        // allocated_listings rows WITHOUT paying their minutes back into court_schedule while
        // deducting the new booking again — available_duration_mins leaked the hearing's full
        // duration on every pass, so sessions falsely reported no availability to every consumer
        // of the column (crown fallback search, slot search).
        final String courtScheduleId = randomUUID().toString();
        final String hearingId = randomUUID().toString();
        final LocalDate sessionDate = getRandomFutureDateWithinNextYear();

        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
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
        courtSchedule.setMaxDuration(360);
        courtSchedule.setAvailableDuration(360);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(combineDateAndTime(sessionDate, "10:00"));
        courtSchedule.setSessionEndTime(combineDateAndTime(sessionDate, "17:00"));
        courtSchedule.setIsOverbookingAllowed(false);
        courtSchedule.setTotalBookedMorning(0);
        databaseSeeder.insertCourtSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(createJudiciaryForSchedule(courtSchedule));

        final String listPayload = jakarta.json.Json.createObjectBuilder()
                .add("hearingSlots", jakarta.json.Json.createArrayBuilder()
                        .add(jakarta.json.Json.createObjectBuilder()
                                .add("hearingId", hearingId)
                                .add("courtScheduleIds", jakarta.json.Json.createArrayBuilder()
                                        .add(jakarta.json.Json.createObjectBuilder()
                                                .add("courtScheduleId", courtScheduleId)
                                                .add("hearingStartTime", toLocalDateTimeString(sessionDate.atTime(10, 0)))
                                                .add("durationInMinutes", 60)))))
                .build()
                .toString();

        // First list: books 60 minutes → 300 remaining.
        final Response first = postCommand("/hearings",
                "application/vnd.courtscheduler.list.hearings-in-sessions+json", SYSTEM_USER_ID, listPayload);
        assertThat(first.getStatus(), is(OK.getStatusCode()));
        assertThat(databaseReader.courtScheduleById(courtScheduleId).getAvailableDuration(), is(300));

        // Re-list of the SAME hearing into the SAME session (what every enrichment pass does):
        // the prior 60 minutes must be paid back before the new 60 are charged — still 300,
        // not the pre-fix 240.
        final Response second = postCommand("/hearings",
                "application/vnd.courtscheduler.list.hearings-in-sessions+json", SYSTEM_USER_ID, listPayload);
        assertThat(second.getStatus(), is(OK.getStatusCode()));
        assertThat("capacity charged exactly once across re-lists — no drift",
                databaseReader.courtScheduleById(courtScheduleId).getAvailableDuration(), is(300));

        final long hearingRows = databaseReader.allocatedListings().stream()
                .filter(al -> hearingId.equals(al.getHearingId()))
                .count();
        assertThat("exactly one allocated_listings row for the hearing after re-list", hearingRows, is(1L));
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


        final Response response = deleteCommand(format("/sessions/%s", hearingId), "application/vnd.courtscheduler.release.sessions+json", SYSTEM_USER_ID);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldSearchAndBookHearingSlotForPolice() throws Exception {
        // Police (isPolice=true) search-and-book. The search returns the all-day ('AD') weekday session at
        // the centre — keyed on court_house_id = the courtCentreId UUID — on/after the hearing date with a
        // YFL/TRFL/DAFL/NGAP/GAP/REM business type, then books it by its courtScheduleId. ouCode is used by
        // neither the search nor the booking, so court_house_id (a UUID) and ouCode are deliberately distinct
        // here: a regression re-introducing the old ouCode-keyed re-search would query court_house_id = ouCode
        // and find nothing, failing this test.
        final LocalDate hearingDate = nextFutureMonday();
        final String centreId = "6b3f2a10-9c4d-4e8a-bb12-2f7a5c9e0d34";
        final String otherCentreId = "9e1c7d52-3b8a-4f6c-9a01-7d4e2c5b8f10";
        final String roomId = "5771a96b-1c5a-45d1-b647-1bec5212cafb";
        final String hearingId = UUID.randomUUID().toString();

        final String matchingId = seedMagsSession("1771a96b-1c5a-45d1-b647-1bec5212cafc", roomId,
                centreId, "OU-POL", "REM", "AD", hearingDate, 360);
        // Excluded from the search (wrong centre / before the hearing date); distinct courtScheduleIds and
        // ouCodes avoid the unique-index collision.
        seedMagsSession("3771a96b-1c5a-45d1-b647-1bec5212cafc", roomId, otherCentreId, "OU-NCEN", "REM", "AD", hearingDate, 360);
        seedMagsSession("4771a96b-1c5a-45d1-b647-1bec5212cafc", roomId, centreId, "OU-NPAST", "REM", "AD", hearingDate.minusDays(7), 360);

        final String payload = buildMagsPolicePayload(centreId, roomId,
                hearingDate.toString(), hearingDate.plusDays(5).toString(),
                hearingDate + "T10:00:00.000Z", "20", "true");

        final Response response = postCommand("/hearings/" + hearingId, MAGS_ACCEPT, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getJsonObject(0).getString("courtScheduleId"), is(matchingId));
        assertThat(sessions.getJsonObject(0).getString("courtRoomId"), is(roomId));
    }

    @Test
    void shouldSearchAndBookHearingSlotForNonPolice() throws Exception {
        // Non-police (non-SPI) single-day search-and-book matches business type NCFL in the centre
        // (court_house_id = the courtCentreId UUID) and court room, on/after the hearing date, then books the
        // session by its courtScheduleId. A same-day session in a non-NCFL business type is filtered out.
        final LocalDate hearingDate = nextFutureMonday();
        final String centreId = "785339c1-af71-3322-a55b-ba255e0db1c2";
        final String roomId = "5771a96b-1c5a-45d1-b647-1bec5212cafb";
        final String hearingId = UUID.randomUUID().toString();

        final String matchingId = seedMagsSession("1771a96b-1c5a-45d1-b647-1bec5212cafc", roomId,
                centreId, "OU-MATCH", "NCFL", "AD", hearingDate, 360);
        // Non-NCFL business type — excluded by the non-police business-type filter.
        seedMagsSession("2771a96b-1c5a-45d1-b647-1bec5212cafc", roomId, centreId, "OU-OTHER", "ENF", "AD", hearingDate, 360);

        final String payload = buildMagsPayload(centreId, roomId,
                hearingDate.toString(), hearingDate.plusDays(5).toString(),
                hearingDate + "T10:00:00.000Z", "20");

        final Response response = postCommand("/hearings/" + hearingId, MAGS_ACCEPT, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getJsonObject(0).getString("courtScheduleId"), is(matchingId));
        assertThat(sessions.getJsonObject(0).getString("courtRoomId"), is(roomId));
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

        final String searchAndBookHearingSlotsRequestParams = buildMagsPayload(
                "785339c1-af71-3322-a55b-ba255e0db1c2",
                "5771a96b-1c5a-45d1-b647-1bec5212cafb", "2025-05-13", "2025-05-18",
                "2025-05-13T09:00:00Z", "20");

        final Response response = postCommand("/hearings/5771a96b-1c5a-45d1-b647-1bec5212cafc", MAGS_ACCEPT, SYSTEM_USER_ID, searchAndBookHearingSlotsRequestParams);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(0));
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

        final String searchAndBookHearingSlotsRequestParams = buildMagsPayload(
                "785339c1-af71-3322-a55b-ba255e0db1c2",
                "5771a96b-1c5a-45d1-b647-1bec5212cafb", "2025-05-13", "2025-05-18",
                "2025-05-13T09:00:00Z", "20");

        final Response response = postCommand("/hearings/5771a96b-1c5a-45d1-b647-1bec5212cafc", MAGS_ACCEPT, SYSTEM_USER_ID, searchAndBookHearingSlotsRequestParams);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(0));
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

        final String searchAndBookHearingSlotsRequestParams = buildMagsPayload(
                "785339c1-af71-3322-a55b-ba255e0db1c2",
                "5771a96b-1c5a-45d1-b647-1bec5212cafb", "2025-05-13", "2025-05-18",
                "2025-05-13T09:00:00Z", "20");

        final Response response = postCommand("/hearings/5771a96b-1c5a-45d1-b647-1bec5212cafc", MAGS_ACCEPT, SYSTEM_USER_ID, searchAndBookHearingSlotsRequestParams);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(0));
    }

    @Test
    void shouldSearchAndBookHearingSlotDurationBasedForNonPolice() throws Exception {
        // Duration drives the number of days: durationInMinutes > 360 selects the multi-day consecutive
        // path. 720 minutes => 2 days, so the engine books one AD session per consecutive weekday within a
        // single (court room, business type) and returns them in date order.
        final LocalDate day1 = nextFutureMonday();
        final LocalDate day2 = day1.plusDays(1);
        final String centreId = "785339c1-af71-3322-a55b-ba255e0db1c2";
        final String roomId = "5771a96b-1c5a-45d1-b647-1bec5212cafb";
        final String hearingId = UUID.randomUUID().toString();

        final String day1Id = seedMagsSession("1771a96b-1c5a-45d1-b647-1bec5212cafc", roomId,
                centreId, "OU-DAY1", "NCFL", "AD", day1, 360);
        final String day2Id = seedMagsSession("2771a96b-1c5a-45d1-b647-1bec5212cafc", roomId,
                centreId, "OU-DAY2", "NCFL", "AD", day2, 360);

        final String payload = buildMagsPayload(centreId, roomId,
                day1.toString(), day1.plusDays(14).toString(),
                day1 + "T10:00:00.000Z", "720");

        final Response response = postCommand("/hearings/" + hearingId, MAGS_ACCEPT, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(2));
        assertThat(sessions.getJsonObject(0).getString("courtScheduleId"), is(day1Id));
        assertThat(sessions.getJsonObject(1).getString("courtScheduleId"), is(day2Id));
    }

    @Test
    void shouldNotBookMultidayWhenNoSingleBusinessTypeRunExists() throws Exception {
        // Multi-day search-and-book is CONSECUTIVE-only and binds to a single (court room, business type):
        // the engine needs `daysNeeded` AD weekday sessions of the SAME business type in one room. Here the
        // two consecutive days carry DIFFERENT business types (REM then NGAP), so no single business-type
        // run of length 2 exists and nothing is booked — an empty response. (The old AD-sparse engine booked
        // across business types; that behaviour has been removed.)
        final LocalDate day1 = nextFutureMonday();
        final LocalDate day2 = day1.plusDays(1);
        final String centreId = "785339c1-af71-3322-a55b-ba255e0db1c2";
        final String roomId = "5771a96b-1c5a-45d1-b647-1bec5212cafb";
        final String hearingId = UUID.randomUUID().toString();

        seedMagsSession("1771a96b-1c5a-45d1-b647-1bec5212cafc", roomId,
                centreId, "OU-DAY1", "REM", "AD", day1, 360);
        seedMagsSession("2771a96b-1c5a-45d1-b647-1bec5212cafc", roomId,
                centreId, "OU-DAY2", "NGAP", "AD", day2, 360);

        final String payload = buildMagsPayload(centreId, roomId,
                day1.toString(), day1.plusDays(14).toString(),
                day1 + "T10:00:00.000Z", "720");

        final Response response = postCommand("/hearings/" + hearingId, MAGS_ACCEPT, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(0));
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
            } else if (slotStartTimeJsonObject.getString("sessionStartTime").equals(toResponseDateStringISO(localDateToDateWithTime(courtSchedule.getSessionDate(),14,0)))) {
                assertThat(slotStartTimeJsonObject.getInt("count"), is(0));
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
    void shouldReturnOnlyEarliestDateSessionForSingleDaySearch() throws Exception {
        // A single-day search (durationInMinutes <= 360 => daysNeeded = 1) books exactly one session. The
        // requested hearingStartTime is anchored to the earliest date, so a later NCFL session in the window
        // is excluded by the "start time within the session" filter and the earliest-date session is booked.
        final LocalDate earliest = nextFutureMonday();
        final LocalDate later = earliest.plusDays(1);
        final String centreId = "785339c1-af71-3322-a55b-ba255e0db1c2";
        final String roomId = "5771a96b-1c5a-45d1-b647-1bec5212cafb";
        final String hearingId = UUID.randomUUID().toString();

        final String earliestId = seedMagsSession("1771a96b-1c5a-45d1-b647-1bec5212cafc", roomId,
                centreId, "OU-EARLY", "NCFL", "AD", earliest, 360);
        seedMagsSession("2771a96b-1c5a-45d1-b647-1bec5212cafc", roomId, centreId, "OU-LATE", "NCFL", "AD", later, 360);

        final String payload = buildMagsPayload(centreId, roomId,
                earliest.toString(), earliest.plusDays(14).toString(),
                earliest + "T10:00:00.000Z", "20");

        final Response response = postCommand("/hearings/" + hearingId, MAGS_ACCEPT, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getJsonObject(0).getString("courtScheduleId"), is(earliestId));
    }

    @Test
    void shouldExcludeSessionsBeforeHearingDate() throws Exception {
        // The engine only considers sessions whose session_start is on or after the requested hearingDate.
        // An NCFL session a week earlier is ignored; the NCFL session on the hearing date is booked.
        final LocalDate hearingDate = nextFutureMonday();
        final LocalDate before = hearingDate.minusDays(7);
        final String centreId = "785339c1-af71-3322-a55b-ba255e0db1c2";
        final String roomId = "5771a96b-1c5a-45d1-b647-1bec5212cafb";
        final String hearingId = UUID.randomUUID().toString();

        final String onDateId = seedMagsSession("1771a96b-1c5a-45d1-b647-1bec5212cafc", roomId,
                centreId, "OU-ONDATE", "NCFL", "AD", hearingDate, 360);
        seedMagsSession("2771a96b-1c5a-45d1-b647-1bec5212cafc", roomId, centreId, "OU-BEFORE", "NCFL", "AD", before, 360);

        final String payload = buildMagsPayload(centreId, roomId,
                hearingDate.toString(), hearingDate.plusDays(14).toString(),
                hearingDate + "T10:00:00.000Z", "20");

        final Response response = postCommand("/hearings/" + hearingId, MAGS_ACCEPT, SYSTEM_USER_ID, payload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.readEntity(String.class));
        final JsonArray sessions = jsonObject.getJsonArray("sessions");
        assertThat(sessions.size(), is(1));
        assertThat(sessions.getJsonObject(0).getString("courtScheduleId"), is(onDateId));
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
        // Final session with an assigned courtroom — without this, random() may set isDraft=true and the draft-strip nulls courtRoomId.
        schedule.setIsDraft(false);
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

    // ---- Multiday CROWN search integration tests ----

    @Test
    void shouldReturnFirstDayOfConsecutiveAvailabilityForMultidayCrownSearch() throws Exception {
        // 3-day CROWN hearing: 3 consecutive business days Thu 26, Fri 27, Mon 30 March 2026
        final String courtRoomId = randomUUID().toString();
        final String ouCode = "C20CO00";

        final java.time.LocalDate d1 = futureMultidayStartThursday();
        final java.time.LocalDate d2 = d1.plusDays(1);
        final java.time.LocalDate d3 = d1.plusDays(4);

        CourtSchedule day1 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d1, 360);
        CourtSchedule day2 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d2, 360);
        CourtSchedule day3 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d3, 360);

        databaseSeeder.insertCourtSchedule(day1);
        databaseSeeder.insertCourtSchedule(day2);
        databaseSeeder.insertCourtSchedule(day3);

        Map<String, Object> map = buildMultidayCrownRequest("ADULT", ouCode, d1.minusDays(1).toString(), d3.plusDays(1).toString(), "AD", "1080", "CROWN", "10", "1");
        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        JsonArray hearingSlots = jsonObject.getJsonArray("hearingSlots");

        assertThat(jsonObject.getInt("results"), is(1));
        assertThat(hearingSlots.size(), is(1));
        assertThat(hearingSlots.getJsonObject(0).getString("courtScheduleId"), is(day1.getCourtScheduleId()));
    }

    @Test
    void shouldPopulateSlotStartTimesForMultidayCrownDurationSearch() throws Exception {
        // Regression guard (SPRDT-903 perf-fix #4): the multi-day re-hydrate path was switched to
        // getCourtSchedulesByIdList, which never enriches slotStartTimes, so duration-based
        // multi-day Crown searches returned slotStartTimes:[] while single-day searches kept the
        // hourly breakdown. This asserts the breakdown is present (and correct) for the returned
        // start day. The pre-existing multi-day tests only assert the consecutive-day filtering,
        // which is why the regression slipped through.
        final String courtRoomId = randomUUID().toString();
        final String ouCode = "C20CO00";

        final java.time.LocalDate d1 = futureMultidayStartThursday();
        final java.time.LocalDate d2 = d1.plusDays(1);
        final java.time.LocalDate d3 = d1.plusDays(4);

        // Start day is overbooking-exempt so it stays a valid multi-day start despite the booking,
        // letting us assert the booked minutes surface in its slotStartTimes.
        CourtSchedule day1 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d1, 360);
        day1.setIsOverbookingAllowed(true);
        CourtSchedule day2 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d2, 360);
        CourtSchedule day3 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d3, 360);

        databaseSeeder.insertCourtSchedule(day1);
        databaseSeeder.insertCourtSchedule(day2);
        databaseSeeder.insertCourtSchedule(day3);

        // 120 booked minutes at 10:00 on the start day (clear of the lunchtime national break).
        createAllocatedListingsAndInsert(day1.getCourtScheduleId(), d1,
                randomUUID().toString(), randomUUID().toString(), "10:00", 120);

        Map<String, Object> map = buildMultidayCrownRequest("ADULT", ouCode, d1.minusDays(1).toString(), d3.plusDays(1).toString(), "AD", "1080", "CROWN", "10", "1");
        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        final JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        final JsonArray hearingSlots = jsonObject.getJsonArray("hearingSlots");

        assertThat(jsonObject.getInt("results"), is(1));
        assertThat(hearingSlots.size(), is(1));
        final JsonObject hearingSlot = hearingSlots.getJsonObject(0);
        assertThat(hearingSlot.getString("courtScheduleId"), is(day1.getCourtScheduleId()));

        // The regression: this array used to come back empty for multi-day duration searches.
        final JsonArray slotStartTimes = hearingSlot.getJsonArray("slotStartTimes");
        assertFalse(slotStartTimes.isEmpty(),
                "multi-day duration search must return the hourly slotStartTimes breakdown, not an empty array");

        // The 120 booked minutes must be reflected; total booked across the hourly buckets == 120.
        int totalBookedInSlots = 0;
        for (final JsonValue slot : slotStartTimes) {
            totalBookedInSlots += slot.asJsonObject().getInt("count");
        }
        assertThat(totalBookedInSlots, is(120));
    }

    @Test
    void shouldSkipFirstDayIfInsufficientAvailabilityForMultidayCrown() throws Exception {
        // Day1 (Thu 26) has only 200 available, Day2 (Fri 27), Day3 (Mon 30), Day4 (Tue 31) have 360 each
        final String courtRoomId = randomUUID().toString();
        final String ouCode = "C20CO00";

        CourtSchedule day1 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 26), 360);
        CourtSchedule day2 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 27), 360);
        CourtSchedule day3 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 30), 360);
        CourtSchedule day4 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 31), 360);

        databaseSeeder.insertCourtSchedule(day1);
        databaseSeeder.insertCourtSchedule(day2);
        databaseSeeder.insertCourtSchedule(day3);
        databaseSeeder.insertCourtSchedule(day4);

        // Book 200 on day1 so available = 360-200 = 160 < 360
        createAllocatedListingsAndInsert(day1.getCourtScheduleId(), LocalDate.of(2026, 3, 26),
                randomUUID().toString(), randomUUID().toString(), "10:00", 200);

        Map<String, Object> map = buildMultidayCrownRequest("ADULT", ouCode, "2026-03-25", "2026-04-01", "AD", "1080", "CROWN", "10", "1");
        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        JsonArray hearingSlots = jsonObject.getJsonArray("hearingSlots");

        // Day1 can't start a 3-day window (insufficient), Day2 can (Fri 27, Mon 30, Tue 31)
        assertThat(jsonObject.getInt("results"), is(1));
        assertThat(hearingSlots.size(), is(1));
        assertThat(hearingSlots.getJsonObject(0).getString("courtScheduleId"), is(day2.getCourtScheduleId()));
    }

    @Test
    void shouldReturnEmptyWhenNoConsecutiveDaysAvailableForMultidayCrown() throws Exception {
        // Only 2 consecutive days available (26,27), need 3 - gap on 28
        final String courtRoomId = randomUUID().toString();
        final String ouCode = "C20CO00";

        CourtSchedule day1 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 26), 360);
        CourtSchedule day2 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 27), 360);
        // Missing 28 March

        databaseSeeder.insertCourtSchedule(day1);
        databaseSeeder.insertCourtSchedule(day2);

        Map<String, Object> map = buildMultidayCrownRequest("ADULT", ouCode, "2026-03-25", "2026-03-31", "AD", "1080", "CROWN", "10", "1");
        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(jsonObject.getInt("results"), is(0));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(0));
    }

    @Test
    void shouldAllowOverbookingExemptDaysInMultidayCrownSearch() throws Exception {
        // Day1 (Thu) has 0 available but overbooking allowed, Day2 (Fri) and Day3 (Mon) have 360 each
        final String courtRoomId = randomUUID().toString();
        final String ouCode = "C20CO00";

        final java.time.LocalDate d1 = futureMultidayStartThursday();
        final java.time.LocalDate d2 = d1.plusDays(1);
        final java.time.LocalDate d3 = d1.plusDays(4);

        CourtSchedule day1 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d1, 360);
        day1.setIsOverbookingAllowed(true);
        CourtSchedule day2 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d2, 360);
        CourtSchedule day3 = createCrownDurationBasedSchedule(courtRoomId, ouCode, d3, 360);

        databaseSeeder.insertCourtSchedule(day1);
        databaseSeeder.insertCourtSchedule(day2);
        databaseSeeder.insertCourtSchedule(day3);

        // Book all of day1's capacity
        createAllocatedListingsAndInsert(day1.getCourtScheduleId(), d1,
                randomUUID().toString(), randomUUID().toString(), "10:00", 360);

        Map<String, Object> map = buildMultidayCrownRequest("ADULT", ouCode, d1.minusDays(1).toString(), d3.plusDays(1).toString(), "AD", "1080", "CROWN", "10", "1");
        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        // Day1 overbooking allowed, so Thu 26→Fri 27→Mon 30 is valid (weekend skipped)
        assertThat(jsonObject.getInt("results"), is(1));
        assertThat(jsonObject.getJsonArray("hearingSlots").getJsonObject(0).getString("courtScheduleId"), is(day1.getCourtScheduleId()));
    }

    @Test
    void shouldNotApplyMultidayFilterForMagistratesJurisdiction() throws Exception {
        // Same setup as multiday but MAGISTRATES - should return all sessions normally
        final String courtRoomId = randomUUID().toString();
        final String ouCode = "B40IM00";

        CourtSchedule day1 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 26), 360);
        day1.setJurisdiction("MAGISTRATES");
        day1.setIsOverbookingAllowed(true);
        CourtSchedule day2 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 27), 360);
        day2.setJurisdiction("MAGISTRATES");
        day2.setIsOverbookingAllowed(true);

        databaseSeeder.insertCourtSchedule(day1);
        databaseSeeder.insertCourtSchedule(day2);

        Map<String, Object> map = buildMultidayCrownRequest("ADULT", ouCode, "2026-03-25", "2026-03-27", "AD", "1080", "MAGISTRATES", "10", "1");
        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        // MAGISTRATES: no multiday filter, returns all sessions that pass overbooking filter
        assertThat(jsonObject.getInt("results"), is(2));
    }

    @Test
    void shouldReturnEmptyWhenDurationExceedsAvailableConsecutiveDaysForCrownMultiday() throws Exception {
        // Reproduces issue from curl: duration=3600 (10 days) but only 4 consecutive days available
        final String courtRoomId = randomUUID().toString();
        final String ouCode = "C20BI00";

        CourtSchedule day1 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 30), 360);
        CourtSchedule day2 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 3, 31), 360);
        CourtSchedule day3 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 4, 1), 360);
        CourtSchedule day4 = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 4, 2), 360);

        databaseSeeder.insertCourtSchedule(day1);
        databaseSeeder.insertCourtSchedule(day2);
        databaseSeeder.insertCourtSchedule(day3);
        databaseSeeder.insertCourtSchedule(day4);

        // duration=3600 means 3600/360=10 consecutive days needed, only 4 available
        Map<String, Object> map = buildMultidayCrownRequest("ADULT", ouCode, "2026-03-13", "2026-04-30", "AD", "3600", "CROWN", "10", "1");
        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        assertThat(jsonObject.getInt("results"), is(0));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(0));
    }

    @Test
    void shouldReturnCorrectPaginationTotalCountForNonMultidaySearch() throws Exception {
        final String courtRoomId = randomUUID().toString();
        final String ouCode = "C20CO02";

        // Create 3 schedules
        for (int i = 0; i < 3; i++) {
            CourtSchedule schedule = createCrownDurationBasedSchedule(courtRoomId, ouCode, LocalDate.of(2026, 4, 10 + i), 360);
            schedule.setIsOverbookingAllowed(true);
            databaseSeeder.insertCourtSchedule(schedule);
        }

        // pageSize=2, pageNumber=1 - should get 2 results but total should be 3
        Map<String, Object> map = buildMultidayCrownRequest("ADULT", ouCode, "2026-04-10", "2026-04-15", "AD", "60", "CROWN", "2", "1");
        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", SYSTEM_USER_ID, map);
        final ResponseData response = poll(requestParams).with().timeout(30L, SECONDS).pollInterval(50L, MILLISECONDS).pollDelay(0L, MILLISECONDS).until();

        assertThat(response.getStatus().getStatusCode(), is(OK.getStatusCode()));
        JsonObject jsonObject = stringToJsonObjectConverter.convert(response.getPayload());
        // Total results from DB should be 3, not 2 (the page size)
        assertThat(jsonObject.getInt("results"), is(3));
        assertThat(jsonObject.getInt("pageCount"), is(2)); // ceil(3/2) = 2
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(2));
    }

    /** A future Thursday (~2 weeks out) so {Thu, Fri, Mon=+4} are 3 consecutive *business*
     * days spanning a weekend - keeps the multi-day weekend-skip coverage without stale hardcoded dates. */
    private static java.time.LocalDate futureMultidayStartThursday() {
        java.time.LocalDate d = java.time.LocalDate.now().plusWeeks(2);
        while (d.getDayOfWeek() != java.time.DayOfWeek.THURSDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    private CourtSchedule createCrownDurationBasedSchedule(String courtRoomId, String ouCode, LocalDate sessionDate, int maxDuration) {
        CourtSchedule schedule = random(CourtSchedule.class);
        schedule.setCourtScheduleId(randomUUID().toString());
        schedule.setSlotBased(false);
        schedule.setOuCode(ouCode);
        schedule.setCourtRoomId(courtRoomId);
        schedule.setBusinessType("CRI");
        schedule.setSessionDate(sessionDate);
        schedule.setCourtSession("AD");
        schedule.setActive(true);
        schedule.setCourtRoomNumber(1);
        schedule.setCourtHouseName("Crown Court Centre");
        schedule.setCourtRoomName("Court Room 1");
        schedule.setOperationalUnit(ouCode);
        schedule.setPanel("ADULT");
        schedule.setMaxSlots(0);
        schedule.setMaxDuration(maxDuration);
        schedule.setAvailableSlots(0);
        schedule.setAvailableDuration(maxDuration);
        schedule.setCourtHouseId("CH" + ouCode);
        schedule.setSupportAdSplit(false);
        schedule.setIsOverbookingAllowed(false);
        schedule.setMaxAdMorningDuration(0);
        schedule.setMaxAdAfternoonDuration(0);
        schedule.setJurisdiction("CROWN");
        schedule.setIsDraft(false);
        schedule.setSessionStartTime(combineDateAndTime(sessionDate, "09:00"));
        schedule.setSessionEndTime(combineDateAndTime(sessionDate, "17:00"));
        schedule.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        return schedule;
    }

    /**
     * A Monday safely in the future. The MAGS search-and-book engine
     * ({@code SlotsUpdateService.magsSearchAndBook}: single-day {@code searchBookHearingSlots} cascade,
     * multi-day {@code findConsecutiveSessionsForCentre}) only matches sessions whose {@code session_start}
     * is a weekday on or after the requested {@code hearingDate}, so seeded sessions must fall on a future
     * weekday. Anchoring on a Monday keeps multi-day spans (Mon, Tue, ...) clear of the weekend exclusion
     * regardless of which day the suite runs.
     */
    private static LocalDate nextFutureMonday() {
        final int dow = LocalDate.now().getDayOfWeek().getValue(); // 1=Mon..7=Sun
        return LocalDate.now().plusDays((8 - dow) % 7).plusDays(7);
    }

    /**
     * Seed an all-day court schedule for the MAGS search-and-book engine. The engine matches on
     * {@code court_house_id} (= the request's {@code courtCentreId} UUID), {@code court_session = 'AD'}, a
     * weekday {@code session_start} within the search window, and — for non-police only —
     * {@code available_duration_mins >= 360}; {@code ouCode} is independent of {@code court_house_id} and is
     * not used by the search/booking. Pass {@code courtSession}/{@code availableDuration} that deliberately
     * miss those filters to seed excluded "noise" rows.
     */
    private String seedMagsSession(final String courtScheduleId, final String courtRoomId,
                                   final String courtHouseId, final String ouCode,
                                   final String businessType, final String courtSession,
                                   final LocalDate sessionDate, final int availableDuration)
            throws java.sql.SQLException {
        final CourtSchedule cs = RANDOM.nextObject(CourtSchedule.class);
        cs.setCourtScheduleId(courtScheduleId);
        cs.setOuCode(ouCode);
        cs.setCourtRoomNumber(1501);
        cs.setCourtRoomName("Luton Magistrates's Court");
        cs.setCourtRoomId(courtRoomId);
        cs.setCourtHouseId(courtHouseId);
        cs.setPanel(PanelTypes.ADULT.name());
        cs.setBusinessType(businessType);
        cs.setActive(true);
        cs.setSlotBased(false);
        cs.setCourtSession(courtSession);
        cs.setMaxSlots(0);
        cs.setMaxDuration(360);
        cs.setAvailableDuration(availableDuration);
        cs.setSupportAdSplit(false);
        cs.setMaxAdMorningDuration(180);
        cs.setMaxAdAfternoonDuration(180);
        cs.setSessionDate(sessionDate);
        cs.setSessionStartTime(combineDateAndTime(sessionDate, "10:00"));
        cs.setSessionEndTime(combineDateAndTime(sessionDate, "17:00"));
        cs.setIsDraft(false);
        databaseSeeder.insertCourtSchedule(cs);
        return courtScheduleId;
    }

    private static String buildMagsPayload(final String courtCentreId,
                                           final String courtRoomId, final String hearingDate,
                                           final String cutOff, final String hearingStartTime,
                                           final String durationInMinutes) {
        return String.format(
                "{\"courtCentreId\":\"%s\",\"courtRoomId\":\"%s\"," +
                "\"hearingDate\":\"%s\",\"hearingSessionDateSearchCutOff\":\"%s\"," +
                "\"hearingStartTime\":\"%s\",\"durationInMinutes\":%s}",
                courtCentreId, courtRoomId, hearingDate, cutOff, hearingStartTime, durationInMinutes);
    }

    private static String buildMagsPolicePayload(final String courtCentreId,
                                                  final String courtRoomId, final String hearingDate,
                                                  final String cutOff, final String hearingStartTime,
                                                  final String durationInMinutes, final String isPolice) {
        return String.format(
                "{\"courtCentreId\":\"%s\",\"courtRoomId\":\"%s\"," +
                "\"hearingDate\":\"%s\",\"hearingSessionDateSearchCutOff\":\"%s\"," +
                "\"hearingStartTime\":\"%s\",\"durationInMinutes\":%s,\"isPolice\":%s}",
                courtCentreId, courtRoomId, hearingDate, cutOff, hearingStartTime, durationInMinutes, isPolice);
    }

    private Map<String, Object> buildMultidayCrownRequest(String panel, String ouCode, String startDate, String endDate,
                                                           String courtSession, String duration, String jurisdiction,
                                                           String pageSize, String pageNumber) throws Exception {
        String payload = getPayload("courtscheduler.get.hearing.slots_multiday_crown.json");
        payload = payload.replace("PANEL", panel);
        payload = payload.replace("OU_CODE", ouCode);
        payload = payload.replace("SESSION_START_DATE", startDate);
        payload = payload.replace("SESSION_END_DATE", endDate);
        payload = payload.replace("COURT_SESSION", courtSession);
        payload = payload.replace("DURATION", duration);
        payload = payload.replace("JURISDICTION", jurisdiction);
        payload = payload.replace("PAGE_SIZE", pageSize);
        payload = payload.replace("PAGE_NUMBER", pageNumber);
        return objectMapper.readValue(payload, new TypeReference<>() {});
    }
}
