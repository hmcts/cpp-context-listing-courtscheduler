package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.time.LocalDate.parse;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.CROWN;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SlotsSearchServiceTest {
    private static final String UUID_001C067D = "001c067d-eaca-4ce5-ad90-a366ef3e4bb6";
    private static final String UUID_0B9417B8 = "0b9417b8-91b4-385d-9e01-069855777c4f";
    private static final String VALUE_1080 = "1080";
    private static final String VALUE_10_00 = "10:00";
    private static final String VALUE_12_00 = "12:00";
    private static final String DATE_2026_03_23 = "2026-03-23";
    private static final String DATE_2026_03_24 = "2026-03-24";
    private static final String DATE_2026_03_25 = "2026-03-25";
    private static final String DATE_2026_03_26 = "2026-03-26";
    private static final String DATE_2026_03_27 = "2026-03-27";
    private static final String DATE_2026_03_30 = "2026-03-30";
    private static final String DATE_2026_04_08 = "2026-04-08";
    private static final String DATE_2026_04_09 = "2026-04-09";
    private static final String DATE_2026_04_10 = "2026-04-10";
    private static final String DATE_2026_04_14 = "2026-04-14";
    private static final String DATE_2026_04_15 = "2026-04-15";
    private static final String DATE_2026_04_16 = "2026-04-16";
    private static final String DATE_2026_04_17 = "2026-04-17";
    private static final String DATE_2026_04_21 = "2026-04-21";
    private static final String DATE_2026_05_07 = "2026-05-07";
    private static final String DATE_2026_05_08 = "2026-05-08";
    private static final String DATE_2026_08_17 = "2026-08-17";
    private static final String VALUE_500 = "500";
    private static final String VALUE_720 = "720";
    private static final String ADULT_2 = "ADULT";
    private static final String ADULT_YOUTH = "ADULT,YOUTH";
    private static final String APPLS_2 = "APPLS";
    private static final String B12_JR00 = "B12JR00";
    private static final String BYS_2 = "BYS";
    private static final String BLACKFRIARS_CROWN_COURT = "Blackfriars Crown Court";
    private static final String C01_CY00 = "C01CY00";
    private static final String C03_CL00 = "C03CL00";
    private static final String C05_LV00 = "C05LV00";
    private static final String C13_BR00 = "C13BR00";
    private static final String C20_CO00 = "C20CO00";
    private static final String CHAIR_2 = "CHAIR";
    private static final String CROWN_2 = "CROWN";
    private static final String COURT_NAME1 = "Court name1";
    private static final String COURTROOM_1 = "Courtroom 1";
    private static final String DRAFT_2 = "DRAFT";
    private static final String LGT_2 = "LGT";
    private static final String LIVERPOOL_MAGS_COURT = "Liverpool Mags Court";
    private static final String PANEL_2 = "PANEL";
    private static final String UNN_2 = "UNN";
    private static final String UTC_2 = "UTC";
    private static final String HEARING_SLOTS = "hearingSlots";
    private static final String PAGE_COUNT = "pageCount";
    private static final String RESULTS = "results";
    private static final String SESSION_DATE = "sessionDate";


    @Mock
    private CourtScheduleRepository courtScheduleRepository;
    @InjectMocks
    private SlotsSearchService slotsSearchService;
    private final UUID rightWingerId = randomUUID();
    private final UUID leftWingerId = randomUUID();
    private final UUID chairId = randomUUID();

    @BeforeEach
    void setUp() {
        setField(slotsSearchService, "courtScheduleRepository", courtScheduleRepository);
    }

    @Test
    void shouldSearchSlots() {
        final List<CourtSchedule> courtSchedulesExpected = List.of(courtScheduleWithMultipleJudiciaries(rightWingerId, leftWingerId, chairId));
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final JsonObject jsonObject = slotsSearchService.search(hearingSlotRequestParam);
        assertThat(stripNulls(jsonObject), is(stripNulls(toJsonObject(rightWingerId, leftWingerId, chairId))));
    }

    @Test
    void shouldStripCourtRoomFromDraftScheduleInSearchOutput() {
        // ADR-005: get.hearing.slots must not expose a courtroom for a draft (unallocated) session.
        // courtScheduleId (used to book) survives; only the room within the venue is provisional.
        final CourtSchedule draftSchedule = courtScheduleWithMultipleJudiciaries(rightWingerId, leftWingerId, chairId);
        draftSchedule.setDraft(true);
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, List.of(draftSchedule));
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final JsonObject jsonObject = slotsSearchService.search(hearingSlotRequestParam);

        final JsonObject slot = jsonObject.getJsonArray(HEARING_SLOTS).getJsonObject(0);
        assertThat(slot.containsKey("courtRoomId") && !slot.isNull("courtRoomId"), is(false));
        assertThat(slot.containsKey("courtRoomName") && !slot.isNull("courtRoomName"), is(false));
        assertThat(slot.containsKey("courtRoomNumber") && !slot.isNull("courtRoomNumber"), is(false));
        assertThat(slot.getString("courtScheduleId"), is("0000fbb0-8579-4f2b-948e-c4e48a48e3f8"));
    }

    @Test
    void shouldReturnAvailableSlotsByDuration() {
        final List<CourtSchedule> courtSchedules = List.of(getCourtScheduleWithRegularSessions());
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtSchedules);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParamWithDuration();
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final Pair<Integer, List<CourtSchedule>> availableCourtSchedules = slotsSearchService.getCourtSchedules(hearingSlotRequestParam);
        final CourtSchedule courtSchedule = availableCourtSchedules.getValue().get(0);
        assertThat(courtSchedule.getOuCode(),is(B12_JR00));
        assertThat(courtSchedule.getAvailableDuration(), is(120));
    }

    @Test
    void shouldReturnAvailableSlotBasedCourtSchedules() {
        final List<CourtSchedule> courtSchedules = List.of(getCourtScheduleWithSlotBasedSessions(),
                getCourtScheduleWithSlotBasedSessions());
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtSchedules);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParamWithDuration();
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final Pair<Integer, List<CourtSchedule>> availableCourtSchedules = slotsSearchService.getCourtSchedules(hearingSlotRequestParam);
        final CourtSchedule courtSchedule = availableCourtSchedules.getValue().get(0);
        assertThat(courtSchedule.getOuCode(),is(B12_JR00));
        assertThat(courtSchedule.getAvailableSlots(), is(2));
    }

    @Test
    void shouldSearchSlotsWhenPageSizeSent0() {
        final List<CourtSchedule> courtSchedulesExpected = List.of(courtScheduleWithMultipleJudiciaries(rightWingerId, leftWingerId, chairId));
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("0");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final JsonObject jsonObject = slotsSearchService.search(hearingSlotRequestParam);
        assertThat(stripNulls(jsonObject), is(stripNulls(toJsonObject(rightWingerId, leftWingerId, chairId))));
    }

    @Test
    void shouldHandleMultipleJudiciaries() {
        final List<CourtSchedule> courtSchedulesExpected = List.of(courtScheduleWithMultipleJudiciaries(rightWingerId, leftWingerId, chairId));
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(100, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final Pair<Integer, List<CourtSchedule>> courtSchedulesActual = slotsSearchService.getCourtSchedules(hearingSlotRequestParam);
        assertThat(courtSchedulesActual.getValue().size(), is(1));
        final CourtSchedule courtSchedule = courtSchedulesActual.getValue().get(0);
        assertThat(courtSchedule.getCourtScheduleId().toString(), is("0000fbb0-8579-4f2b-948e-c4e48a48e3f8"));
        assertThat(courtSchedule.getSessionDate(), is(LocalDate.of(2020, 12, 1)));
        assertThat(courtSchedule.getOuCode(), is("CABC90"));
        assertThat(courtSchedule.getCourtRoomId(), is(UUID_001C067D));
        assertThat(courtSchedule.getCourtRoomNumber(), is(1234));
        assertThat(courtSchedule.getCourtHouseName(), is(LIVERPOOL_MAGS_COURT));
        assertThat(courtSchedule.getCourtRoomName(), is(COURT_NAME1));
        assertThat(courtSchedule.getOperationalUnit(), is(UNN_2));
        assertThat(courtSchedule.getBusinessType(), is(BYS_2));
        assertThat(courtSchedule.getPanel(), is(PANEL_2));
        assertThat(courtSchedule.getCourtSession(), is("AM"));
        assertThat(courtSchedule.getMaxDuration(), is(182));
        assertThat(courtSchedule.getAvailableSlots(), is(125));
        assertThat(courtSchedule.getAvailableDuration(), is(182));
        assertThat(courtSchedule.getMaxSlots(), is(125));
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryDetails = courtSchedule.getJudiciaries();
        assertThat(courtScheduleJudiciaryDetails.get(0).getJudiciaryId(), is(rightWingerId.toString()));
        assertThat(courtScheduleJudiciaryDetails.get(1).getJudiciaryId(), is(leftWingerId.toString()));
        assertThat(courtScheduleJudiciaryDetails.get(2).getJudiciaryId(), is(chairId.toString()));
        assertThat(courtScheduleJudiciaryDetails.get(0).getPosition(), is("RIGHT_WINGER"));
        assertThat(courtScheduleJudiciaryDetails.get(1).getPosition(), is("LEFT_WINGER"));
        assertThat(courtScheduleJudiciaryDetails.get(2).getPosition(), is(CHAIR_2));
    }

    @Test
    void shouldNotCallGetCourtScheduleJudiciariesWhenNoListingProfileId() {
        final List<CourtSchedule> courtScheduleList = List.of(
                createCourtScheduleWithoutListingProfileId(),
                createCourtScheduleWithoutListingProfileId()
        );
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(2, courtScheduleList);

        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        slotsSearchService.getCourtSchedules(hearingSlotRequestParam);

        verify(courtScheduleRepository, times(0)).getCourtScheduleJudiciaries(any());
    }

    @Test
    void shouldSetMinHearingTimeAndMaxHearingTimeToNullInGetCourtSchedules() {
        // Given
        final CourtSchedule courtScheduleWithHearingTimes = createCourtScheduleWithHearingTimes();
        final List<CourtSchedule> courtScheduleList = List.of(courtScheduleWithHearingTimes);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtScheduleList);

        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        // When
        final Pair<Integer, List<CourtSchedule>> result = slotsSearchService.getCourtSchedules(hearingSlotRequestParam);

        // Then
        assertThat(result.getValue(), hasSize(1));
        final CourtSchedule returnedCourtSchedule = result.getValue().get(0);
        assertThat(returnedCourtSchedule.getMinHearingTime(), is(nullValue()));
        assertThat(returnedCourtSchedule.getMaxHearingTime(), is(nullValue()));
    }

    private CourtSchedule createCourtScheduleWithoutListingProfileId() {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .listingProfileId(null);
    }

    private CourtSchedule createCourtScheduleWithHearingTimes() {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .listingProfileId(randomUUID().toString())
                .sessionDate(parse("2025-03-01"))
                .ouCode(B12_JR00)
                .courtRoomId(UUID_001C067D)
                .courtRoomNumber(1234)
                .courtHouseName("Test Court House")
                .courtHouseId(UUID_0B9417B8)
                .courtRoomName("Test Court Room")
                .operationalUnit(UNN_2)
                .businessType(BYS_2)
                .businessDescription("Test Business")
                .panel(PANEL_2)
                .courtSession("AM")
                .maxDuration(120)
                .availableSlots(2)
                .availableDuration(120)
                .maxSlots(2)
                .judiciaries(List.of(buildJudiciary(randomUUID(), CHAIR_2)))
                .active(true)
                .sessionStartTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(LocalTime.parse(VALUE_10_00).atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of(UTC_2)).toInstant()))
                .sessionEndTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(LocalTime.parse(VALUE_12_00).atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of(UTC_2)).toInstant()))
                .overbookingAllowed(true)
                .minHearingTime("09:00")
                .maxHearingTime(VALUE_12_00);
    }


    private CourtSchedule courtScheduleWithMultipleJudiciaries(final UUID rightWingerId, final UUID leftWingerId, final UUID chairId) {
        final List<CourtScheduleJudiciary> courtScheduleJudiciaries = new ArrayList<>();
        final CourtScheduleJudiciary rightWinger = buildJudiciary(rightWingerId, "RIGHT_WINGER");
        final CourtScheduleJudiciary leftWinger = buildJudiciary(leftWingerId, "LEFT_WINGER");
        final CourtScheduleJudiciary chair = buildJudiciary(chairId, CHAIR_2);
        courtScheduleJudiciaries.add(rightWinger);
        courtScheduleJudiciaries.add(leftWinger);
        courtScheduleJudiciaries.add(chair);
        return courtSchedule(courtScheduleJudiciaries);
    }

    private CourtScheduleJudiciary buildJudiciary(final UUID id, final String position) {
        return new CourtScheduleJudiciary()
                .judiciaryId(id.toString())
                .position(position);
    }

    private CourtSchedule courtSchedule(final List<CourtScheduleJudiciary> courtScheduleJudiciary) {
        return new CourtSchedule()
                .courtScheduleId("0000fbb0-8579-4f2b-948e-c4e48a48e3f8")
                .listingProfileId(null)
                .sessionDate(parse("2020-12-01"))
                .ouCode("CABC90")
                .courtRoomId(UUID_001C067D)
                .courtRoomNumber(1234)
                .courtHouseName(LIVERPOOL_MAGS_COURT)
                .courtHouseId(UUID_0B9417B8)
                .courtRoomName(COURT_NAME1)
                .operationalUnit(UNN_2)
                .businessType(BYS_2)
                .businessDescription(null)
                .panel(PANEL_2)
                .courtSession("AM")
                .maxDuration(182)
                .availableSlots(125)
                .availableDuration(182)
                .maxSlots(125)
                .judiciaries(courtScheduleJudiciary)
                .active(true)
                .sessionStartTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(LocalTime.parse(VALUE_10_00).atDate(LocalDate.of(2020, 12, 1)).atZone(ZoneId.of(UTC_2)).toInstant()))
                .sessionEndTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(LocalTime.parse("13:00").atDate(LocalDate.of(2020, 12, 1)).atZone(ZoneId.of(UTC_2)).toInstant()))
                .nationalBreakTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(TimezoneUtils.calculateNationalBreakTime(LocalDate.of(2020, 12, 1))))
                .overbookingAllowed(true)
                .draft(false)
                .jurisdiction(MAGISTRATES.getJurisdiction())
                .minHearingTime("09:00")
                .maxHearingTime(VALUE_12_00);
    }

    private CourtSchedule getCourtScheduleWithRegularSessions() {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .listingProfileId(null)
                .sessionDate(parse("2025-03-01"))
                .ouCode(B12_JR00)
                .courtRoomId(UUID_001C067D)
                .courtRoomNumber(1234)
                .courtHouseName(LIVERPOOL_MAGS_COURT)
                .courtHouseId(UUID_0B9417B8)
                .courtRoomName(COURT_NAME1)
                .operationalUnit(UNN_2)
                .businessType(BYS_2)
                .businessDescription(null)
                .panel(PANEL_2)
                .courtSession("AM")
                .maxDuration(120)
                .availableSlots(2)
                .availableDuration(120)
                .maxSlots(2)
                .judiciaries(List.of(buildJudiciary(randomUUID(),CHAIR_2)))
                .active(true)
                .sessionStartTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(LocalTime.parse(VALUE_10_00).atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of(UTC_2)).toInstant()))
                .sessionEndTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(LocalTime.parse(VALUE_12_00).atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of(UTC_2)).toInstant()))
                .overbookingAllowed(true);
    }

    private CourtSchedule getCourtScheduleWithSlotBasedSessions() {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .listingProfileId(null)
                .sessionDate(parse("2025-03-01"))
                .ouCode(B12_JR00)
                .courtRoomId(UUID_001C067D)
                .courtRoomNumber(1234)
                .courtHouseName(LIVERPOOL_MAGS_COURT)
                .courtHouseId(UUID_0B9417B8)
                .courtRoomName(COURT_NAME1)
                .slotBased(true)
                .operationalUnit(UNN_2)
                .businessType(BYS_2)
                .businessDescription(null)
                .panel(PANEL_2)
                .courtSession("AM")
                .maxDuration(120)
                .availableSlots(2)
                .availableDuration(120)
                .maxSlots(2)
                .judiciaries(List.of(buildJudiciary(randomUUID(),CHAIR_2)))
                .active(true)
                .sessionStartTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(LocalTime.parse(VALUE_10_00).atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of(UTC_2)).toInstant()))
                .sessionEndTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(LocalTime.parse(VALUE_12_00).atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of(UTC_2)).toInstant()))
                .overbookingAllowed(true)
                .draft(false)
                .jurisdiction(MAGISTRATES.getJurisdiction());
    }

    private HearingSlotRequestParam createRequestParam(final String pageSize) {
        return new HearingSlotRequestParam(ADULT_2, LocalDate.now().toString(), LocalDate.now().toString(),
                Instant.now().toString(),  null, "BA124", pageSize, "1", null, null, null, null, false,null,false,"100", null, null);
    }

    private HearingSlotRequestParam createRequestParamWithDuration() {
        return new HearingSlotRequestParam(ADULT_2, LocalDate.now().toString(), LocalDate.now().toString(),
                Instant.now().toString(), null, B12_JR00, "10", "1", null, null, null, null, false, null, false,"20", null, null);
    }

    private JsonObject toJsonObject(final UUID judiciaryId1, final UUID judiciaryId2, final UUID judiciaryId3) {
        final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        String source = fileToString("/test-data/courtscheduler.get.slots-search-response.json");
        source = source.replace("JUDICIARY_ID_1", judiciaryId1.toString());
        source = source.replace("JUDICIARY_ID_2", judiciaryId2.toString());
        source = source.replace("JUDICIARY_ID_3", judiciaryId3.toString());
        return stringToJsonObjectConverter.convert(source);
    }

    // Recursively drops JSON null entries from objects and arrays so the
    // assertion is agnostic to whether a field is absent or explicitly null —
    // either is a valid representation of "no value" and the test shouldn't
    // care which one the converter happens to emit.
    private static JsonObject stripNulls(final JsonObject obj) {
        final JsonObjectBuilder out = Json.createObjectBuilder();
        obj.forEach((k, v) -> {
            if (v.getValueType() != JsonValue.ValueType.NULL) {
                out.add(k, stripNulls(v));
            }
        });
        return out.build();
    }

    private static JsonValue stripNulls(final JsonValue value) {
        return switch (value.getValueType()) {
            case OBJECT -> stripNulls((JsonObject) value);
            case ARRAY -> {
                final JsonArrayBuilder arr = Json.createArrayBuilder();
                for (final JsonValue v : (JsonArray) value) {
                    if (v.getValueType() != JsonValue.ValueType.NULL) {
                        arr.add(stripNulls(v));
                    }
                }
                yield arr.build();
            }
            default -> value;
        };
    }

    // Tests for overbookingFilter method
    @Test
    void shouldIncludeAllSchedulesWhenOverbookingAllowed() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createCourtScheduleWithOverbookingAllowed(true, false, 0, 0, 0, 0, 0, 0),
            createCourtScheduleWithOverbookingAllowed(true, true, 5, 10, 0, 0, 0, 0)
        );
        final boolean showOverbookedSlots = false;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(2));
        assertThat(result, is(courtSchedules));
    }

    @Test
    void shouldIncludeAllSchedulesWhenShowOverbookedSlotsIsTrue() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createCourtScheduleWithOverbookingAllowed(false, false, 0, 0, 0, 0, 0, 0),
            createCourtScheduleWithOverbookingAllowed(false, true, 5, 10, 0, 0, 0, 0)
        );
        final boolean showOverbookedSlots = true;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(2));
        assertThat(result, is(courtSchedules));
    }

    @Test
    void shouldIncludeSlotBasedScheduleWithAvailableSlots() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createSlotBasedCourtSchedule(false, false, 3, 5) // 3 booked out of 5 max slots
        );
        final boolean showOverbookedSlots = false;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldExcludeSlotBasedScheduleWithNoAvailableSlots() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createSlotBasedCourtSchedule(false, false, 5, 5) // 5 booked out of 5 max slots (full)
        );
        final boolean showOverbookedSlots = false;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldIncludeAllDaySplitScheduleWithSufficientMorningAfternoonDuration() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createAllDaySplitCourtSchedule(false, false, 100, 80, 20, 10, 0, 0) // 100+80-20-10=150 available, need 60
        );
        final boolean showOverbookedSlots = false;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldIncludeAllDaySplitScheduleWithSufficientTotalDuration() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createAllDaySplitCourtSchedule(false, false, 100, 100, 20, 30, 200, 50) // 200-50=150 available, need 60
        );
        final boolean showOverbookedSlots = false;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldExcludeAllDaySplitScheduleWithInsufficientDuration() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createAllDaySplitCourtSchedule(false, false, 50, 30, 20, 10, 0, 0) // 50+30-20-10=50 available, need 60
        );
        final boolean showOverbookedSlots = false;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldExcludeRegularScheduleWithInsufficientDuration() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createRegularCourtSchedule(false, false, 100, 50) // 100-50=50 available, need 60
        );
        final boolean showOverbookedSlots = false;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldHandleNullDuration() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createSlotBasedCourtSchedule(false, false, 3, 5)
        );
        final boolean showOverbookedSlots = false;
        final String duration = null;

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldHandleEmptyDuration() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createSlotBasedCourtSchedule(false, false, 3, 5)
        );
        final boolean showOverbookedSlots = false;
        final String duration = "";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldHandleMixedScheduleTypes() {
        // Given
        final List<CourtSchedule> courtSchedules = List.of(
            createCourtScheduleWithOverbookingAllowed(true, false, 0, 0, 0, 0, 0, 0), // Should be included (overbooking allowed)
            createSlotBasedCourtSchedule(false, false, 3, 5), // Should be included (available slots)
            createSlotBasedCourtSchedule(false, false, 5, 5), // Should be excluded (no available slots)
            createAllDaySplitCourtSchedule(false, false, 100, 80, 20, 10, 0, 0), // Should be included (sufficient duration)
            createAllDaySplitCourtSchedule(false, false, 50, 30, 20, 10, 0, 0)  // Should be excluded (insufficient duration)
        );
        final boolean showOverbookedSlots = false;
        final String duration = "60";

        // When
        final List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(3));
        assertThat(result.get(0), is(courtSchedules.get(0))); // Overbooking allowed
        assertThat(result.get(1), is(courtSchedules.get(1))); // Available slots
        assertThat(result.get(2), is(courtSchedules.get(3))); // Sufficient duration
    }

    // Helper method to invoke the private overbookingFilter method using reflection
    private List<CourtSchedule> invokeOverbookingFilter(final List<CourtSchedule> courtSchedules, final boolean showOverbookedSlots, final String duration) {
        return ReflectionTestUtils.invokeMethod(slotsSearchService, "overbookingFilter", courtSchedules, showOverbookedSlots, duration);
    }

    // Helper methods to create test CourtSchedule objects
    private CourtSchedule createCourtScheduleWithOverbookingAllowed(final boolean isOverbookingAllowed, final boolean slotBased,
            final int maxDurationForMorning, final int maxDurationForAfternoon, final int totalBookedForMorning, final int totalBookedForAfternoon,
            final int maxDuration, final int totalBooked) {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .overbookingAllowed(isOverbookingAllowed)
                .slotBased(slotBased)
                .allDaySplit(!slotBased)
                .maxDurationForMorning(maxDurationForMorning)
                .maxDurationForAfternoon(maxDurationForAfternoon)
                .totalBookedForMorning(totalBookedForMorning)
                .totalBookedForAfternoon(totalBookedForAfternoon)
                .maxDuration(maxDuration)
                .totalBooked(totalBooked)
                .maxSlots(5);
    }

    private CourtSchedule createSlotBasedCourtSchedule(final boolean isOverbookingAllowed, final boolean allDaySplit, final int totalBooked, final int maxSlots) {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .overbookingAllowed(isOverbookingAllowed)
                .slotBased(true)
                .allDaySplit(allDaySplit)
                .totalBooked(totalBooked)
                .maxSlots(maxSlots);
    }

    private CourtSchedule createAllDaySplitCourtSchedule(final boolean isOverbookingAllowed, final boolean slotBased,
            final int maxDurationForMorning, final int maxDurationForAfternoon, final int totalBookedForMorning, final int totalBookedForAfternoon,
            final int maxDuration, final int totalBooked) {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .overbookingAllowed(isOverbookingAllowed)
                .slotBased(slotBased)
                .allDaySplit(true)
                .maxDurationForMorning(maxDurationForMorning)
                .maxDurationForAfternoon(maxDurationForAfternoon)
                .totalBookedForMorning(totalBookedForMorning)
                .totalBookedForAfternoon(totalBookedForAfternoon)
                .maxDuration(maxDuration)
                .totalBooked(totalBooked);
    }

    private CourtSchedule createRegularCourtSchedule(final boolean isOverbookingAllowed, final boolean slotBased, final int maxDuration, final int totalBooked) {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .overbookingAllowed(isOverbookingAllowed)
                .slotBased(slotBased)
                .allDaySplit(false)
                .maxDuration(maxDuration)
                .totalBooked(totalBooked);
    }

    // ---- Multiday CROWN search tests ----

    @Test
    void isMultidayCrownSearch_shouldReturnTrueForCrownWithDurationOver360() {
        final HearingSlotRequestParam param = createMultidayRequestParam(CROWN_2, VALUE_1080);
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(true));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForMagistrates() {
        final HearingSlotRequestParam param = createMultidayRequestParam("MAGISTRATES", VALUE_1080);
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForDuration360() {
        final HearingSlotRequestParam param = createMultidayRequestParam(CROWN_2, "360");
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForNullDuration() {
        final HearingSlotRequestParam param = createMultidayRequestParam(CROWN_2, null);
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForNullJurisdiction() {
        final HearingSlotRequestParam param = createMultidayRequestParam(null, VALUE_1080);
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    @Test
    void filterForMultidayAvailability_shouldFindConsecutiveDaysForThreeDayHearing() {
        // 3 consecutive calendar days: Mon 23, Tue 24, Wed 25 March 2026
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_23)));
    }

    @Test
    void filterForMultidayAvailability_shouldFindMultipleValidStartDates() {
        // Mon 23, Tue 24, Wed 25, Thu 26 - 4 consecutive weekdays
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // 23 is valid start (23,24,25); 24 is also valid start (24,25,26)
        assertThat(result, hasSize(2));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_23)));
        assertThat(result.get(1).getSessionDate(), is(parse(DATE_2026_03_24)));
    }

    @Test
    void filterForMultidayAvailability_shouldReturnSecondDayWhenFirstHasInsufficientAvailability() {
        // Mon 23 has <360 available, Tue 24, Wed 25, Thu 26 have >=360 → 24 is the first valid start
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 100, false), // 260 available
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),   // 360 available
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false),   // 360 available
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false)    // 360 available
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_24)));
    }

    @Test
    void filterForMultidayAvailability_shouldReturnEmptyWhenInsufficientConsecutiveDays() {
        // Only 2 consecutive days available (26,27), need 3 - gap on 28
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false)
                // Missing 28 March
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldAllowOverbookingExemptDays() {
        // Mon 23 has <360 but overbooking allowed, Tue 24 and Wed 25 have >=360
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 300, true),  // only 60 available, but overbooking allowed
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_23)));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByCourtRoom() {
        // Two courtrooms - only courtroom B has 3 consecutive days available
        final String courtRoomA = randomUUID().toString();
        final String courtRoomB = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomA, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomA, parse(DATE_2026_03_24), 360, 0, false),
                // courtRoomA missing Wed 25 March
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_25), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getCourtRoomId(), is(courtRoomB));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_23)));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByCourtRoom_emptyWhenFourDaysNeeded() {
        // Same data as shouldGroupByCourtRoom: courtRoomA has 2 days, courtRoomB has 3 days
        // Asking for 4 consecutive days - neither room qualifies
        final String courtRoomA = randomUUID().toString();
        final String courtRoomB = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomA, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomA, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_25), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 4, false);

        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByCourtRoom_twoDaysFromBothRooms() {
        // Same data: courtRoomA has Mon-Tue (2 days), courtRoomB has Mon-Tue-Wed (3 days)
        // Asking for 2 consecutive days - both rooms qualify
        // courtRoomA: start 23 (23→24); courtRoomB: start 23 (23→24) and start 24 (24→25)
        final String courtRoomA = randomUUID().toString();
        final String courtRoomB = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomA, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomA, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse(DATE_2026_03_25), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // courtRoomA: 23→24 valid. courtRoomB: 23→24 valid, 24→25 valid → 3 start dates
        assertThat(result, hasSize(3));
        // Results sorted by sessionDate, then courtHouseName, then courtRoomName
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_23)));
        assertThat(result.get(1).getSessionDate(), is(parse(DATE_2026_03_23)));
        assertThat(result.get(2).getSessionDate(), is(parse(DATE_2026_03_24)));
        assertThat(result.get(2).getCourtRoomId(), is(courtRoomB));
        // Both rooms appear for the 23rd
        final List<String> roomsOn23rd = result.stream()
                .filter(cs -> cs.getSessionDate().equals(parse(DATE_2026_03_23)))
                .map(CourtSchedule::getCourtRoomId).sorted().toList();
        final List<String> expectedRooms = List.of(courtRoomA, courtRoomB).stream().sorted().toList();
        assertThat(roomsOn23rd, is(expectedRooms));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByBusinessType() {
        // Same courtroom & ouCode, different business types:
        // APPLS on 08, 09, 11 (gap on 10 - NOT consecutive for 3 days)
        // LGT   on 08, 09, 10 (consecutive for 3 days)
        final String courtRoomId = randomUUID().toString();
        final String ouCode = C03_CL00;
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-11"), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, LGT_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, LGT_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_10), 360, 0, false, LGT_2, ouCode)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // Only LGT should qualify - APPLS has a gap on 10th
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getBusinessType(), is(LGT_2));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_04_08)));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByBusinessType_emptyWhenFourDaysNeeded() {
        // Same data as shouldGroupByBusinessType: APPLS has 08,09,11 (gap); LGT has 08,09,10 (3 consecutive)
        // Asking for 4 consecutive days - neither business type qualifies
        final String courtRoomId = randomUUID().toString();
        final String ouCode = C03_CL00;
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-11"), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, LGT_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, LGT_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_10), 360, 0, false, LGT_2, ouCode)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 4, false);

        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByBusinessType_twoDaysFromAPPLSAndLGT() {
        // Same data: APPLS has 08,09,11; LGT has 08,09,10
        // Asking for 2 consecutive days - APPLS 08→09 qualifies, LGT 08→09 and 09→10 qualify
        final String courtRoomId = randomUUID().toString();
        final String ouCode = C03_CL00;
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-11"), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, LGT_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, LGT_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_10), 360, 0, false, LGT_2, ouCode)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // APPLS: 08→09 valid (1 start). LGT: 08→09 valid, 09→10 valid (2 starts) → 3 total
        assertThat(result, hasSize(3));
        // Results sorted by sessionDate; two entries share the 8th (order within same date is non-deterministic)
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_04_08)));
        assertThat(result.get(1).getSessionDate(), is(parse(DATE_2026_04_08)));
        assertThat(result.get(2).getSessionDate(), is(parse(DATE_2026_04_09)));
        assertThat(result.get(2).getBusinessType(), is(LGT_2));
        // Both business types appear for the 8th
        final List<String> typesOn8th = result.stream()
                .filter(cs -> cs.getSessionDate().equals(parse(DATE_2026_04_08)))
                .map(CourtSchedule::getBusinessType).sorted().toList();
        assertThat(typesOn8th, is(List.of(APPLS_2, LGT_2)));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByOuCode() {
        // Same courtroom & businessType, different ouCodes
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, LGT_2, C03_CL00),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, LGT_2, C03_CL00),
                // C03CL00 missing 10th
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, LGT_2, C05_LV00),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, LGT_2, C05_LV00),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_10), 360, 0, false, LGT_2, C05_LV00)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getOuCode(), is(C05_LV00));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_04_08)));
    }

    @Test
    void filterForMultidayAvailability_shouldReturnBothBusinessTypesWhenBothHaveConsecutiveDays() {
        // Both APPLS and LGT have 3 consecutive days in the same courtroom
        final String courtRoomId = randomUUID().toString();
        final String ouCode = C03_CL00;
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_10), 360, 0, false, APPLS_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_08), 360, 0, false, LGT_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_09), 360, 0, false, LGT_2, ouCode),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_10), 360, 0, false, LGT_2, ouCode)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // Both business types qualify - each has 3 consecutive days
        assertThat(result, hasSize(2));
    }

    @Test
    void filterForMultidayAvailability_shouldHandleTwoDayHearing() {
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // 23→24 valid, 24→25 valid
        assertThat(result, hasSize(2));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_23)));
        assertThat(result.get(1).getSessionDate(), is(parse(DATE_2026_03_24)));
    }

    @Test
    void filterForMultidayAvailability_shouldFindConsecutiveBusinessDaysAcrossWeekend() {
        // Thu 26, Fri 27, Mon 30 March 2026 — 3 business days spanning weekend
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_30), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // Thu→Fri→Mon is 3 consecutive business days
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_26)));
    }

    @Test
    void filterForMultidayAvailability_shouldFindTwoDayHearingAcrossWeekend() {
        // Fri 27, Mon 30 March 2026 — 2 business days spanning weekend
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_30), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // Fri→Mon is 2 consecutive business days
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_27)));
    }

    @Test
    void filterForMultidayAvailability_shouldFindFiveDayHearingSpanningWeekend() {
        // Wed 25, Thu 26, Fri 27, Mon 30, Tue 31 March 2026 — 5 business days
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_30), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-31"), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 5, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_25)));
    }

    @Test
    void filterForMultidayAvailability_shouldOrderByDateThenCourthouseNameThenCourtroomNumberThenCourtroomName() {
        // SPRDT bug: multiday results were sorted by date only, so within a date the courthouse/room
        // order was driven by HashMap iteration. Fix mirrors the single-day SQL ORDER BY
        // (session_start, court_house_name, court_room_number, court_room_name).
        // Numeric room ordering ensures Courtroom 2 sorts before Courtroom 10 (lexicographic sort would invert this).
        final String blackfriarsRoom1 = randomUUID().toString();
        final String blackfriarsRoom2 = randomUUID().toString();
        final String blackfriarsRoom10 = randomUUID().toString();
        final String aylesburyRoom1 = randomUUID().toString();

        // Built in deliberately scrambled order; the sort must reorder them.
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtScheduleWithNames(blackfriarsRoom10, 10, parse(DATE_2026_05_07),
                        BLACKFRIARS_CROWN_COURT, "Courtroom 10"),
                createMultidayCourtScheduleWithNames(blackfriarsRoom10, 10, parse(DATE_2026_05_08),
                        BLACKFRIARS_CROWN_COURT, "Courtroom 10"),
                createMultidayCourtScheduleWithNames(blackfriarsRoom2, 2, parse(DATE_2026_05_07),
                        BLACKFRIARS_CROWN_COURT, "Courtroom 2"),
                createMultidayCourtScheduleWithNames(blackfriarsRoom2, 2, parse(DATE_2026_05_08),
                        BLACKFRIARS_CROWN_COURT, "Courtroom 2"),
                createMultidayCourtScheduleWithNames(aylesburyRoom1, 1, parse(DATE_2026_05_07),
                        "Aylesbury Crown Court", COURTROOM_1),
                createMultidayCourtScheduleWithNames(aylesburyRoom1, 1, parse(DATE_2026_05_08),
                        "Aylesbury Crown Court", COURTROOM_1),
                createMultidayCourtScheduleWithNames(blackfriarsRoom1, 1, parse(DATE_2026_05_07),
                        BLACKFRIARS_CROWN_COURT, COURTROOM_1),
                createMultidayCourtScheduleWithNames(blackfriarsRoom1, 1, parse(DATE_2026_05_08),
                        BLACKFRIARS_CROWN_COURT, COURTROOM_1)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // 4 valid start dates, all on 2026-05-07.
        // Expected order: Aylesbury Courtroom 1 → Blackfriars Courtroom 1 → Courtroom 2 → Courtroom 10
        // (numeric room sort places 2 before 10; lexicographic would put 10 before 2)
        assertThat(result, hasSize(4));
        assertThat(result.get(0).getCourtHouseName(), is("Aylesbury Crown Court"));
        assertThat(result.get(0).getCourtRoomName(), is(COURTROOM_1));
        assertThat(result.get(1).getCourtHouseName(), is(BLACKFRIARS_CROWN_COURT));
        assertThat(result.get(1).getCourtRoomName(), is(COURTROOM_1));
        assertThat(result.get(2).getCourtHouseName(), is(BLACKFRIARS_CROWN_COURT));
        assertThat(result.get(2).getCourtRoomName(), is("Courtroom 2"));
        assertThat(result.get(3).getCourtHouseName(), is(BLACKFRIARS_CROWN_COURT));
        assertThat(result.get(3).getCourtRoomName(), is("Courtroom 10"));
    }

    private CourtSchedule createMultidayCourtScheduleWithNames(final String courtRoomId, final int courtRoomNumber,
                                                                final LocalDate sessionDate,
                                                                final String courtHouseName, final String courtRoomName) {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .courtRoomNumber(courtRoomNumber)
                .sessionDate(sessionDate)
                .maxDuration(360)
                .totalBooked(0)
                .availableDuration(360)
                .overbookingAllowed(false)
                .slotBased(false)
                .allDaySplit(false)
                .courtSession("AD")
                .jurisdiction(CROWN.getJurisdiction())
                .panel(ADULT_2)
                .ouCode(C20_CO00)
                .courtHouseName(courtHouseName)
                .courtRoomName(courtRoomName)
                .active(true);
    }

    @Test
    void filterForMultidayAvailability_shouldReturnEmptyWhenMondayMissingAfterWeekend() {
        // Thu 26, Fri 27 only — need 3 days but Mon 30 is missing
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // Thu→Fri→Mon(missing) = invalid
        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldReturnEmptyForEmptySchedules() {
        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(new ArrayList<>(), 3, false);
        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldHandleGapInMiddle() {
        // 23, 24 available, 25 missing, 26, 27 available
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                // 25 missing
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // 23→24→missing 25 = invalid. 24→missing 25 = invalid. 26→27→missing 28 = invalid.
        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldHandleAllDaySplitAvailability() {
        // allDaySplit session: morning 200 + afternoon 200 - booked 20+20 = 360 available
        final String courtRoomId = randomUUID().toString();
        final CourtSchedule adSplitDay1 = new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .sessionDate(parse("2026-03-26"))
                .allDaySplit(true)
                .maxDurationForMorning(200)
                .maxDurationForAfternoon(200)
                .totalBookedForMorning(20)
                .totalBookedForAfternoon(20)
                .maxDuration(400)
                .totalBooked(40)
                .overbookingAllowed(false)
                .ouCode(C20_CO00);
        final CourtSchedule day2 = createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false);

        final List<CourtSchedule> schedules = new ArrayList<>(List.of(adSplitDay1, day2));
        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse(DATE_2026_03_26)));
    }

    @Test
    void filterForMultidayAvailability_shouldRejectAllDaySplitWithInsufficientAvailability() {
        // allDaySplit session: morning 200 + afternoon 200 - booked 100+100 = 200 available (<360)
        final String courtRoomId = randomUUID().toString();
        final CourtSchedule adSplitDay1 = new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .sessionDate(parse("2026-03-26"))
                .allDaySplit(true)
                .maxDurationForMorning(200)
                .maxDurationForAfternoon(200)
                .totalBookedForMorning(100)
                .totalBookedForAfternoon(100)
                .maxDuration(400)
                .totalBooked(200)
                .overbookingAllowed(false)
                .ouCode(C20_CO00);
        final CourtSchedule day2 = createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false);

        final List<CourtSchedule> schedules = new ArrayList<>(List.of(adSplitDay1, day2));
        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldNotSkipGapDays() {
        // Mon 23, Wed 25 - gap on Tue 24 means NOT consecutive
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false)
        );

        final List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // 23→24 missing = invalid. 25→26 missing = invalid.
        assertThat(result, is(empty()));
    }

    @Test
    void whenGetMultidayCourtSchedules_shouldFetchUnpaginatedAndReturnPaginatedResults() {
        final String courtRoomId = randomUUID().toString();
        // Mon 23 to Fri 27 = 5 consecutive weekdays
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = createMultidayRequestParamWithPagination(CROWN_2, VALUE_1080, "2", "1");

        final Pair<Integer, List<CourtSchedule>> result = slotsSearchService.getMultidayCourtSchedules(param);

        // Valid starts: 23(23,24,25), 24(24,25,26), 25(25,26,27) = 3 total; page 1 with pageSize 2 = first 2
        assertThat(result.getKey(), is(3));
        assertThat(result.getValue(), hasSize(2));
        assertThat(result.getValue().get(0).getSessionDate(), is(parse(DATE_2026_03_23)));
        assertThat(result.getValue().get(1).getSessionDate(), is(parse(DATE_2026_03_24)));
    }

    @Test
    void whenGetMultidayCourtSchedules_shouldReturnSecondPage() {
        final String courtRoomId = randomUUID().toString();
        // Mon 23 to Fri 27 = 5 consecutive weekdays
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = createMultidayRequestParamWithPagination(CROWN_2, VALUE_1080, "2", "2");

        final Pair<Integer, List<CourtSchedule>> result = slotsSearchService.getMultidayCourtSchedules(param);

        // 3 total valid starts, page 2 with pageSize 2 = last 1
        assertThat(result.getKey(), is(3));
        assertThat(result.getValue(), hasSize(1));
        assertThat(result.getValue().get(0).getSessionDate(), is(parse(DATE_2026_03_25)));
    }

    @Test
    void search_shouldUseMultidayPathForCrownOver360() {
        final String courtRoomId = randomUUID().toString();
        // Mon 23, Tue 24, Wed 25 = 3 consecutive weekdays
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = createMultidayRequestParamWithPagination(CROWN_2, VALUE_1080, "10", "1");

        final JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt(RESULTS), is(1));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).size(), is(1));
    }

    @Test
    void search_shouldUseNormalPathForMagistratesOver360() {
        final List<CourtSchedule> schedules = List.of(getCourtScheduleWithRegularSessions());
        when(courtScheduleRepository.getCourtSchedules(any())).thenReturn(Pair.of(1, schedules));

        final HearingSlotRequestParam param = createMultidayRequestParamWithPagination("MAGISTRATES", VALUE_1080, "10", "1");

        final JsonObject jsonObject = slotsSearchService.search(param);

        // Non-multiday path, normal result
        assertThat(jsonObject.getInt(RESULTS), is(1));
    }

    @Test
    void search_shouldReturnCorrectPageCountForMultidayCrown() {
        final String courtRoomId = randomUUID().toString();
        // Mon 23 to Fri 27 = 5 consecutive weekdays, duration 1080 (3 days) => valid starts: 23,24,25 = 3
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = createMultidayRequestParamWithPagination(CROWN_2, VALUE_1080, "2", "1");

        final JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt(RESULTS), is(3));
        assertThat(jsonObject.getInt(PAGE_COUNT), is(2));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).size(), is(2));
    }

    @Test
    void search_shouldReturnSecondPageForMultidayCrown() {
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = createMultidayRequestParamWithPagination(CROWN_2, VALUE_1080, "2", "2");

        final JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt(RESULTS), is(3));
        assertThat(jsonObject.getInt(PAGE_COUNT), is(2));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).size(), is(1));
    }

    @Test
    void search_shouldReturnEmptyPageWhenPageNumberExceedsTotalPages() {
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = createMultidayRequestParamWithPagination(CROWN_2, VALUE_1080, "10", "5");

        final JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt(RESULTS), is(1));
        assertThat(jsonObject.getInt(PAGE_COUNT), is(1));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).size(), is(0));
    }

    @Test
    void search_shouldReturnAllResultsWhenPageSizeExceedsTotalForMultidayCrown() {
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_23), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_24), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_25), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_26), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        // pageSize=10 like the example curl, duration=720 (2-day hearing)
        final HearingSlotRequestParam param = createMultidayRequestParamWithPagination(CROWN_2, VALUE_720, "10", "1");

        final JsonObject jsonObject = slotsSearchService.search(param);

        // 2-day hearing across 5 consecutive days => valid starts: 23,24,25,26 = 4
        assertThat(jsonObject.getInt(RESULTS), is(4));
        assertThat(jsonObject.getInt(PAGE_COUNT), is(1));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).size(), is(4));
    }

    // ---- Bug fix: Crown multiday with high duration returns no sessions when consecutive days insufficient ----

    @Test
    void search_shouldReturnNoResultsForCrownMultidayWhenDurationExceedsAvailableConsecutiveDays() {
        // Reproduces issue where availableDurationMins=3600 (10 days) is sent for Crown multiday,
        // but only 4 consecutive days exist (March 30 - April 2) with max_duration_mins=360
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_30), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-31"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-01"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-02"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(schedules);

        // duration=3600 means 3600/360=10 days needed, but only 4 consecutive days available
        final HearingSlotRequestParam param = new HearingSlotRequestParam(
                ADULT_YOUTH, "2026-03-13", "2026-04-30",
                null, null, "C20BI00", "10", "1", null, null, null, "AD", false, null, false,
                "3600", "FINAL", CROWN_2);

        final JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt(RESULTS), is(0));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).size(), is(0));
    }

    // ---- Bug fix: multiday results must not exceed sessionEndDate ----

    @Test
    void search_shouldNotReturnSessionsBeyondEndDateForMultidayCrown() {
        // Reproduces real bug: endDate=2026-04-21, duration=720 (2 days).
        // Sessions exist Apr 14-17, Apr 20-23. Apr 22 and 23 are valid 2-day starts
        // (22→23, 23→24 if 24 existed) but must be excluded because they're after endDate.
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_14), 360, 40, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_15), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_16), 360, 20, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_17), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-20"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_21), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-22"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-23"), 360, 0, true)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = new HearingSlotRequestParam(
                ADULT_YOUTH, DATE_2026_04_14, DATE_2026_04_21,
                null, null, C01_CY00, "10", "1", null, null, null, "AD", false, null, false,
                VALUE_720, "FINAL", CROWN_2);

        final JsonObject jsonObject = slotsSearchService.search(param);

        // Valid 2-day starts within range: 14(→15), 15(→16), 16(→17), 17(→20), 20(→21), 21(→22)
        // Apr 22 and 23 are beyond endDate and must NOT appear
        assertThat(jsonObject.getInt(RESULTS), is(6));
        jsonObject.getJsonArray(HEARING_SLOTS).forEach(slot -> {
            final String sessionDate = slot.asJsonObject().getString(SESSION_DATE);
            assertThat("Session " + sessionDate + " should not be after endDate",
                    parse(sessionDate).isAfter(parse(DATE_2026_04_21)), is(false));
        });
    }

    @Test
    void search_shouldReturnSessionAtEndDateButNotBeyondForMultidayCrown() {
        // endDate = Wed Apr 15. Sessions: Mon 14, Tue 15, Wed 16, Thu 17.
        // For a 2-day hearing: 14(→15)✓, 15(→16)✓ (15 = endDate, valid), 16(→17) ✗ beyond endDate
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_14), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_15), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_16), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_17), 360, 0, true)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = new HearingSlotRequestParam(
                ADULT_2, DATE_2026_04_14, DATE_2026_04_15,
                null, null, C01_CY00, "10", "1", null, null, null, "AD", false, null, false,
                VALUE_720, null, CROWN_2);

        final JsonObject jsonObject = slotsSearchService.search(param);

        // Only 14 and 15 should appear (both valid 2-day starts within endDate)
        assertThat(jsonObject.getInt(RESULTS), is(2));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).getJsonObject(0).getString(SESSION_DATE), is(DATE_2026_04_14));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).getJsonObject(1).getString(SESSION_DATE), is(DATE_2026_04_15));
    }

    @Test
    void search_shouldExcludeLookaheadDaysAcrossWeekendForMultidayCrown() {
        // endDate = Fri Apr 17. Sessions: Thu 16, Fri 17, Mon 20, Tue 21.
        // For a 2-day hearing: 16(→17)✓, 17(→Mon 20)✓ (17=endDate, weekend skipped), 20(→21) ✗ beyond endDate
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_16), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_17), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-20"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_04_21), 360, 0, true)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = new HearingSlotRequestParam(
                ADULT_2, DATE_2026_04_16, DATE_2026_04_17,
                null, null, C01_CY00, "10", "1", null, null, null, "AD", false, null, false,
                VALUE_720, null, CROWN_2);

        final JsonObject jsonObject = slotsSearchService.search(param);

        // 16 and 17 are valid start dates (17's second day is Mon 20 — look-ahead validates but Mon 20 isn't returned)
        assertThat(jsonObject.getInt(RESULTS), is(2));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).getJsonObject(0).getString(SESSION_DATE), is(DATE_2026_04_16));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).getJsonObject(1).getString(SESSION_DATE), is(DATE_2026_04_17));
    }

    // ---- Multiday: availability starting at endDate and narrow range with large duration ----

    @Test
    void search_shouldReturnResultWhenAvailabilityStartsAtEndDate() {
        // Range: Thu 26 to Fri 27 March. Sessions exist on Fri 27, Mon 30, Tue 31.
        // The endDate (Fri 27) is a valid start for a 3-day hearing (Fri→Mon→Tue).
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_27), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse(DATE_2026_03_30), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-31"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = new HearingSlotRequestParam(
                ADULT_2, DATE_2026_03_26, DATE_2026_03_27,
                null, null, C20_CO00, "10", "1", null, null, null, "AD", false, null, false,
                VALUE_1080, null, CROWN_2);

        final JsonObject jsonObject = slotsSearchService.search(param);

        // Fri 27 is a valid start: Fri 27→Mon 30→Tue 31
        assertThat(jsonObject.getInt(RESULTS), is(1));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).size(), is(1));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).getJsonObject(0).getString(SESSION_DATE), is(DATE_2026_03_27));
    }

    @Test
    void search_shouldReturnResultForNarrowRangeWithLargeDuration() {
        // Simulates the curl: startDate=Thu 2026-04-09, endDate=Sun 2026-04-12, duration=5400 (15 days)
        // Sessions exist for 15 consecutive business days starting Apr 9
        final String courtRoomId = randomUUID().toString();
        final List<CourtSchedule> allSchedules = new ArrayList<>();
        // Generate 15 consecutive business days starting Thu Apr 9
        LocalDate date = parse(DATE_2026_04_09);
        int businessDays = 0;
        while (businessDays < 15) {
            if (date.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && date.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                allSchedules.add(createMultidayCourtSchedule(courtRoomId, date, 360, 0, false));
                businessDays++;
            }
            date = date.plusDays(1);
        }
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        final HearingSlotRequestParam param = new HearingSlotRequestParam(
                ADULT_YOUTH, DATE_2026_04_09, "2026-04-12",
                null, null, C01_CY00, "10", "1", null, null, null, "AD", false, null, false,
                "5400", "FINAL", CROWN_2);

        final JsonObject jsonObject = slotsSearchService.search(param);

        // Apr 9 (Thu) is a valid start for 15 consecutive business days
        assertThat(jsonObject.getInt(RESULTS), is(1));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).size(), is(1));
        assertThat(jsonObject.getJsonArray(HEARING_SLOTS).getJsonObject(0).getString(SESSION_DATE), is(DATE_2026_04_09));
    }

    // ---- Bug fix: Pagination total count propagated from DB ----

    @Test
    void whenGetCourtSchedules_shouldReturnDbTotalCountNotFilteredPageCount() {
        // DB returns total count 100 with a page of 10 records
        final List<CourtSchedule> pageSchedules = List.of(
                getCourtScheduleWithRegularSessions(),
                getCourtScheduleWithRegularSessions()
        );
        final int dbTotalCount = 100;
        when(courtScheduleRepository.getCourtSchedules(any())).thenReturn(Pair.of(dbTotalCount, pageSchedules));

        final HearingSlotRequestParam param = createRequestParam("10");
        final Pair<Integer, List<CourtSchedule>> result = slotsSearchService.getCourtSchedules(param);

        // Total count should come from DB, not from filtered page size
        assertThat(result.getKey(), is(dbTotalCount));
    }

    @Test
    void search_shouldReturnCorrectPageCountFromDbTotal() {
        // DB returns total count 50 with a page of 10 records
        final List<CourtSchedule> pageSchedules = List.of(getCourtScheduleWithRegularSessions());
        final int dbTotalCount = 50;
        when(courtScheduleRepository.getCourtSchedules(any())).thenReturn(Pair.of(dbTotalCount, pageSchedules));

        final HearingSlotRequestParam param = createRequestParam("10");
        final JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt(RESULTS), is(dbTotalCount));
        assertThat(jsonObject.getInt(PAGE_COUNT), is(5)); // ceil(50/10) = 5
    }

    // ---- SPRDT-1276: CROWN >360 forces courtSession=AD and isSlotBased=false ----

    @Test
    void multidayCrownSearch_shouldForceCourtSessionToAdWhenCallerSendsAm() {
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(List.of());

        // The reported defect: a 720-minute CROWN search that asks for AM sessions.
        slotsSearchService.search(new HearingSlotRequestParam(
                ADULT_YOUTH, DATE_2026_08_17, DATE_2026_08_17,
                null, null, C13_BR00, VALUE_500, "1", null, null, null, "AM", true, null, true,
                VALUE_720, DRAFT_2, CROWN_2));

        assertThat(capturedMultidayRequest().courtSession(), is("AD"));
        assertThat(capturedMultidayRequest().isSlotBased(), is(false));
    }

    @Test
    void multidayCrownSearch_shouldDefaultCourtSessionToAdWhenCallerSendsNone() {
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(List.of());

        // An absent courtSession previously meant "no court_session predicate at all", which is
        // how AM sessions reached the response for the exact curl on the ticket.
        slotsSearchService.search(new HearingSlotRequestParam(
                ADULT_YOUTH, DATE_2026_08_17, DATE_2026_08_17,
                null, null, C13_BR00, VALUE_500, "1", null, null, null, null, null, null, true,
                VALUE_720, DRAFT_2, CROWN_2));

        assertThat(capturedMultidayRequest().courtSession(), is("AD"));
        assertThat(capturedMultidayRequest().isSlotBased(), is(false));
    }

    @Test
    void multidayCrownSearch_shouldForceSessionDefaultsEvenWhenBusinessTypeSupplied() {
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(List.of());

        slotsSearchService.search(new HearingSlotRequestParam(
                ADULT_YOUTH, DATE_2026_08_17, DATE_2026_08_17,
                null, null, C13_BR00, VALUE_500, "1", null, null, "TRIAL", "PM", true, null, true,
                VALUE_720, DRAFT_2, CROWN_2));

        final HearingSlotRequestParam forwarded = capturedMultidayRequest();
        assertThat(forwarded.courtSession(), is("AD"));
        assertThat(forwarded.isSlotBased(), is(false));
        // businessType still narrows the search — it is no longer an alternative to isSlotBased.
        assertThat(forwarded.businessType(), is("TRIAL"));
    }

    @Test
    void singleDayCrownSearch_shouldLeaveCallerSessionParamsUntouched() {
        when(courtScheduleRepository.getCourtSchedules(any())).thenReturn(Pair.of(0, List.of()));

        // 360 is not multiday (the threshold is strictly greater than), so nothing is forced.
        final HearingSlotRequestParam param = new HearingSlotRequestParam(
                ADULT_YOUTH, DATE_2026_08_17, DATE_2026_08_17,
                null, null, C13_BR00, VALUE_500, "1", null, null, null, "AM", true, null, true,
                "360", DRAFT_2, CROWN_2);

        slotsSearchService.search(param);

        final ArgumentCaptor<HearingSlotRequestParam> captor =
                ArgumentCaptor.forClass(HearingSlotRequestParam.class);
        verify(courtScheduleRepository).getCourtSchedules(captor.capture());
        assertThat(captor.getValue().courtSession(), is("AM"));
        assertThat(captor.getValue().isSlotBased(), is(true));
    }

    @Test
    void multidayMagistratesSearch_shouldLeaveCallerSessionParamsUntouched() {
        when(courtScheduleRepository.getCourtSchedules(any())).thenReturn(Pair.of(0, List.of()));

        // The forcing is CROWN-only — MAGISTRATES never enters the multiday branch.
        slotsSearchService.search(new HearingSlotRequestParam(
                ADULT_YOUTH, DATE_2026_08_17, DATE_2026_08_17,
                null, null, C13_BR00, VALUE_500, "1", null, null, null, "AM", true, null, true,
                VALUE_720, DRAFT_2, "MAGISTRATES"));

        final ArgumentCaptor<HearingSlotRequestParam> captor =
                ArgumentCaptor.forClass(HearingSlotRequestParam.class);
        verify(courtScheduleRepository).getCourtSchedules(captor.capture());
        assertThat(captor.getValue().courtSession(), is("AM"));
        assertThat(captor.getValue().isSlotBased(), is(true));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForMalformedDuration() {
        // The predicate is now evaluated on every hearing-slots query, MAGISTRATES included,
        // so a duration it cannot parse must answer false rather than throw.
        final HearingSlotRequestParam param = new HearingSlotRequestParam(
                ADULT_YOUTH, DATE_2026_08_17, DATE_2026_08_17,
                null, null, C13_BR00, VALUE_500, "1", null, null, null, null, null, null, true,
                "not-a-number", DRAFT_2, CROWN_2);

        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    private HearingSlotRequestParam capturedMultidayRequest() {
        final ArgumentCaptor<HearingSlotRequestParam> captor =
                ArgumentCaptor.forClass(HearingSlotRequestParam.class);
        verify(courtScheduleRepository).getMultidayHearingSlotCandidates(captor.capture(), anyInt());
        return captor.getValue();
    }

    // ---- Multiday helper methods ----

    private CourtSchedule createMultidayCourtSchedule(final String courtRoomId, final LocalDate sessionDate,
                                                       final int maxDuration, final int totalBooked, final boolean overbookingAllowed) {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .courtRoomNumber(1)
                .sessionDate(sessionDate)
                .maxDuration(maxDuration)
                .totalBooked(totalBooked)
                .availableDuration(maxDuration - totalBooked)
                .overbookingAllowed(overbookingAllowed)
                .slotBased(false)
                .allDaySplit(false)
                .courtSession("AD")
                .jurisdiction(CROWN.getJurisdiction())
                .panel(ADULT_2)
                .ouCode(C20_CO00)
                .courtHouseName("Crown Court")
                .active(true);
    }

    private CourtSchedule createMultidayCourtSchedule(final String courtRoomId, final LocalDate sessionDate,
                                                       final int maxDuration, final int totalBooked, final boolean overbookingAllowed,
                                                       final String businessType, final String ouCode) {
        return new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .courtRoomId(courtRoomId)
                .courtRoomNumber(1)
                .sessionDate(sessionDate)
                .maxDuration(maxDuration)
                .totalBooked(totalBooked)
                .availableDuration(maxDuration - totalBooked)
                .overbookingAllowed(overbookingAllowed)
                .slotBased(false)
                .allDaySplit(false)
                .courtSession("AD")
                .jurisdiction(CROWN.getJurisdiction())
                .panel(ADULT_2)
                .ouCode(ouCode)
                .businessType(businessType)
                .courtHouseName("Crown Court")
                .active(true);
    }

    private HearingSlotRequestParam createMultidayRequestParam(final String jurisdiction, final String duration) {
        return new HearingSlotRequestParam(ADULT_2, LocalDate.now().toString(), LocalDate.now().plusDays(14).toString(),
                null, null, C20_CO00, "10", "1", null, null, null, "AD", false, null, false,
                duration, null, jurisdiction);
    }

    private HearingSlotRequestParam createMultidayRequestParamWithPagination(final String jurisdiction, final String duration,
                                                                              final String pageSize, final String pageNumber) {
        return new HearingSlotRequestParam(ADULT_2, DATE_2026_03_26, DATE_2026_04_10,
                null, null, C20_CO00, pageSize, pageNumber, null, null, null, "AD", false, null, false,
                duration, null, jurisdiction);
    }

}
