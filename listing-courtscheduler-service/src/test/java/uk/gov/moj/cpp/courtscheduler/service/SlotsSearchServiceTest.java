package uk.gov.moj.cpp.courtscheduler.service;

import static java.time.LocalDate.parse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.json.JsonObject;

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
    private UUID rightWingerId = UUID.randomUUID();
    private UUID leftWingerId = UUID.randomUUID();
    private UUID chairId = UUID.randomUUID();

    @BeforeEach
    public void setUp() {
        setField(slotsSearchService, "courtScheduleRepository", courtScheduleRepository);
    }

    @Test
    public void shouldSearchSlots() {
        final List<CourtSchedule> courtSchedulesExpected = List.of(courtScheduleWithMultipleJudiciaries(rightWingerId, leftWingerId, chairId));
        courtSchedulesExpected.stream().map(CourtSchedule::getJudiciaries);
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final JsonObject jsonObject = slotsSearchService.search(hearingSlotRequestParam);
        assertThat(jsonObject, is(toJsonObject(rightWingerId, leftWingerId, chairId)));
    }

    @Test
    public void shouldSearchSlotsWhenPageSizeSent0() {
        final List<CourtSchedule> courtSchedulesExpected = List.of(courtScheduleWithMultipleJudiciaries(rightWingerId, leftWingerId, chairId));
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("0");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final JsonObject jsonObject = slotsSearchService.search(hearingSlotRequestParam);
        assertThat(jsonObject, is(toJsonObject(rightWingerId, leftWingerId, chairId)));
    }

    @Test
    public void shouldHandleMultipleJudiciaries() {
        final List<CourtSchedule> courtSchedulesExpected = List.of(courtScheduleWithMultipleJudiciaries(rightWingerId, leftWingerId, chairId));
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(100, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final Pair<Integer, List<CourtSchedule>> courtSchedulesActual = slotsSearchService.getCourtSchedules(hearingSlotRequestParam);
        assertThat(courtSchedulesActual.getValue().size(), is(1));
        final CourtSchedule courtSchedule = courtSchedulesActual.getValue().get(0);
        assertThat(courtSchedule.getCourtScheduleId().toString(), is("0000fbb0-8579-4f2b-948e-c4e48a48e3f8"));
        assertThat(courtSchedule.getListingProfileId(), is("0000fbb0-8579-4f2b-948e-c4e48a48e3f7"));
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
                .withListingProfileId("0000fbb0-8579-4f2b-948e-c4e48a48e3f7")
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
                .build();
    }

    private HearingSlotRequestParam createRequestParam(String pageSize) {
        return new HearingSlotRequestParam("ADULT", LocalDate.now().toString(), LocalDate.now().toString(),
                null, "BA124", pageSize, "1", null, null, null, null);
    }

    private JsonObject toJsonObject(UUID judiciaryId1, UUID judiciaryId2, UUID judiciaryId3) {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        String source = fileToString("/test-data/courtscheduler.get.slots-search-response.json");
        source = source.replace("JUDICIARY_ID_1", judiciaryId1.toString());
        source= source.replace("JUDICIARY_ID_2", judiciaryId2.toString());
        source = source.replace("JUDICIARY_ID_3", judiciaryId3.toString());
        return stringToJsonObjectConverter.convert(source);
    }
}
