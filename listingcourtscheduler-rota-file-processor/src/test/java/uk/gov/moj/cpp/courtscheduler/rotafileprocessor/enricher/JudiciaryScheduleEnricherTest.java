package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util.PropertiesLoader;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryScheduleEnricherTest {

    @InjectMocks
    private JudiciaryScheduleEnricher judiciaryScheduleEnricher;

    @Mock
    private Map<String, CourtSchedule> courtScheduleMap;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private Requester requester;

    @Spy
    private MissingReferenceDataMappingLogger missingMessageLogger = new MissingReferenceDataMappingLogger();

    @BeforeEach
    public void setUp() {
        setField(judiciaryScheduleEnricher, "missingMessageLogger", missingMessageLogger);
        setField(judiciaryScheduleEnricher, "judiciaryBuilder", new JudiciaryBuilder());
    }

    @Test
    void shouldEnrichJudiciarySchedules() throws IOException {
        final String file = "rotafileprocessor/judiciary_enrich_payload.xml";
        final Judiciary judiciary = getJudiciary();
        final byte[] blobContent = givenBlobContent(file);
        final RotaFileParser rotaFileParser = new RotaFileParser();
        setField(rotaFileParser, "propertiesLoader", new PropertiesLoader());
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(file, blobContent);

        when(referenceDataMapperService.findByEmail(eq(requester), anyString())).thenReturn(Optional.of(judiciary));
        when(courtScheduleMap.get(anyString())).thenReturn(new CourtSchedule());

        final Collection<CourtScheduleJudiciary> courtScheduleJudiciaries = judiciaryScheduleEnricher.enrichJudiciarySchedules(courtScheduleMap, records, false, requester);

        verify(referenceDataMapperService, times(3)).findByEmail(eq(requester), anyString());
        assertThat(courtScheduleJudiciaries.size(), is(3));

        final Optional<CourtScheduleJudiciary> courtScheduleJudiciary = courtScheduleJudiciaries.stream().findFirst();

        if (courtScheduleJudiciary.isPresent()) {
            final CourtScheduleJudiciary csj = courtScheduleJudiciary.get();

            assertThat(csj.getJudiciaryId(), is("9ff490e1-c5b8-47b8-ae78-b71d88fdb798"));
            assertThat(csj.getJudiciaryType(), is("Magistrates"));
            assertThat(csj.getEmailAddress(), is("LuciusTSFloraTS@moj.gov.uk"));
            assertThat(csj.getForenames(), is("TienaTS"));
            assertThat(csj.getSurname(), is("SvenTS"));
            assertNotNull(csj.getRotaJudiciaryId());
            assertNotNull(csj.getTitle());
            assertNotNull(csj.getBenchChairman());
            assertNotNull(csj.getDeputy());
            assertNotNull(csj.getCourtListingProfileId());
            assertNotNull(csj.getPosition());
        }

        verify(referenceDataMapperService, times(3)).findByEmail(eq(requester), anyString());

        verifyNoMoreInteractions(referenceDataMapperService, missingMessageLogger);
    }

    @Test
    void shouldEnrichCourtScheduleJudiciaryForUnmappedEmail() throws IOException {

        final String file = "rotafileprocessor/judiciary_enrich_payload.xml";
        final byte[] blobContent = givenBlobContent(file);
        final RotaFileParser rotaFileParser = new RotaFileParser();
        setField(rotaFileParser, "propertiesLoader", new PropertiesLoader());
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(file, blobContent);

        when(referenceDataMapperService.findByEmail(eq(requester), anyString())).thenReturn(Optional.empty());
        final Map<String, CourtSchedule> courtScheduleMap = new HashMap<>();
        final CourtSchedule courtSchedule = courtSchedule();
        courtScheduleMap.put(courtSchedule.getListingProfileId(), courtSchedule);
        when(sessionsService.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(anyString(), any(), anyString(), anyString()))
                .thenReturn(new CourtScheduleMatcherInfo(courtSchedule.getCourtScheduleId(), "B01LY00", Calendar.getInstance().getTime()));

        final Collection<CourtScheduleJudiciary> courtScheduleJudiciaries = judiciaryScheduleEnricher.enrichJudiciarySchedules(courtScheduleMap, records, false, requester);

        verify(referenceDataMapperService, times(3)).findByEmail(eq(requester), anyString());
        assertThat(courtScheduleJudiciaries.size(), is(0));

        final Optional<CourtScheduleJudiciary> courtScheduleJudiciary = courtScheduleJudiciaries.stream().findFirst();

        if (courtScheduleJudiciary.isPresent()) {
            final CourtScheduleJudiciary csj = courtScheduleJudiciary.get();
            assertThat(csj.getJudiciaryId(), is(nullValue()));
        }

        verify(referenceDataMapperService, times(3)).findByEmail(eq(requester), anyString());
        verify(missingMessageLogger, atLeastOnce()).logJudiciaryMissingMessage(anyCollection());

        verifyNoMoreInteractions(referenceDataMapperService, missingMessageLogger);
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = JudiciaryScheduleEnricherTest.class.getClassLoader().getResourceAsStream(file)) {
            assert inputStream != null;
            return toByteArray(inputStream);
        }
    }

    private Judiciary getJudiciary() {
        return new Judiciary("9ff490e1-c5b8-47b8-ae78-b71d88fdb798", "Mrs", "TienaTS", "SvenTS", "LuciusTSFloraTS@moj.gov.uk", "Magistrates");
    }

    private CourtSchedule courtSchedule() {
        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(UUID.randomUUID().toString())
                .withListingProfileId("LH2294283")
                .withSessionDate(LocalDate.of(2024, 11, 24))
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
                .build();
    }
}
