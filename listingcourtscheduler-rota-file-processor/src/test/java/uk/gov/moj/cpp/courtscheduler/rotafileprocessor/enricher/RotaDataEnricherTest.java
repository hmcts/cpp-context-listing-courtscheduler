package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.Boolean.FALSE;
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
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
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

    private ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

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
        final Map<String,String> missingReferenceDataMappingMap = new HashMap<>();

        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule courtSchedule = getCourtSchedule();
        final Map<String, Boolean> migratedMap = Map.of(courtSchedule.getOuCode(), FALSE);
        final List<CourtSchedule> courtScheduleList = List.of(CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtSchedule(courtSchedule)
                .withCourtScheduleId(courtScheduleId)
                .withOuCode(courtSchedule.getOuCode())
                .withCreatedOn(Calendar.getInstance().getTime())
                .build());

        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString())).thenReturn(of(sessionAllocation));
        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), anyString())).thenReturn(courtSchedule);
        when(courtSession.getCourtSession(any(LocalDate.class), anyString())).thenReturn("WEDPM");

        final byte[] blobContent = givenBlobContent(file);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(file, blobContent);

        final Map<String, CourtSchedule> courtSchedules = rotaDataEnricher.enrichCourtListings(records, rotaPeriodCutOffDate, migratedMap, FALSE, courtScheduleList, randomUUID().toString(), missingReferenceDataMappingMap);

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
        final Map<String,String> missingReferenceDataMappingMap = new HashMap<>();

        final String courtScheduleId = randomUUID().toString();
        final CourtSchedule courtSchedule = getCourtSchedule();
        final Map<String, Boolean> migratedMap = Map.of(courtSchedule.getOuCode(), FALSE);
        final List<CourtSchedule> courtScheduleList = List.of(CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtSchedule(courtSchedule)
                .withCourtScheduleId(courtScheduleId)
                .withOuCode(courtSchedule.getOuCode())
                .withCreatedOn(Calendar.getInstance().getTime())
                .build());

        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString())).thenReturn(empty());
        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), anyString())).thenReturn(courtSchedule);
        when(courtSession.getCourtSession(any(LocalDate.class), anyString())).thenReturn("WEDPM");

        final byte[] blobContent = givenBlobContent(file);

        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(file, blobContent);

        final Map<String, CourtSchedule> courtSchedules = rotaDataEnricher.enrichCourtListings(records, rotaPeriodCutOffDate, migratedMap, FALSE, courtScheduleList, randomUUID().toString(), missingReferenceDataMappingMap);

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

    @Test
    void updateExistingCourtScheduleShouldUseRefdataTimesForAllDay() {
        final LocalDate sessionDate = LocalDate.of(2026, 4, 29); // Wednesday
        final String linkedSessionId = "L1";
        final String ouCode = "B01LY00";
        final String businessType = "DVB";
        final Integer courtRoomNumber = 2332;
        final String courtRoomId = randomUUID().toString();

        // Two rows for the same linkedSessionId and business -> second hits updateExistingCourtSchedule (ALL_DAY)
        final Map<RotaPayload, Map<String, Map<String, String>>> records = new HashMap<>();
        final Map<String, Map<String, String>> listings = new java.util.LinkedHashMap<>();
        listings.put("L1", listingRow("L1", linkedSessionId, sessionDate, "AM", businessType));
        listings.put("L2", listingRow("L2", linkedSessionId, sessionDate, "PM", businessType));
        records.put(COURT_LISTING, listings);

        // First row: enricher returns the AM-built schedule (its listingProfileId acts as the key in the map)
        final CourtSchedule built = CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withListingProfileId("L1")
                .withOuCode(ouCode)
                .withCourtRoomId(courtRoomId)
                .withCourtRoomNumber(courtRoomNumber)
                .withBusinessType(businessType)
                .withCourtSession("AM")
                .withSessionDate(sessionDate)
                .withMaxSlots(0)
                .withAvailableSlots(0)
                .withMaxDuration(0)
                .withAvailableDuration(0)
                .build();
        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), anyString())).thenReturn(built);

        // Second row triggers refdata lookup; refdata supplies custom AD start/end times
        when(courtSession.getCourtSession(any(LocalDate.class), anyString())).thenReturn("WEDPM");
        final CourtRoomSessionAllocation allocation = CourtRoomSessionAllocation.CourtRoomSessionAllocationBuilder.aCourtRoomSessionAllocation()
                .withId("alloc-1")
                .withCourtRoomId(courtRoomNumber)
                .withOucode(ouCode)
                .withMaxSlot(4)
                .withMaxDurationMins(45)
                .withRotaBusinessTypeCode(businessType)
                .withCourtSession("WEDPM")
                .withSessionStartTime("09:15")
                .withSessionEndTime("16:30")
                .build();
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(of(allocation));

        final Map<String, Boolean> migratedMap = Map.of(ouCode, FALSE);
        final Map<String, String> missing = new HashMap<>();
        final Map<String, CourtSchedule> result = rotaDataEnricher.enrichCourtListings(records, sessionDate, migratedMap, FALSE, List.of(), randomUUID().toString(), missing);

        assertThat(result.size(), is(1));
        final CourtSchedule updated = result.get("L1");
        assertThat(updated.getCourtSession(), is(ALL_DAY_SESSION));
        // refdata times override hardcoded ALL_DAY defaults (10:00 / 17:00)
        assertThat(updated.getSessionStartTime(), is(DateUtils.combineDateAndTime(sessionDate, "09:15")));
        assertThat(updated.getSessionEndTime(), is(DateUtils.combineDateAndTime(sessionDate, "16:30")));
        // slot/duration totals get incremented by allocation values
        assertThat(updated.getMaxSlots(), is(4));
        assertThat(updated.getAvailableSlots(), is(4));
        assertThat(updated.getMaxDuration(), is(45));
        assertThat(updated.getAvailableDuration(), is(45));
    }

    @Test
    void updateExistingCourtScheduleShouldUseDefaultAllDayTimesWhenRefdataMissing() {
        final LocalDate sessionDate = LocalDate.of(2026, 4, 29);
        final String linkedSessionId = "L1";
        final String ouCode = "B01LY00";
        final String businessType = "DVB";
        final Integer courtRoomNumber = 2332;
        final String courtRoomId = randomUUID().toString();

        final Map<RotaPayload, Map<String, Map<String, String>>> records = new HashMap<>();
        final Map<String, Map<String, String>> listings = new java.util.LinkedHashMap<>();
        listings.put("L1", listingRow("L1", linkedSessionId, sessionDate, "AM", businessType));
        listings.put("L2", listingRow("L2", linkedSessionId, sessionDate, "PM", businessType));
        records.put(COURT_LISTING, listings);

        final CourtSchedule built = CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(randomUUID().toString())
                .withListingProfileId("L1")
                .withOuCode(ouCode)
                .withCourtRoomId(courtRoomId)
                .withCourtRoomNumber(courtRoomNumber)
                .withBusinessType(businessType)
                .withCourtSession("AM")
                .withSessionDate(sessionDate)
                .build();
        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), anyString())).thenReturn(built);

        when(courtSession.getCourtSession(any(LocalDate.class), anyString())).thenReturn("WEDPM");
        // No allocation -> defaults must apply (ALL_DAY: 10:00 / 17:00)
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(empty());

        final Map<String, Boolean> migratedMap = Map.of(ouCode, FALSE);
        final Map<String, String> missing = new HashMap<>();
        final Map<String, CourtSchedule> result = rotaDataEnricher.enrichCourtListings(records, sessionDate, migratedMap, FALSE, List.of(), randomUUID().toString(), missing);

        final CourtSchedule updated = result.get("L1");
        assertThat(updated.getCourtSession(), is(ALL_DAY_SESSION));
        assertThat(updated.getSessionStartTime(), is(DateUtils.combineDateAndTime(sessionDate, "10:00")));
        assertThat(updated.getSessionEndTime(), is(DateUtils.combineDateAndTime(sessionDate, "17:00")));
    }

    private Map<String, String> listingRow(final String id, final String linkedSessionId, final LocalDate sessionDate, final String session, final String businessType) {
        final Map<String, String> row = new HashMap<>();
        row.put("id", id);
        row.put("linkedSessionId", linkedSessionId);
        row.put("sessionDate", sessionDate.toString());
        row.put("session", session);
        row.put("business", businessType);
        return row;
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
