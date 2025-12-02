package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util.PropertiesLoader;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaDataEnricherTest {

    @InjectMocks
    private RotaDataEnricher rotaDataEnricher;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private CourtSession courtSession;

    @Mock
    private CourtScheduleEnricher courtScheduleEnricher;

    @Mock
    private Requester requester;

    private ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private static final String AM_SESSION = "AM";
    private static final String PM_SESSION = "PM";
    private static final String ALL_DAY_SESSION = "AD";

    private CourtRoomSessionAllocation sessionAllocation = new CourtRoomSessionAllocation("241546", 2332, "B01LY00", 8, 60, "TRF", AM_SESSION);

    @BeforeEach
    public void setup() {
        setField(rotaDataEnricher, "missingReferenceDataMappingLogger", new MissingReferenceDataMappingLogger());
    }

    @Test
    void shouldEnrichListingWithCppReferenceData() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final LocalDate rotaPeriodCutOffDate = LocalDate.of(2019, 12, 16);
        final RotaFileParser rotaFileParser = new RotaFileParser();
        setField(rotaFileParser, "propertiesLoader", new PropertiesLoader());
        final Map<String,String> missingReferenceDataMappingMap = new HashMap();

        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule courtSchedule = getCourtSchedule();
        final List<CourtSchedule> courtScheduleList = List.of(CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtSchedule(courtSchedule)
                .withCourtScheduleId(courtScheduleId)
                .withOuCode(courtSchedule.getOuCode())
                .withCreatedOn(Calendar.getInstance().getTime())
                .build());

        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(eq(requester), anyString(), anyInt(), anyString(), anyString())).thenReturn(of(sessionAllocation));
        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), eq(requester), anyString())).thenReturn(courtSchedule);
        when(courtSession.getCourtSession(any(LocalDate.class), anyString())).thenReturn("WEDPM");

        final byte[] blobContent = givenBlobContent(file);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(file, blobContent);

        final Map<String, CourtSchedule> courtSchedules = rotaDataEnricher.enrichCourtListings(records, rotaPeriodCutOffDate, courtScheduleList, requester, randomUUID().toString());

        final Collection<CourtSchedule> schedules = courtSchedules.values();
        final Integer totalListings = records.get(COURT_LISTING).values().size();
        final long allDay = schedules.stream().filter(ch -> ch.getCourtSession().equals(ALL_DAY_SESSION)).count();
        final long amSessions = schedules.stream().filter(ch -> ch.getCourtSession().equals(AM_SESSION)).count();
        final long pmSessions = schedules.stream().filter(ch -> ch.getCourtSession().equals(PM_SESSION)).count();
        final CourtSchedule pmSession = schedules.stream().filter(ch -> ch.getCourtSession().equals(PM_SESSION)).findFirst().get();

        assertThat(totalListings, is(472));
        assertThat(allDay, is(0L));
        assertThat(amSessions, is(0L));
        assertThat(pmSessions, is(1L));
        assertThat(pmSession.getMaxSlots(), is(0));
        assertThat(pmSession.getAvailableSlots(), is(0));
        assertThat(pmSession.getMaxDuration(), is(0));
        assertThat(pmSession.getAvailableDuration(), is(0));
        assertThat(missingReferenceDataMappingMap.size(), is(0));

    }

    @Test
    void shouldEnrichListingWithCppReferenceDataWithLoggingMissingReferenceDataMapping() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";
        final LocalDate rotaPeriodCutOffDate = LocalDate.of(2019, 12, 16);
        final RotaFileParser rotaFileParser = new RotaFileParser();
        setField(rotaFileParser, "propertiesLoader", new PropertiesLoader());
        final Map<String,String> missingReferenceDataMappingMap = new HashMap();

        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule courtSchedule = getCourtSchedule();
        final List<CourtSchedule> courtScheduleList = List.of(CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtSchedule(courtSchedule)
                .withCourtScheduleId(courtScheduleId)
                .withOuCode(courtSchedule.getOuCode())
                .withCreatedOn(Calendar.getInstance().getTime())
                .build());

        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(eq(requester), anyString(), anyInt(), anyString(), anyString())).thenReturn(empty());
        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), eq(requester), anyString())).thenReturn(courtSchedule);
        when(courtSession.getCourtSession(any(LocalDate.class), anyString())).thenReturn("WEDPM");

        final byte[] blobContent = givenBlobContent(file);

        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(file, blobContent);

        final Map<String, CourtSchedule> courtSchedules = rotaDataEnricher.enrichCourtListings(records, rotaPeriodCutOffDate, courtScheduleList, requester, randomUUID().toString());

        final Collection<CourtSchedule> schedules = courtSchedules.values();
        final Integer totalListings = records.get(COURT_LISTING).values().size();
        final long allDay = schedules.stream().filter(ch -> ch.getCourtSession().equals(ALL_DAY_SESSION)).count();
        final long amSessions = schedules.stream().filter(ch -> ch.getCourtSession().equals(AM_SESSION)).count();
        final long pmSessions = schedules.stream().filter(ch -> ch.getCourtSession().equals(PM_SESSION)).count();
        final CourtSchedule pmSession = schedules.stream().filter(ch -> ch.getCourtSession().equals(PM_SESSION)).findFirst().get();

        assertThat(totalListings, is(472));
        assertThat(allDay, is(0L));
        assertThat(amSessions, is(0L));
        assertThat(pmSessions, is(1L));
        assertThat(pmSession.getMaxSlots(), is(0));
        assertThat(pmSession.getAvailableSlots(), is(0));
        assertThat(pmSession.getMaxDuration(), is(0));
        assertThat(pmSession.getAvailableDuration(), is(0));
        assertThat(missingReferenceDataMappingMap.size(),is(0));

    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaDataEnricherTest.class.getClassLoader().getResourceAsStream(file)) {
            return toByteArray(inputStream);
        }
    }

    private CourtSchedule getCourtSchedule() throws JsonProcessingException {
        final String courtScheduleDomainsJsonString = FileUtil.fileToString("/test-data/single-court-schedule-domain-data.json");

        return objectMapper.readValue(courtScheduleDomainsJsonString, CourtSchedule.class);
    }
}
