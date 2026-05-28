package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.time.LocalDate.parse;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
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
                Instant.now().toString(),  null, "BA124", pageSize, "1", null, null, null, null, false,null,false,"API","100");
    }

    private HearingSlotRequestParam createRequestParamWithDuration() {
        return new HearingSlotRequestParam("ADULT", LocalDate.now().toString(), LocalDate.now().toString(),
                Instant.now().toString(), null, "B12JR00", "10", "1", null, null, null, null, false, null, false,"API","20");
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
}
