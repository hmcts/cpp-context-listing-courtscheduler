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
    @BeforeEach
    public void setUp() {
        setField(slotsSearchService, "courtScheduleRepository", courtScheduleRepository);
    }

    @Test
    public void shouldSearchSlots() {
        final List<CourtSchedule> courtSchedulesExpected = courtListWithMultipleJudiciaries();
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final JsonObject jsonObject = slotsSearchService.search(hearingSlotRequestParam);
        assertThat(jsonObject, is(toJsonObject()));
    }

    @Test
    public void shouldSearchSlotsWhenPageSizeSent0() {
        final List<CourtSchedule> courtSchedulesExpected = courtListWithMultipleJudiciaries();
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(1, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("0");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final JsonObject jsonObject = slotsSearchService.search(hearingSlotRequestParam);
        assertThat(jsonObject, is(toJsonObject()));
    }

    @Test
    public void shouldHandleMultipleJudiciaries() {
        final List<CourtSchedule> courtSchedulesExpected = courtListWithMultipleJudiciaries();
        final Pair<Integer, List<CourtSchedule>> courtSchedulePair = Pair.of(100, courtSchedulesExpected);
        final HearingSlotRequestParam hearingSlotRequestParam = createRequestParam("10");
        when(courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam)).thenReturn(courtSchedulePair);

        final Pair<Integer, List<CourtSchedule>> courtSchedulesActual = slotsSearchService.getCourtSchedules(hearingSlotRequestParam);
        assertThat(courtSchedulesActual.getValue().size(), is(1));
        final CourtSchedule courtSchedule = courtSchedulesActual.getValue().get(0);
        assertThat(courtSchedule.getCourtScheduleId(), is("0000fbb0-8579-4f2b-948e-c4e48a48e3f8"));
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
        assertThat(courtScheduleJudiciaryDetails.get(0).getJudiciaryId(), is("123"));
        assertThat(courtScheduleJudiciaryDetails.get(1).getJudiciaryId(), is("124"));
        assertThat(courtScheduleJudiciaryDetails.get(2).getJudiciaryId(), is("125"));
        assertThat(courtScheduleJudiciaryDetails.get(0).getPosition(), is("RIGHT_WINGER"));
        assertThat(courtScheduleJudiciaryDetails.get(1).getPosition(), is("LEFT_WINGER"));
        assertThat(courtScheduleJudiciaryDetails.get(2).getPosition(), is("CHAIR"));
    }

    private List<CourtSchedule> courtListWithMultipleJudiciaries() {
        final List<CourtSchedule> courtScheduleArrayList = new ArrayList<>();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaries = new ArrayList<>();
        final CourtScheduleJudiciary rightWinger =  buildJudiciary("123","RIGHT_WINGER");
        final CourtScheduleJudiciary leftWinger = buildJudiciary("124", "LEFT_WINGER");
        final CourtScheduleJudiciary chair = buildJudiciary("125", "CHAIR");
        courtScheduleJudiciaries.add(rightWinger);
        courtScheduleJudiciaries.add(leftWinger);
        courtScheduleJudiciaries.add(chair);
        final CourtSchedule cs = courtSchedule(courtScheduleJudiciaries);
        courtScheduleArrayList.add(cs);
        return courtScheduleArrayList;
    }

    private CourtScheduleJudiciary buildJudiciary(final String id, final String position) {
        return judiciary()
                .withJudiciaryId(id)
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
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(182)
                .withAvailableSlots(125)
                .withAvailableDuration(182)
                .withMaxSlots(125)
                .withJudiciaries(courtScheduleJudiciary)
                .build();
    }

    private HearingSlotRequestParam createRequestParam(String pageSize) {
        return new HearingSlotRequestParam("ADULT", LocalDate.now().toString(), LocalDate.now().toString(),
                null, "BA124", pageSize, "1", null, null, null, null);
    }

    private JsonObject toJsonObject() {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.get.slots-search-response.json"));
    }
}
