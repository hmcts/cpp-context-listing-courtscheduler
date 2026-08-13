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
import static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
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

@ExtendWith(MockitoExtension.class)
class SlotsSearchServiceTest {

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
        courtSchedulesExpected.stream().map(CourtSchedule::getJudiciaries);
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
        draftSchedule.setIsDraft(true);
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, List.of(draftSchedule));
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final JsonObject jsonObject = slotsSearchService.search(hearingSlotRequestParam);

        final JsonObject slot = jsonObject.getJsonArray("hearingSlots").getJsonObject(0);
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
        assertThat(courtSchedule.getOuCode(),is("B12JR00"));
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
        assertThat(courtSchedule.getOuCode(),is("B12JR00"));
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
        assertThat(courtSchedule.getCourtRoomId(), is("001c067d-eaca-4ce5-ad90-a366ef3e4bb6"));
        assertThat(courtSchedule.getCourtRoomNumber(), is(1234));
        assertThat(courtSchedule.getCourtHouseName(), is("Liverpool Mags Court"));
        assertThat(courtSchedule.getCourtRoomName(), is("Court name1"));
        assertThat(courtSchedule.getOperationalUnit(), is("UNN"));
        assertThat(courtSchedule.getBusinessType(), is("BYS"));
        assertThat(courtSchedule.getPanel(), is("PANEL"));
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
        assertThat(courtScheduleJudiciaryDetails.get(2).getPosition(), is("CHAIR"));
    }

    @Test
    void shouldNotCallGetCourtScheduleJudiciariesWhenNoListingProfileId() {
        List<CourtSchedule> courtScheduleList = List.of(
                createCourtScheduleWithoutListingProfileId(),
                createCourtScheduleWithoutListingProfileId()
        );
        HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(2, courtScheduleList);

        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        slotsSearchService.getCourtSchedules(hearingSlotRequestParam);

        verify(courtScheduleRepository, times(0)).getCourtScheduleJudiciaries(any());
    }

    @Test
    void shouldSetMinHearingTimeAndMaxHearingTimeToNullInGetCourtSchedules() {
        // Given
        CourtSchedule courtScheduleWithHearingTimes = createCourtScheduleWithHearingTimes();
        List<CourtSchedule> courtScheduleList = List.of(courtScheduleWithHearingTimes);
        HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtScheduleList);

        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        // When
        Pair<Integer, List<CourtSchedule>> result = slotsSearchService.getCourtSchedules(hearingSlotRequestParam);

        // Then
        assertThat(result.getValue(), hasSize(1));
        CourtSchedule returnedCourtSchedule = result.getValue().get(0);
        assertThat(returnedCourtSchedule.getMinHearingTime(), is(nullValue()));
        assertThat(returnedCourtSchedule.getMaxHearingTime(), is(nullValue()));
    }

    private CourtSchedule createCourtScheduleWithoutListingProfileId() {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withListingProfileId(null)
                .build();
    }

    private CourtSchedule createCourtScheduleWithHearingTimes() {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withListingProfileId(randomUUID().toString())
                .withSessionDate(parse("2025-03-01"))
                .withOuCode("B12JR00")
                .withCourtRoomId("001c067d-eaca-4ce5-ad90-a366ef3e4bb6")
                .withCourtRoomNumber(1234)
                .withCourtHouseName("Test Court House")
                .withCourtHouseId("0b9417b8-91b4-385d-9e01-069855777c4f")
                .withCourtRoomName("Test Court Room")
                .withOperationalUnit("UNN")
                .withBusinessType("BYS")
                .withBusinessDescription("Test Business")
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(120)
                .withAvailableSlots(2)
                .withAvailableDuration(120)
                .withMaxSlots(2)
                .withJudiciaries(List.of(buildJudiciary(randomUUID(), "CHAIR")))
                .withActive(true)
                .withSessionStartTime(Date.from(LocalTime.parse("10:00").atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of("UTC")).toInstant()))
                .withSessionEndTime(Date.from(LocalTime.parse("12:00").atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of("UTC")).toInstant()))
                .withIsOverbookingAllowed(true)
                .withMinHearingTime("09:00")
                .withMaxHearingTime("12:00")
                .build();
    }


    private CourtSchedule courtScheduleWithMultipleJudiciaries(UUID rightWingerId, UUID leftWingerId, UUID chairId) {
        final List<CourtScheduleJudiciary> courtScheduleJudiciaries = new ArrayList<>();
        final CourtScheduleJudiciary rightWinger = buildJudiciary(rightWingerId, "RIGHT_WINGER");
        final CourtScheduleJudiciary leftWinger = buildJudiciary(leftWingerId, "LEFT_WINGER");
        final CourtScheduleJudiciary chair = buildJudiciary(chairId, "CHAIR");
        courtScheduleJudiciaries.add(rightWinger);
        courtScheduleJudiciaries.add(leftWinger);
        courtScheduleJudiciaries.add(chair);
        return courtSchedule(courtScheduleJudiciaries);
    }

    private CourtScheduleJudiciary buildJudiciary(final UUID id, final String position) {
        return judiciary()
                .withJudiciaryId(id.toString())
                .withPosition(position)
                .build();
    }

    private CourtSchedule courtSchedule(final List<CourtScheduleJudiciary> courtScheduleJudiciary) {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId("0000fbb0-8579-4f2b-948e-c4e48a48e3f8")
                .withListingProfileId(null)
                .withSessionDate(parse("2020-12-01"))
                .withOuCode("CABC90")
                .withCourtRoomId("001c067d-eaca-4ce5-ad90-a366ef3e4bb6")
                .withCourtRoomNumber(1234)
                .withCourtHouseName("Liverpool Mags Court")
                .withCourtHouseId("0b9417b8-91b4-385d-9e01-069855777c4f")
                .withCourtRoomName("Court name1")
                .withOperationalUnit("UNN")
                .withBusinessType("BYS")
                .withBusinessDescription(null)
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(182)
                .withAvailableSlots(125)
                .withAvailableDuration(182)
                .withMaxSlots(125)
                .withJudiciaries(courtScheduleJudiciary)
                .withActive(true)
                .withSessionStartTime(Date.from(LocalTime.parse("10:00").atDate(LocalDate.of(2020, 12, 1)).atZone(ZoneId.of("UTC")).toInstant()))
                .withSessionEndTime(Date.from(LocalTime.parse("13:00").atDate(LocalDate.of(2020, 12, 1)).atZone(ZoneId.of("UTC")).toInstant()))
                .withNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(LocalDate.of(2020, 12, 1)))
                .withIsOverbookingAllowed(true)
                .withIsDraft(false)
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .withMinHearingTime("09:00")
                .withMaxHearingTime("12:00")
                .build();
    }

    private CourtSchedule getCourtScheduleWithRegularSessions() {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withListingProfileId(null)
                .withSessionDate(parse("2025-03-01"))
                .withOuCode("B12JR00")
                .withCourtRoomId("001c067d-eaca-4ce5-ad90-a366ef3e4bb6")
                .withCourtRoomNumber(1234)
                .withCourtHouseName("Liverpool Mags Court")
                .withCourtHouseId("0b9417b8-91b4-385d-9e01-069855777c4f")
                .withCourtRoomName("Court name1")
                .withOperationalUnit("UNN")
                .withBusinessType("BYS")
                .withBusinessDescription(null)
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(120)
                .withAvailableSlots(2)
                .withAvailableDuration(120)
                .withMaxSlots(2)
                .withJudiciaries(List.of(buildJudiciary(randomUUID(),"CHAIR")))
                .withActive(true)
                .withSessionStartTime(Date.from(LocalTime.parse("10:00").atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of("UTC")).toInstant()))
                .withSessionEndTime(Date.from(LocalTime.parse("12:00").atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of("UTC")).toInstant()))
                .withIsOverbookingAllowed(true)
                .build();
    }

    private CourtSchedule getCourtScheduleWithSlotBasedSessions() {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withListingProfileId(null)
                .withSessionDate(parse("2025-03-01"))
                .withOuCode("B12JR00")
                .withCourtRoomId("001c067d-eaca-4ce5-ad90-a366ef3e4bb6")
                .withCourtRoomNumber(1234)
                .withCourtHouseName("Liverpool Mags Court")
                .withCourtHouseId("0b9417b8-91b4-385d-9e01-069855777c4f")
                .withCourtRoomName("Court name1")
                .withSlotBased(true)
                .withOperationalUnit("UNN")
                .withBusinessType("BYS")
                .withBusinessDescription(null)
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(120)
                .withAvailableSlots(2)
                .withAvailableDuration(120)
                .withMaxSlots(2)
                .withJudiciaries(List.of(buildJudiciary(randomUUID(),"CHAIR")))
                .withActive(true)
                .withSessionStartTime(Date.from(LocalTime.parse("10:00").atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of("UTC")).toInstant()))
                .withSessionEndTime(Date.from(LocalTime.parse("12:00").atDate(LocalDate.of(2025, 3, 12)).atZone(ZoneId.of("UTC")).toInstant()))
                .withIsOverbookingAllowed(true)
                .withIsDraft(false)
                .withJurisdiction(MAGISTRATES.getJurisdiction())
                .build();
    }

    private HearingSlotRequestParam createRequestParam(String pageSize) {
        return new HearingSlotRequestParam("ADULT", LocalDate.now().toString(), LocalDate.now().toString(),
                Instant.now().toString(),  null, "BA124", pageSize, "1", null, null, null, null, false,null,false,"100", null, null);
    }

    private HearingSlotRequestParam createRequestParamWithDuration() {
        return new HearingSlotRequestParam("ADULT", LocalDate.now().toString(), LocalDate.now().toString(),
                Instant.now().toString(), null, "B12JR00", "10", "1", null, null, null, null, false, null, false,"20", null, null);
    }

    private JsonObject toJsonObject(UUID judiciaryId1, UUID judiciaryId2, UUID judiciaryId3) {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
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
        List<CourtSchedule> courtSchedules = List.of(
            createCourtScheduleWithOverbookingAllowed(true, false, 0, 0, 0, 0, 0, 0),
            createCourtScheduleWithOverbookingAllowed(true, true, 5, 10, 0, 0, 0, 0)
        );
        boolean showOverbookedSlots = false;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(2));
        assertThat(result, is(courtSchedules));
    }

    @Test
    void shouldIncludeAllSchedulesWhenShowOverbookedSlotsIsTrue() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createCourtScheduleWithOverbookingAllowed(false, false, 0, 0, 0, 0, 0, 0),
            createCourtScheduleWithOverbookingAllowed(false, true, 5, 10, 0, 0, 0, 0)
        );
        boolean showOverbookedSlots = true;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(2));
        assertThat(result, is(courtSchedules));
    }

    @Test
    void shouldIncludeSlotBasedScheduleWithAvailableSlots() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createSlotBasedCourtSchedule(false, false, 3, 5) // 3 booked out of 5 max slots
        );
        boolean showOverbookedSlots = false;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldExcludeSlotBasedScheduleWithNoAvailableSlots() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createSlotBasedCourtSchedule(false, false, 5, 5) // 5 booked out of 5 max slots (full)
        );
        boolean showOverbookedSlots = false;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldIncludeAllDaySplitScheduleWithSufficientMorningAfternoonDuration() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createAllDaySplitCourtSchedule(false, false, 100, 80, 20, 10, 0, 0) // 100+80-20-10=150 available, need 60
        );
        boolean showOverbookedSlots = false;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldIncludeAllDaySplitScheduleWithSufficientTotalDuration() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createAllDaySplitCourtSchedule(false, false, 100, 100, 20, 30, 200, 50) // 200-50=150 available, need 60
        );
        boolean showOverbookedSlots = false;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldExcludeAllDaySplitScheduleWithInsufficientDuration() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createAllDaySplitCourtSchedule(false, false, 50, 30, 20, 10, 0, 0) // 50+30-20-10=50 available, need 60
        );
        boolean showOverbookedSlots = false;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldExcludeRegularScheduleWithInsufficientDuration() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createRegularCourtSchedule(false, false, 100, 50) // 100-50=50 available, need 60
        );
        boolean showOverbookedSlots = false;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(0));
    }

    @Test
    void shouldHandleNullDuration() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createSlotBasedCourtSchedule(false, false, 3, 5)
        );
        boolean showOverbookedSlots = false;
        String duration = null;

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldHandleEmptyDuration() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createSlotBasedCourtSchedule(false, false, 3, 5)
        );
        boolean showOverbookedSlots = false;
        String duration = "";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(1));
        assertThat(result.get(0), is(courtSchedules.get(0)));
    }

    @Test
    void shouldHandleMixedScheduleTypes() {
        // Given
        List<CourtSchedule> courtSchedules = List.of(
            createCourtScheduleWithOverbookingAllowed(true, false, 0, 0, 0, 0, 0, 0), // Should be included (overbooking allowed)
            createSlotBasedCourtSchedule(false, false, 3, 5), // Should be included (available slots)
            createSlotBasedCourtSchedule(false, false, 5, 5), // Should be excluded (no available slots)
            createAllDaySplitCourtSchedule(false, false, 100, 80, 20, 10, 0, 0), // Should be included (sufficient duration)
            createAllDaySplitCourtSchedule(false, false, 50, 30, 20, 10, 0, 0)  // Should be excluded (insufficient duration)
        );
        boolean showOverbookedSlots = false;
        String duration = "60";

        // When
        List<CourtSchedule> result = invokeOverbookingFilter(courtSchedules, showOverbookedSlots, duration);

        // Then
        assertThat(result.size(), is(3));
        assertThat(result.get(0), is(courtSchedules.get(0))); // Overbooking allowed
        assertThat(result.get(1), is(courtSchedules.get(1))); // Available slots
        assertThat(result.get(2), is(courtSchedules.get(3))); // Sufficient duration
    }

    // Helper method to invoke the private overbookingFilter method using reflection
    @SuppressWarnings("unchecked")
    private List<CourtSchedule> invokeOverbookingFilter(List<CourtSchedule> courtSchedules, boolean showOverbookedSlots, String duration) {
        try {
            java.lang.reflect.Method method = SlotsSearchService.class.getDeclaredMethod("overbookingFilter", List.class, boolean.class, String.class);
            method.setAccessible(true);
            return (List<CourtSchedule>) method.invoke(slotsSearchService, courtSchedules, showOverbookedSlots, duration);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke overbookingFilter method", e);
        }
    }

    // Helper methods to create test CourtSchedule objects
    private CourtSchedule createCourtScheduleWithOverbookingAllowed(boolean isOverbookingAllowed, boolean slotBased,
            int maxDurationForMorning, int maxDurationForAfternoon, int totalBookedForMorning, int totalBookedForAfternoon,
            int maxDuration, int totalBooked) {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withIsOverbookingAllowed(isOverbookingAllowed)
                .withSlotBased(slotBased)
                .withAllDaySplit(!slotBased)
                .withMaxDurationForMorning(maxDurationForMorning)
                .withMaxDurationForAfternoon(maxDurationForAfternoon)
                .withTotalBookedForMorning(totalBookedForMorning)
                .withTotalBookedForAfternoon(totalBookedForAfternoon)
                .withMaxDuration(maxDuration)
                .withTotalBooked(totalBooked)
                .withMaxSlots(5)
                .build();
    }

    private CourtSchedule createSlotBasedCourtSchedule(boolean isOverbookingAllowed, boolean allDaySplit, int totalBooked, int maxSlots) {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withIsOverbookingAllowed(isOverbookingAllowed)
                .withSlotBased(true)
                .withAllDaySplit(allDaySplit)
                .withTotalBooked(totalBooked)
                .withMaxSlots(maxSlots)
                .build();
    }

    private CourtSchedule createAllDaySplitCourtSchedule(boolean isOverbookingAllowed, boolean slotBased,
            int maxDurationForMorning, int maxDurationForAfternoon, int totalBookedForMorning, int totalBookedForAfternoon,
            int maxDuration, int totalBooked) {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withIsOverbookingAllowed(isOverbookingAllowed)
                .withSlotBased(slotBased)
                .withAllDaySplit(true)
                .withMaxDurationForMorning(maxDurationForMorning)
                .withMaxDurationForAfternoon(maxDurationForAfternoon)
                .withTotalBookedForMorning(totalBookedForMorning)
                .withTotalBookedForAfternoon(totalBookedForAfternoon)
                .withMaxDuration(maxDuration)
                .withTotalBooked(totalBooked)
                .build();
    }

    private CourtSchedule createRegularCourtSchedule(boolean isOverbookingAllowed, boolean slotBased, int maxDuration, int totalBooked) {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withIsOverbookingAllowed(isOverbookingAllowed)
                .withSlotBased(slotBased)
                .withAllDaySplit(false)
                .withMaxDuration(maxDuration)
                .withTotalBooked(totalBooked)
                .build();
    }

    // ---- Multiday CROWN search tests ----

    @Test
    void isMultidayCrownSearch_shouldReturnTrueForCrownWithDurationOver360() {
        HearingSlotRequestParam param = createMultidayRequestParam("CROWN", "1080");
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(true));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForMagistrates() {
        HearingSlotRequestParam param = createMultidayRequestParam("MAGISTRATES", "1080");
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForDuration360() {
        HearingSlotRequestParam param = createMultidayRequestParam("CROWN", "360");
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForNullDuration() {
        HearingSlotRequestParam param = createMultidayRequestParam("CROWN", null);
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    @Test
    void isMultidayCrownSearch_shouldReturnFalseForNullJurisdiction() {
        HearingSlotRequestParam param = createMultidayRequestParam(null, "1080");
        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    @Test
    void filterForMultidayAvailability_shouldFindConsecutiveDaysForThreeDayHearing() {
        // 3 consecutive calendar days: Mon 23, Tue 24, Wed 25 March 2026
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-23")));
    }

    @Test
    void filterForMultidayAvailability_shouldFindMultipleValidStartDates() {
        // Mon 23, Tue 24, Wed 25, Thu 26 - 4 consecutive weekdays
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // 23 is valid start (23,24,25); 24 is also valid start (24,25,26)
        assertThat(result, hasSize(2));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-23")));
        assertThat(result.get(1).getSessionDate(), is(parse("2026-03-24")));
    }

    @Test
    void filterForMultidayAvailability_shouldReturnSecondDayWhenFirstHasInsufficientAvailability() {
        // Mon 23 has <360 available, Tue 24, Wed 25, Thu 26 have >=360 → 24 is the first valid start
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 100, false), // 260 available
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),   // 360 available
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false),   // 360 available
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false)    // 360 available
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-24")));
    }

    @Test
    void filterForMultidayAvailability_shouldReturnEmptyWhenInsufficientConsecutiveDays() {
        // Only 2 consecutive days available (26,27), need 3 - gap on 28
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false)
                // Missing 28 March
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldAllowOverbookingExemptDays() {
        // Mon 23 has <360 but overbooking allowed, Tue 24 and Wed 25 have >=360
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 300, true),  // only 60 available, but overbooking allowed
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-23")));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByCourtRoom() {
        // Two courtrooms - only courtroom B has 3 consecutive days available
        String courtRoomA = randomUUID().toString();
        String courtRoomB = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomA, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomA, parse("2026-03-24"), 360, 0, false),
                // courtRoomA missing Wed 25 March
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-25"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getCourtRoomId(), is(courtRoomB));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-23")));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByCourtRoom_emptyWhenFourDaysNeeded() {
        // Same data as shouldGroupByCourtRoom: courtRoomA has 2 days, courtRoomB has 3 days
        // Asking for 4 consecutive days - neither room qualifies
        String courtRoomA = randomUUID().toString();
        String courtRoomB = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomA, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomA, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-25"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 4, false);

        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByCourtRoom_twoDaysFromBothRooms() {
        // Same data: courtRoomA has Mon-Tue (2 days), courtRoomB has Mon-Tue-Wed (3 days)
        // Asking for 2 consecutive days - both rooms qualify
        // courtRoomA: start 23 (23→24); courtRoomB: start 23 (23→24) and start 24 (24→25)
        String courtRoomA = randomUUID().toString();
        String courtRoomB = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomA, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomA, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomB, parse("2026-03-25"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // courtRoomA: 23→24 valid. courtRoomB: 23→24 valid, 24→25 valid → 3 start dates
        assertThat(result, hasSize(3));
        // Results sorted by sessionDate, then courtHouseName, then courtRoomName
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-23")));
        assertThat(result.get(1).getSessionDate(), is(parse("2026-03-23")));
        assertThat(result.get(2).getSessionDate(), is(parse("2026-03-24")));
        assertThat(result.get(2).getCourtRoomId(), is(courtRoomB));
        // Both rooms appear for the 23rd
        List<String> roomsOn23rd = result.stream()
                .filter(cs -> cs.getSessionDate().equals(parse("2026-03-23")))
                .map(CourtSchedule::getCourtRoomId).sorted().toList();
        List<String> expectedRooms = List.of(courtRoomA, courtRoomB).stream().sorted().toList();
        assertThat(roomsOn23rd, is(expectedRooms));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByBusinessType() {
        // Same courtroom & ouCode, different business types:
        // APPLS on 08, 09, 11 (gap on 10 - NOT consecutive for 3 days)
        // FWT   on 08, 09, 10 (consecutive for 3 days)
        String courtRoomId = randomUUID().toString();
        String ouCode = "C03CL00";
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-11"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "FWT", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "FWT", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-10"), 360, 0, false, "FWT", ouCode)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // Only FWT should qualify - APPLS has a gap on 10th
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getBusinessType(), is("FWT"));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-04-08")));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByBusinessType_emptyWhenFourDaysNeeded() {
        // Same data as shouldGroupByBusinessType: APPLS has 08,09,11 (gap); FWT has 08,09,10 (3 consecutive)
        // Asking for 4 consecutive days - neither business type qualifies
        String courtRoomId = randomUUID().toString();
        String ouCode = "C03CL00";
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-11"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "FWT", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "FWT", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-10"), 360, 0, false, "FWT", ouCode)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 4, false);

        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByBusinessType_twoDaysFromAPPLSAndFWT() {
        // Same data: APPLS has 08,09,11; FWT has 08,09,10
        // Asking for 2 consecutive days - APPLS 08→09 qualifies, FWT 08→09 and 09→10 qualify
        String courtRoomId = randomUUID().toString();
        String ouCode = "C03CL00";
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-11"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "FWT", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "FWT", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-10"), 360, 0, false, "FWT", ouCode)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // APPLS: 08→09 valid (1 start). FWT: 08→09 valid, 09→10 valid (2 starts) → 3 total
        assertThat(result, hasSize(3));
        // Results sorted by sessionDate; two entries share the 8th (order within same date is non-deterministic)
        assertThat(result.get(0).getSessionDate(), is(parse("2026-04-08")));
        assertThat(result.get(1).getSessionDate(), is(parse("2026-04-08")));
        assertThat(result.get(2).getSessionDate(), is(parse("2026-04-09")));
        assertThat(result.get(2).getBusinessType(), is("FWT"));
        // Both business types appear for the 8th
        List<String> typesOn8th = result.stream()
                .filter(cs -> cs.getSessionDate().equals(parse("2026-04-08")))
                .map(CourtSchedule::getBusinessType).sorted().toList();
        assertThat(typesOn8th, is(List.of("APPLS", "FWT")));
    }

    @Test
    void filterForMultidayAvailability_shouldGroupByOuCode() {
        // Same courtroom & businessType, different ouCodes
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "FWT", "C03CL00"),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "FWT", "C03CL00"),
                // C03CL00 missing 10th
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "FWT", "C05LV00"),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "FWT", "C05LV00"),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-10"), 360, 0, false, "FWT", "C05LV00")
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getOuCode(), is("C05LV00"));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-04-08")));
    }

    @Test
    void filterForMultidayAvailability_shouldReturnBothBusinessTypesWhenBothHaveConsecutiveDays() {
        // Both APPLS and FWT have 3 consecutive days in the same courtroom
        String courtRoomId = randomUUID().toString();
        String ouCode = "C03CL00";
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-10"), 360, 0, false, "APPLS", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-08"), 360, 0, false, "FWT", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-09"), 360, 0, false, "FWT", ouCode),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-10"), 360, 0, false, "FWT", ouCode)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // Both business types qualify - each has 3 consecutive days
        assertThat(result, hasSize(2));
    }

    @Test
    void filterForMultidayAvailability_shouldHandleTwoDayHearing() {
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // 23→24 valid, 24→25 valid
        assertThat(result, hasSize(2));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-23")));
        assertThat(result.get(1).getSessionDate(), is(parse("2026-03-24")));
    }

    @Test
    void filterForMultidayAvailability_shouldFindConsecutiveBusinessDaysAcrossWeekend() {
        // Thu 26, Fri 27, Mon 30 March 2026 — 3 business days spanning weekend
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-30"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // Thu→Fri→Mon is 3 consecutive business days
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-26")));
    }

    @Test
    void filterForMultidayAvailability_shouldFindTwoDayHearingAcrossWeekend() {
        // Fri 27, Mon 30 March 2026 — 2 business days spanning weekend
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-30"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // Fri→Mon is 2 consecutive business days
        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-27")));
    }

    @Test
    void filterForMultidayAvailability_shouldFindFiveDayHearingSpanningWeekend() {
        // Wed 25, Thu 26, Fri 27, Mon 30, Tue 31 March 2026 — 5 business days
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-30"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-31"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 5, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-25")));
    }

    @Test
    void filterForMultidayAvailability_shouldOrderByDateThenCourthouseNameThenCourtroomNumberThenCourtroomName() {
        // SPRDT bug: multiday results were sorted by date only, so within a date the courthouse/room
        // order was driven by HashMap iteration. Fix mirrors the single-day SQL ORDER BY
        // (session_start, court_house_name, court_room_number, court_room_name).
        // Numeric room ordering ensures Courtroom 2 sorts before Courtroom 10 (lexicographic sort would invert this).
        String blackfriarsRoom1 = randomUUID().toString();
        String blackfriarsRoom2 = randomUUID().toString();
        String blackfriarsRoom10 = randomUUID().toString();
        String aylesburyRoom1 = randomUUID().toString();

        // Built in deliberately scrambled order; the sort must reorder them.
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtScheduleWithNames(blackfriarsRoom10, 10, parse("2026-05-07"),
                        "Blackfriars Crown Court", "Courtroom 10"),
                createMultidayCourtScheduleWithNames(blackfriarsRoom10, 10, parse("2026-05-08"),
                        "Blackfriars Crown Court", "Courtroom 10"),
                createMultidayCourtScheduleWithNames(blackfriarsRoom2, 2, parse("2026-05-07"),
                        "Blackfriars Crown Court", "Courtroom 2"),
                createMultidayCourtScheduleWithNames(blackfriarsRoom2, 2, parse("2026-05-08"),
                        "Blackfriars Crown Court", "Courtroom 2"),
                createMultidayCourtScheduleWithNames(aylesburyRoom1, 1, parse("2026-05-07"),
                        "Aylesbury Crown Court", "Courtroom 1"),
                createMultidayCourtScheduleWithNames(aylesburyRoom1, 1, parse("2026-05-08"),
                        "Aylesbury Crown Court", "Courtroom 1"),
                createMultidayCourtScheduleWithNames(blackfriarsRoom1, 1, parse("2026-05-07"),
                        "Blackfriars Crown Court", "Courtroom 1"),
                createMultidayCourtScheduleWithNames(blackfriarsRoom1, 1, parse("2026-05-08"),
                        "Blackfriars Crown Court", "Courtroom 1")
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // 4 valid start dates, all on 2026-05-07.
        // Expected order: Aylesbury Courtroom 1 → Blackfriars Courtroom 1 → Courtroom 2 → Courtroom 10
        // (numeric room sort places 2 before 10; lexicographic would put 10 before 2)
        assertThat(result, hasSize(4));
        assertThat(result.get(0).getCourtHouseName(), is("Aylesbury Crown Court"));
        assertThat(result.get(0).getCourtRoomName(), is("Courtroom 1"));
        assertThat(result.get(1).getCourtHouseName(), is("Blackfriars Crown Court"));
        assertThat(result.get(1).getCourtRoomName(), is("Courtroom 1"));
        assertThat(result.get(2).getCourtHouseName(), is("Blackfriars Crown Court"));
        assertThat(result.get(2).getCourtRoomName(), is("Courtroom 2"));
        assertThat(result.get(3).getCourtHouseName(), is("Blackfriars Crown Court"));
        assertThat(result.get(3).getCourtRoomName(), is("Courtroom 10"));
    }

    private CourtSchedule createMultidayCourtScheduleWithNames(String courtRoomId, int courtRoomNumber,
                                                                LocalDate sessionDate,
                                                                String courtHouseName, String courtRoomName) {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withCourtRoomNumber(courtRoomNumber)
                .withSessionDate(sessionDate)
                .withMaxDuration(360)
                .withTotalBooked(0)
                .withAvailableDuration(360)
                .withIsOverbookingAllowed(false)
                .withSlotBased(false)
                .withAllDaySplit(false)
                .withCourtSession("AD")
                .withJurisdiction(CROWN.getJurisdiction())
                .withPanel("ADULT")
                .withOuCode("C20CO00")
                .withCourtHouseName(courtHouseName)
                .withCourtRoomName(courtRoomName)
                .withActive(true)
                .build();
    }

    @Test
    void filterForMultidayAvailability_shouldReturnEmptyWhenMondayMissingAfterWeekend() {
        // Thu 26, Fri 27 only — need 3 days but Mon 30 is missing
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // Thu→Fri→Mon(missing) = invalid
        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldReturnEmptyForEmptySchedules() {
        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(new ArrayList<>(), 3, false);
        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldHandleGapInMiddle() {
        // 23, 24 available, 25 missing, 26, 27 available
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                // 25 missing
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 3, false);

        // 23→24→missing 25 = invalid. 24→missing 25 = invalid. 26→27→missing 28 = invalid.
        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldHandleAllDaySplitAvailability() {
        // allDaySplit session: morning 200 + afternoon 200 - booked 20+20 = 360 available
        String courtRoomId = randomUUID().toString();
        CourtSchedule adSplitDay1 = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withSessionDate(parse("2026-03-26"))
                .withAllDaySplit(true)
                .withMaxDurationForMorning(200)
                .withMaxDurationForAfternoon(200)
                .withTotalBookedForMorning(20)
                .withTotalBookedForAfternoon(20)
                .withMaxDuration(400)
                .withTotalBooked(40)
                .withIsOverbookingAllowed(false)
                .withOuCode("C20CO00")
                .build();
        CourtSchedule day2 = createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false);

        List<CourtSchedule> schedules = new ArrayList<>(List.of(adSplitDay1, day2));
        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getSessionDate(), is(parse("2026-03-26")));
    }

    @Test
    void filterForMultidayAvailability_shouldRejectAllDaySplitWithInsufficientAvailability() {
        // allDaySplit session: morning 200 + afternoon 200 - booked 100+100 = 200 available (<360)
        String courtRoomId = randomUUID().toString();
        CourtSchedule adSplitDay1 = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withSessionDate(parse("2026-03-26"))
                .withAllDaySplit(true)
                .withMaxDurationForMorning(200)
                .withMaxDurationForAfternoon(200)
                .withTotalBookedForMorning(100)
                .withTotalBookedForAfternoon(100)
                .withMaxDuration(400)
                .withTotalBooked(200)
                .withIsOverbookingAllowed(false)
                .withOuCode("C20CO00")
                .build();
        CourtSchedule day2 = createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false);

        List<CourtSchedule> schedules = new ArrayList<>(List.of(adSplitDay1, day2));
        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        assertThat(result, is(empty()));
    }

    @Test
    void filterForMultidayAvailability_shouldNotSkipGapDays() {
        // Mon 23, Wed 25 - gap on Tue 24 means NOT consecutive
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false)
        );

        List<CourtSchedule> result = slotsSearchService.filterForMultidayAvailability(schedules, 2, false);

        // 23→24 missing = invalid. 25→26 missing = invalid.
        assertThat(result, is(empty()));
    }

    @Test
    void getMultidayCourtSchedules_shouldFetchUnpaginatedAndReturnPaginatedResults() {
        String courtRoomId = randomUUID().toString();
        // Mon 23 to Fri 27 = 5 consecutive weekdays
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = createMultidayRequestParamWithPagination("CROWN", "1080", "2", "1");

        Pair<Integer, List<CourtSchedule>> result = slotsSearchService.getMultidayCourtSchedules(param);

        // Valid starts: 23(23,24,25), 24(24,25,26), 25(25,26,27) = 3 total; page 1 with pageSize 2 = first 2
        assertThat(result.getKey(), is(3));
        assertThat(result.getValue(), hasSize(2));
        assertThat(result.getValue().get(0).getSessionDate(), is(parse("2026-03-23")));
        assertThat(result.getValue().get(1).getSessionDate(), is(parse("2026-03-24")));
    }

    @Test
    void getMultidayCourtSchedules_shouldReturnSecondPage() {
        String courtRoomId = randomUUID().toString();
        // Mon 23 to Fri 27 = 5 consecutive weekdays
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = createMultidayRequestParamWithPagination("CROWN", "1080", "2", "2");

        Pair<Integer, List<CourtSchedule>> result = slotsSearchService.getMultidayCourtSchedules(param);

        // 3 total valid starts, page 2 with pageSize 2 = last 1
        assertThat(result.getKey(), is(3));
        assertThat(result.getValue(), hasSize(1));
        assertThat(result.getValue().get(0).getSessionDate(), is(parse("2026-03-25")));
    }

    @Test
    void search_shouldUseMultidayPathForCrownOver360() {
        String courtRoomId = randomUUID().toString();
        // Mon 23, Tue 24, Wed 25 = 3 consecutive weekdays
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = createMultidayRequestParamWithPagination("CROWN", "1080", "10", "1");

        JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt("results"), is(1));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(1));
    }

    @Test
    void search_shouldUseNormalPathForMagistratesOver360() {
        List<CourtSchedule> schedules = List.of(getCourtScheduleWithRegularSessions());
        when(courtScheduleRepository.getCourtSchedules(any())).thenReturn(Pair.of(1, schedules));

        HearingSlotRequestParam param = createMultidayRequestParamWithPagination("MAGISTRATES", "1080", "10", "1");

        JsonObject jsonObject = slotsSearchService.search(param);

        // Non-multiday path, normal result
        assertThat(jsonObject.getInt("results"), is(1));
    }

    @Test
    void search_shouldReturnCorrectPageCountForMultidayCrown() {
        String courtRoomId = randomUUID().toString();
        // Mon 23 to Fri 27 = 5 consecutive weekdays, duration 1080 (3 days) => valid starts: 23,24,25 = 3
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = createMultidayRequestParamWithPagination("CROWN", "1080", "2", "1");

        JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt("results"), is(3));
        assertThat(jsonObject.getInt("pageCount"), is(2));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(2));
    }

    @Test
    void search_shouldReturnSecondPageForMultidayCrown() {
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = createMultidayRequestParamWithPagination("CROWN", "1080", "2", "2");

        JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt("results"), is(3));
        assertThat(jsonObject.getInt("pageCount"), is(2));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(1));
    }

    @Test
    void search_shouldReturnEmptyPageWhenPageNumberExceedsTotalPages() {
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = createMultidayRequestParamWithPagination("CROWN", "1080", "10", "5");

        JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt("results"), is(1));
        assertThat(jsonObject.getInt("pageCount"), is(1));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(0));
    }

    @Test
    void search_shouldReturnAllResultsWhenPageSizeExceedsTotalForMultidayCrown() {
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-23"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-24"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-25"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-26"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        // pageSize=10 like the example curl, duration=720 (2-day hearing)
        HearingSlotRequestParam param = createMultidayRequestParamWithPagination("CROWN", "720", "10", "1");

        JsonObject jsonObject = slotsSearchService.search(param);

        // 2-day hearing across 5 consecutive days => valid starts: 23,24,25,26 = 4
        assertThat(jsonObject.getInt("results"), is(4));
        assertThat(jsonObject.getInt("pageCount"), is(1));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(4));
    }

    // ---- Bug fix: Crown multiday with high duration returns no sessions when consecutive days insufficient ----

    @Test
    void search_shouldReturnNoResultsForCrownMultidayWhenDurationExceedsAvailableConsecutiveDays() {
        // Reproduces issue where availableDurationMins=3600 (10 days) is sent for Crown multiday,
        // but only 4 consecutive days exist (March 30 - April 2) with max_duration_mins=360
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> schedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-30"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-31"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-01"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-02"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(schedules);

        // duration=3600 means 3600/360=10 days needed, but only 4 consecutive days available
        HearingSlotRequestParam param = new HearingSlotRequestParam(
                "ADULT,YOUTH", "2026-03-13", "2026-04-30",
                null, null, "C20BI00", "10", "1", null, null, null, "AD", false, null, false,
                "3600", "FINAL", "CROWN");

        JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt("results"), is(0));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(0));
    }

    // ---- Bug fix: multiday results must not exceed sessionEndDate ----

    @Test
    void search_shouldNotReturnSessionsBeyondEndDateForMultidayCrown() {
        // Reproduces real bug: endDate=2026-04-21, duration=720 (2 days).
        // Sessions exist Apr 14-17, Apr 20-23. Apr 22 and 23 are valid 2-day starts
        // (22→23, 23→24 if 24 existed) but must be excluded because they're after endDate.
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-14"), 360, 40, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-15"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-16"), 360, 20, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-17"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-20"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-21"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-22"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-23"), 360, 0, true)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = new HearingSlotRequestParam(
                "ADULT,YOUTH", "2026-04-14", "2026-04-21",
                null, null, "C01CY00", "10", "1", null, null, null, "AD", false, null, false,
                "720", "FINAL", "CROWN");

        JsonObject jsonObject = slotsSearchService.search(param);

        // Valid 2-day starts within range: 14(→15), 15(→16), 16(→17), 17(→20), 20(→21), 21(→22)
        // Apr 22 and 23 are beyond endDate and must NOT appear
        assertThat(jsonObject.getInt("results"), is(6));
        jsonObject.getJsonArray("hearingSlots").forEach(slot -> {
            String sessionDate = slot.asJsonObject().getString("sessionDate");
            assertThat("Session " + sessionDate + " should not be after endDate",
                    parse(sessionDate).isAfter(parse("2026-04-21")), is(false));
        });
    }

    @Test
    void search_shouldReturnSessionAtEndDateButNotBeyondForMultidayCrown() {
        // endDate = Wed Apr 15. Sessions: Mon 14, Tue 15, Wed 16, Thu 17.
        // For a 2-day hearing: 14(→15)✓, 15(→16)✓ (15 = endDate, valid), 16(→17) ✗ beyond endDate
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-14"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-15"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-16"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-17"), 360, 0, true)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = new HearingSlotRequestParam(
                "ADULT", "2026-04-14", "2026-04-15",
                null, null, "C01CY00", "10", "1", null, null, null, "AD", false, null, false,
                "720", null, "CROWN");

        JsonObject jsonObject = slotsSearchService.search(param);

        // Only 14 and 15 should appear (both valid 2-day starts within endDate)
        assertThat(jsonObject.getInt("results"), is(2));
        assertThat(jsonObject.getJsonArray("hearingSlots").getJsonObject(0).getString("sessionDate"), is("2026-04-14"));
        assertThat(jsonObject.getJsonArray("hearingSlots").getJsonObject(1).getString("sessionDate"), is("2026-04-15"));
    }

    @Test
    void search_shouldExcludeLookaheadDaysAcrossWeekendForMultidayCrown() {
        // endDate = Fri Apr 17. Sessions: Thu 16, Fri 17, Mon 20, Tue 21.
        // For a 2-day hearing: 16(→17)✓, 17(→Mon 20)✓ (17=endDate, weekend skipped), 20(→21) ✗ beyond endDate
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-16"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-17"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-20"), 360, 0, true),
                createMultidayCourtSchedule(courtRoomId, parse("2026-04-21"), 360, 0, true)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = new HearingSlotRequestParam(
                "ADULT", "2026-04-16", "2026-04-17",
                null, null, "C01CY00", "10", "1", null, null, null, "AD", false, null, false,
                "720", null, "CROWN");

        JsonObject jsonObject = slotsSearchService.search(param);

        // 16 and 17 are valid start dates (17's second day is Mon 20 — look-ahead validates but Mon 20 isn't returned)
        assertThat(jsonObject.getInt("results"), is(2));
        assertThat(jsonObject.getJsonArray("hearingSlots").getJsonObject(0).getString("sessionDate"), is("2026-04-16"));
        assertThat(jsonObject.getJsonArray("hearingSlots").getJsonObject(1).getString("sessionDate"), is("2026-04-17"));
    }

    // ---- Multiday: availability starting at endDate and narrow range with large duration ----

    @Test
    void search_shouldReturnResultWhenAvailabilityStartsAtEndDate() {
        // Range: Thu 26 to Fri 27 March. Sessions exist on Fri 27, Mon 30, Tue 31.
        // The endDate (Fri 27) is a valid start for a 3-day hearing (Fri→Mon→Tue).
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> allSchedules = List.of(
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-27"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-30"), 360, 0, false),
                createMultidayCourtSchedule(courtRoomId, parse("2026-03-31"), 360, 0, false)
        );
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = new HearingSlotRequestParam(
                "ADULT", "2026-03-26", "2026-03-27",
                null, null, "C20CO00", "10", "1", null, null, null, "AD", false, null, false,
                "1080", null, "CROWN");

        JsonObject jsonObject = slotsSearchService.search(param);

        // Fri 27 is a valid start: Fri 27→Mon 30→Tue 31
        assertThat(jsonObject.getInt("results"), is(1));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(1));
        assertThat(jsonObject.getJsonArray("hearingSlots").getJsonObject(0).getString("sessionDate"), is("2026-03-27"));
    }

    @Test
    void search_shouldReturnResultForNarrowRangeWithLargeDuration() {
        // Simulates the curl: startDate=Thu 2026-04-09, endDate=Sun 2026-04-12, duration=5400 (15 days)
        // Sessions exist for 15 consecutive business days starting Apr 9
        String courtRoomId = randomUUID().toString();
        List<CourtSchedule> allSchedules = new ArrayList<>();
        // Generate 15 consecutive business days starting Thu Apr 9
        LocalDate date = parse("2026-04-09");
        for (int i = 0; i < 15; ) {
            if (date.getDayOfWeek() != java.time.DayOfWeek.SATURDAY && date.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
                allSchedules.add(createMultidayCourtSchedule(courtRoomId, date, 360, 0, false));
                i++;
            }
            date = date.plusDays(1);
        }
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(allSchedules);

        HearingSlotRequestParam param = new HearingSlotRequestParam(
                "ADULT,YOUTH", "2026-04-09", "2026-04-12",
                null, null, "C01CY00", "10", "1", null, null, null, "AD", false, null, false,
                "5400", "FINAL", "CROWN");

        JsonObject jsonObject = slotsSearchService.search(param);

        // Apr 9 (Thu) is a valid start for 15 consecutive business days
        assertThat(jsonObject.getInt("results"), is(1));
        assertThat(jsonObject.getJsonArray("hearingSlots").size(), is(1));
        assertThat(jsonObject.getJsonArray("hearingSlots").getJsonObject(0).getString("sessionDate"), is("2026-04-09"));
    }

    // ---- Bug fix: Pagination total count propagated from DB ----

    @Test
    void getCourtSchedules_shouldReturnDbTotalCountNotFilteredPageCount() {
        // DB returns total count 100 with a page of 10 records
        List<CourtSchedule> pageSchedules = List.of(
                getCourtScheduleWithRegularSessions(),
                getCourtScheduleWithRegularSessions()
        );
        int dbTotalCount = 100;
        when(courtScheduleRepository.getCourtSchedules(any())).thenReturn(Pair.of(dbTotalCount, pageSchedules));

        HearingSlotRequestParam param = createRequestParam("10");
        Pair<Integer, List<CourtSchedule>> result = slotsSearchService.getCourtSchedules(param);

        // Total count should come from DB, not from filtered page size
        assertThat(result.getKey(), is(dbTotalCount));
    }

    @Test
    void search_shouldReturnCorrectPageCountFromDbTotal() {
        // DB returns total count 50 with a page of 10 records
        List<CourtSchedule> pageSchedules = List.of(getCourtScheduleWithRegularSessions());
        int dbTotalCount = 50;
        when(courtScheduleRepository.getCourtSchedules(any())).thenReturn(Pair.of(dbTotalCount, pageSchedules));

        HearingSlotRequestParam param = createRequestParam("10");
        JsonObject jsonObject = slotsSearchService.search(param);

        assertThat(jsonObject.getInt("results"), is(dbTotalCount));
        assertThat(jsonObject.getInt("pageCount"), is(5)); // ceil(50/10) = 5
    }

    // ---- SPRDT-1276: CROWN >360 forces courtSession=AD and isSlotBased=false ----

    @Test
    void multidayCrownSearch_shouldForceCourtSessionToAdWhenCallerSendsAm() {
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(List.of());

        // The reported defect: a 720-minute CROWN search that asks for AM sessions.
        slotsSearchService.search(new HearingSlotRequestParam(
                "ADULT,YOUTH", "2026-08-17", "2026-08-17",
                null, null, "C13BR00", "500", "1", null, null, null, "AM", true, null, true,
                "720", "DRAFT", "CROWN"));

        assertThat(capturedMultidayRequest().courtSession(), is("AD"));
        assertThat(capturedMultidayRequest().isSlotBased(), is(false));
    }

    @Test
    void multidayCrownSearch_shouldDefaultCourtSessionToAdWhenCallerSendsNone() {
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(List.of());

        // An absent courtSession previously meant "no court_session predicate at all", which is
        // how AM sessions reached the response for the exact curl on the ticket.
        slotsSearchService.search(new HearingSlotRequestParam(
                "ADULT,YOUTH", "2026-08-17", "2026-08-17",
                null, null, "C13BR00", "500", "1", null, null, null, null, null, null, true,
                "720", "DRAFT", "CROWN"));

        assertThat(capturedMultidayRequest().courtSession(), is("AD"));
        assertThat(capturedMultidayRequest().isSlotBased(), is(false));
    }

    @Test
    void multidayCrownSearch_shouldForceSessionDefaultsEvenWhenBusinessTypeSupplied() {
        when(courtScheduleRepository.getMultidayHearingSlotCandidates(any(), anyInt())).thenReturn(List.of());

        slotsSearchService.search(new HearingSlotRequestParam(
                "ADULT,YOUTH", "2026-08-17", "2026-08-17",
                null, null, "C13BR00", "500", "1", null, null, "TRIAL", "PM", true, null, true,
                "720", "DRAFT", "CROWN"));

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
                "ADULT,YOUTH", "2026-08-17", "2026-08-17",
                null, null, "C13BR00", "500", "1", null, null, null, "AM", true, null, true,
                "360", "DRAFT", "CROWN");

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
                "ADULT,YOUTH", "2026-08-17", "2026-08-17",
                null, null, "C13BR00", "500", "1", null, null, null, "AM", true, null, true,
                "720", "DRAFT", "MAGISTRATES"));

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
                "ADULT,YOUTH", "2026-08-17", "2026-08-17",
                null, null, "C13BR00", "500", "1", null, null, null, null, null, null, true,
                "not-a-number", "DRAFT", "CROWN");

        assertThat(slotsSearchService.isMultidayCrownSearch(param), is(false));
    }

    private HearingSlotRequestParam capturedMultidayRequest() {
        final ArgumentCaptor<HearingSlotRequestParam> captor =
                ArgumentCaptor.forClass(HearingSlotRequestParam.class);
        verify(courtScheduleRepository).getMultidayHearingSlotCandidates(captor.capture(), anyInt());
        return captor.getValue();
    }

    // ---- Multiday helper methods ----

    private CourtSchedule createMultidayCourtSchedule(String courtRoomId, LocalDate sessionDate,
                                                       int maxDuration, int totalBooked, boolean overbookingAllowed) {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withCourtRoomNumber(1)
                .withSessionDate(sessionDate)
                .withMaxDuration(maxDuration)
                .withTotalBooked(totalBooked)
                .withAvailableDuration(maxDuration - totalBooked)
                .withIsOverbookingAllowed(overbookingAllowed)
                .withSlotBased(false)
                .withAllDaySplit(false)
                .withCourtSession("AD")
                .withJurisdiction(CROWN.getJurisdiction())
                .withPanel("ADULT")
                .withOuCode("C20CO00")
                .withCourtHouseName("Crown Court")
                .withActive(true)
                .build();
    }

    private CourtSchedule createMultidayCourtSchedule(String courtRoomId, LocalDate sessionDate,
                                                       int maxDuration, int totalBooked, boolean overbookingAllowed,
                                                       String businessType, String ouCode) {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(randomUUID().toString())
                .withCourtRoomId(courtRoomId)
                .withCourtRoomNumber(1)
                .withSessionDate(sessionDate)
                .withMaxDuration(maxDuration)
                .withTotalBooked(totalBooked)
                .withAvailableDuration(maxDuration - totalBooked)
                .withIsOverbookingAllowed(overbookingAllowed)
                .withSlotBased(false)
                .withAllDaySplit(false)
                .withCourtSession("AD")
                .withJurisdiction(CROWN.getJurisdiction())
                .withPanel("ADULT")
                .withOuCode(ouCode)
                .withBusinessType(businessType)
                .withCourtHouseName("Crown Court")
                .withActive(true)
                .build();
    }

    private HearingSlotRequestParam createMultidayRequestParam(String jurisdiction, String duration) {
        return new HearingSlotRequestParam("ADULT", LocalDate.now().toString(), LocalDate.now().plusDays(14).toString(),
                null, null, "C20CO00", "10", "1", null, null, null, "AD", false, null, false,
                duration, null, jurisdiction);
    }

    private HearingSlotRequestParam createMultidayRequestParamWithPagination(String jurisdiction, String duration,
                                                                              String pageSize, String pageNumber) {
        return new HearingSlotRequestParam("ADULT", "2026-03-26", "2026-04-10",
                null, null, "C20CO00", pageSize, pageNumber, null, null, null, "AD", false, null, false,
                duration, null, jurisdiction);
    }

}
