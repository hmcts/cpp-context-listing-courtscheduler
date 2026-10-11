package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.Boolean.FALSE;
import static java.util.UUID.randomUUID;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util.PropertiesLoader;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

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
    private CourtScheduleEnricher courtScheduleEnricher;

    private ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    private static final String AM_SESSION = "AM";
    private static final String PM_SESSION = "PM";
    private static final String ALL_DAY_SESSION = "AD";

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
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCreatedOn(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(Instant.now()));
        final List<CourtSchedule> courtScheduleList = List.of(courtSchedule);

        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), anyString())).thenReturn(courtSchedule);

        final byte[] blobContent = givenBlobContent(file);
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(file, blobContent);

        final Map<String, CourtSchedule> courtSchedules = rotaDataEnricher.enrichCourtListings(records, rotaPeriodCutOffDate, migratedMap, FALSE, courtScheduleList, randomUUID().toString(), missingReferenceDataMappingMap);

        final Collection<CourtSchedule> schedules = courtSchedules.values();
        final Integer totalListings = records.get(COURT_LISTING).values().size();
        final long allDay = schedules.stream().filter(ch -> ALL_DAY_SESSION.equals(ch.getCourtSession())).count();
        final long amSessions = schedules.stream().filter(ch -> AM_SESSION.equals(ch.getCourtSession())).count();
        final long pmSessions = schedules.stream().filter(ch -> PM_SESSION.equals(ch.getCourtSession())).count();
        final CourtSchedule pmSession = schedules.stream().filter(ch -> PM_SESSION.equals(ch.getCourtSession())).findFirst().get();

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
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setCreatedOn(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(Instant.now()));
        final List<CourtSchedule> courtScheduleList = List.of(courtSchedule);

        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), anyString())).thenReturn(courtSchedule);

        final byte[] blobContent = givenBlobContent(file);

        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(file, blobContent);

        final Map<String, CourtSchedule> courtSchedules = rotaDataEnricher.enrichCourtListings(records, rotaPeriodCutOffDate, migratedMap, FALSE, courtScheduleList, randomUUID().toString(), missingReferenceDataMappingMap);

        final Collection<CourtSchedule> schedules = courtSchedules.values();
        final Integer totalListings = records.get(COURT_LISTING).values().size();
        final long allDay = schedules.stream().filter(ch -> ALL_DAY_SESSION.equals(ch.getCourtSession())).count();
        final long amSessions = schedules.stream().filter(ch -> AM_SESSION.equals(ch.getCourtSession())).count();
        final long pmSessions = schedules.stream().filter(ch -> PM_SESSION.equals(ch.getCourtSession())).count();
        final CourtSchedule pmSession = schedules.stream().filter(ch -> PM_SESSION.equals(ch.getCourtSession())).findFirst().get();

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
    void updateExistingCourtScheduleShouldUseDefaultAllDayTimes() {
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

        final CourtSchedule built = new CourtSchedule()
                .courtScheduleId(randomUUID().toString())
                .listingProfileId("L1")
                .ouCode(ouCode)
                .courtRoomId(courtRoomId)
                .courtRoomNumber(courtRoomNumber)
                .businessType(businessType)
                .courtSession("AM")
                .sessionDate(sessionDate);
        when(courtScheduleEnricher.build(anyMap(), any(LocalDate.class), anyMap(), anyList(), anyString())).thenReturn(built);


        final Map<String, Boolean> migratedMap = Map.of(ouCode, FALSE);
        final Map<String, String> missing = new HashMap<>();
        final Map<String, CourtSchedule> result = rotaDataEnricher.enrichCourtListings(records, sessionDate, migratedMap, FALSE, List.of(), randomUUID().toString(), missing);

        final CourtSchedule updated = result.get("L1");
        assertThat(updated.getCourtSession(), is(ALL_DAY_SESSION));
        assertThat(updated.getSessionStartTime(), is(DateUtils.toOffsetDateTime(DateUtils.combineDateAndTime(sessionDate, "10:00"))));
        assertThat(updated.getSessionEndTime(), is(DateUtils.toOffsetDateTime(DateUtils.combineDateAndTime(sessionDate, "17:00"))));
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
        try (InputStream inputStream = RotaDataEnricherTest.class.getResourceAsStream("/" + file)) {
            return toByteArray(inputStream);
        }
    }

    private CourtSchedule getCourtSchedule() throws JsonProcessingException {
        final String courtScheduleDomainsJsonString = FileUtil.fileToString("/test-data/single-court-schedule-domain-data.json");

        return objectMapper.readValue(courtScheduleDomainsJsonString, CourtSchedule.class);
    }
}
