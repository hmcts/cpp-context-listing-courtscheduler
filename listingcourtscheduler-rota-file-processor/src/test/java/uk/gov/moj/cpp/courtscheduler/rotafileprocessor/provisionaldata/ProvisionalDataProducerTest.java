package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static java.time.LocalDate.parse;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalDataProducerTest {

    private static final List<String> OUCODES = List.of("175,177");

    @Mock
    private SessionsService sessionsService;

    @Mock
    private ProvisionalDataDateInfoProvider provisionalDataDateInfoProvider;

    @Mock
    private ProvisionalDataExtractDateInfoProvider provisionalDataExtractDateInfoProvider;

    @Spy
    private CourtScheduleToForecastCourtScheduleConverter courtScheduleToForecastCourtScheduleConverter;

    @InjectMocks
    private ProvisionalDataProducer provisionalDataProducer;

    @Test
    void shouldProduceProvisionalData() {
        setField(provisionalDataProducer, "courtScheduleToForecastCourtScheduleConverter", courtScheduleToForecastCourtScheduleConverter);
        final LocalDate rotaStartDate = LocalDate.of(2019, 10, 01);
        final LocalDate extractStartDate = LocalDate.of(2019, 10, 02);
        final LocalDate extractEndDate = LocalDate.of(2019, 10, 29);
        final LocalDate provisionalStartDate = LocalDate.of(2020, 1, 01);
        final LocalDate provisionalEndDate = LocalDate.of(2020, 9, 30);
        final int cyclesToPopulate = 10;

        final List<CourtSchedule> extractedSchedules = new ArrayList<CourtSchedule>();
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString()));
        }

        when(provisionalDataExtractDateInfoProvider.getProvisionalDataExtractStartDate()).thenReturn(extractStartDate);
        when(provisionalDataDateInfoProvider.getProvisionalDataStartDate()).thenReturn(provisionalStartDate);
        when(provisionalDataDateInfoProvider.getProvisionalDataEndDate()).thenReturn(provisionalEndDate);
        when(provisionalDataDateInfoProvider.getCyclesToPopulate()).thenReturn(cyclesToPopulate);
        when(sessionsService.getExtractedCourtSchedulesForGhostRota(OUCODES, rotaStartDate, extractEndDate)).thenReturn(extractedSchedules);

        final ProvisionalSessionDateProvider provisionalSessionDateProvider =
                new ProvisionalSessionDateProvider(provisionalDataExtractDateInfoProvider, provisionalDataDateInfoProvider, cyclesToPopulate);


        provisionalDataProducer.produceProvisionalData(rotaStartDate, extractEndDate, cyclesToPopulate, OUCODES, provisionalSessionDateProvider);

        final List<CourtSchedule> provisionalCourtSchedules = provisionalDataProducer.produceProvisionalData(rotaStartDate, extractEndDate, 10, OUCODES, provisionalSessionDateProvider);

        int count = 0;
        assertThat(provisionalCourtSchedules.size(), is(100));
        for (final CourtSchedule courtSchedule : provisionalCourtSchedules) {
            assertThat(courtSchedule.getSessionDate(), is(provisionalStartDate.plusDays(count)));
            assertThat(courtSchedule.getListingProfileId(), is(nullValue()));
            assertThat(courtSchedule.getJudiciaries(), is(nullValue()));
            count++;
        }
    }

    private CourtSchedule courtSchedule(final String sessionDate) {
        final List<CourtScheduleJudiciary> judiciaries = new ArrayList<CourtScheduleJudiciary>();
        judiciaries.add(judiciary().withRotaJudiciaryId("123").withPosition("CHAIR").build());

        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withListingProfileId(UUID.randomUUID().toString())
                .withSessionDate(parse(sessionDate))
                .withOuCode("CABC90")
                .withCourtRoomId("001c067d-eaca-4ce5-ad90-a366ef3e4bb6")
                .withCourtRoomNumber(1234)
                .withCourtHouseName("Liverpool Mags Court")
                .withCourtHouseId("0b9417b8-91b4-385d-9e01-069855777c4f")
                .withCourtRoomName("Court name1")
                .withOperationalUnit("ANC")
                .withBusinessType("BYS")
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(182)
                .withAvailableSlots(125)
                .withAvailableDuration(182)
                .withMaxSlots(125)
                .withJudiciaries(judiciaries)
                .build();
    }
}
