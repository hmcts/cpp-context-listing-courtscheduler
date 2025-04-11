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
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupUserAsSystemUser;

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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


class HearingSlotIT extends AbstractIT {

    private static final String RELATIVE_URL = "/hearingslots";

    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @BeforeAll
    static void setupSystemUser() {
        setupUserAsSystemUser(USER_ID.toString());
    }

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

        final Response response = putCommand(RELATIVE_URL, "application/vnd.courtscheduler.update.hearing.slots+json", USER_ID, updateHearingSlotsPayload);

        assertThat(response.getStatus(), is(OK.getStatusCode()));
    }

    @Test
    void shouldRetrieveHearingSlot() throws Exception {
        final String courtSession = AM_SESSION;
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
        courtSchedule.setOuCode("B40IM00");
        courtSchedule.setSessionDate(LocalDate.of(2025, 1, 3));
        courtSchedule.setSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "09:30"));
        courtSchedule.setSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), "12:30"));
        databaseSeeder.insertCourtSchedule(courtSchedule);

        final CourtScheduleJudiciary courtScheduleJudiciary = createJudiciaryForSchedule(courtSchedule);
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);

        createAllocatedListingsAndInsert(courtScheduleId, hearingId, bookingId, "10:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, hearingId2, bookingId2, "10:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, hearingId3, bookingId3, "11:00", 1);
        createAllocatedListingsAndInsert(courtScheduleId, hearingId4, bookingId4, "12:00", 1);

        String hearingSlotsRequestParams = getPayload("courtscheduler.get.hearing.slots.json");

        LocalDate fromDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate toDate = courtSchedule.getSessionDate().plusDays(1);

        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("PANEL", courtSchedule.getPanel());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("OU_CODE", courtSchedule.getOuCode());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_START_DATE", fromDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("SESSION_END_DATE", toDate.toString());
        hearingSlotsRequestParams = hearingSlotsRequestParams.replace("COURT_SESSION", courtSession);

        Map<String, Object> map = objectMapper.readValue(hearingSlotsRequestParams, new TypeReference<>() {});

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", USER_ID, map);
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

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", USER_ID, map);
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
        LocalDate sessionDate = LocalDate.now().plusDays(1);
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
        createAllocatedListingsAndInsert(courtScheduleId, hearingId, bookingId, "10:00", 20);
        createAllocatedListingsAndInsert(courtScheduleId, hearingId2, bookingId2, "11:00", 30);
        createAllocatedListingsAndInsert(courtScheduleId, hearingId3, bookingId3, "14:00", 20);
        createAllocatedListingsAndInsert(courtScheduleId, hearingId4, bookingId4, "15:00", 10);

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

        createAllocatedListingsAndInsert(courtScheduleId2, hearingId5, bookingId5, "10:00", 20);
        createAllocatedListingsAndInsert(courtScheduleId2, hearingId6, bookingId6, "11:00", 30);
        createAllocatedListingsAndInsert(courtScheduleId2, hearingId7, bookingId7, "14:00", 20);
        createAllocatedListingsAndInsert(courtScheduleId2, hearingId8, bookingId8, "15:00", 10);


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

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", USER_ID, map);
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

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", USER_ID, requestParamMap);
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

        final RequestParams requestParams = getRequestParams(RELATIVE_URL, "application/vnd.courtscheduler.get.hearing.slots+json", USER_ID, requestParamMap);
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


    private void createAllocatedListingsAndInsert(final String courtScheduleId, final String hearingId, final String bookingId, final String time, final Integer duration) throws SQLException {
        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setDuration(duration);
        allocatedListing.setHearingStartTime(DateUtils.combineDateAndTime(LocalDate.now().plusDays(1), time));
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


        final Response response = deleteCommand(format("%s/%s", RELATIVE_URL, hearingId), "application/vnd.courtscheduler.remove.hearing.slots+json", USER_ID);

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }
}
