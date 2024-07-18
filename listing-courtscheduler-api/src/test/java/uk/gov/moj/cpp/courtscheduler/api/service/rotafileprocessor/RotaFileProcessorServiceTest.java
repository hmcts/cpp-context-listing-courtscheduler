package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.api.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.api.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.api.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.BusinessTypeMatchingLogger;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.JudiciaryScheduleEnricher;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaDataEnricher;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.provisionaldata.ProvisionalDataProducer;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileProcessorServiceTest {

    @InjectMocks
    @Spy
    private RotaFileProcessorService rotaFileProcessorService;

    @Mock
    private AzureBlobClientService azureBlobClientService;

    @Mock
    private RotaFileParser rotaFileParser;

    @Mock
    private RotaDataEnricher rotaDataEnricher;

    @Mock
    private JudiciaryScheduleEnricher judiciaryScheduleEnricher;

    @Mock
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @Mock
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Mock
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Mock
    private AllocatedListingService allocatedListingService;

    @Mock
    private ReferenceDataCache referenceDataCache;

    @Mock
    private ReferenceDataService referenceDataService;

    @Mock
    private ProvisionalDataProducer provisionalDataProducer;

    @Mock
    private BusinessTypeMatchingLogger businessTypeMatchingLogger;

    @Mock
    private Requester requester;

    @Mock
    private Map<RotaPayload, Map<String, Map<String, String>>> records;

    @Mock
    private Map<String, CourtSchedule> slots;

    @Mock
    private Collection<CourtScheduleJudiciary> schedules;

    private Map<String, Map<String, String>> rotaPeriodMap;

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @BeforeEach
    public void setUp() {
        setField(rotaFileProcessorService, "rotaMasterDataDaysLength", "168");
        setField(rotaFileProcessorService, "rotaMonthsOfProvisionalDataToPopulate", "6");
        setField(rotaFileProcessorService, "rotaCycleToPopulateLength", "28");
    }

    @Test
    void shouldCaptureRotaFilesAndProcessEach() throws IOException {

        final String file = "rotafileprocessor/rota_payload.xml";
        final String blobName = "lja_avonandsomerset_rota_20240314T160815Z.xml";
        final byte[] blobContent = givenBlobContent(file);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        final Map<String, byte[]> downloadedBlobsByteArrayMap = Map.of(blobName, blobContent);
        when(azureBlobClientService.downloadFiles()).thenReturn(downloadedBlobsByteArrayMap);
        doNothing().when(azureBlobClientService).uploadProcessedFiles(any(InputStream.class), anyLong(), eq(blobName));

        when(rotaFileParser.parse(any(), any())).thenReturn(records);
        when(rotaDataEnricher.enrichCourtListings(eq(records), any(LocalDate.class), eq(requester))).thenReturn(slots);
        when(judiciaryScheduleEnricher.enrichJudiciarySchedules(eq(slots), eq(records), eq(requester))).thenReturn(schedules);
        when(referenceDataService.getCourtRoomsMap(eq(requester))).thenReturn(getCourtRoomsMap());

        final Map<String, String> rotaDetails = new HashMap();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        rotaPeriodMap = new HashMap();
        rotaPeriodMap.putIfAbsent(RotaPayload.ROTA_PERIOD.toString(), rotaDetails);

        when(records.get(RotaPayload.ROTA_PERIOD)).thenReturn(rotaPeriodMap);
        when(records.get(RotaPayload.LOCATION)).thenReturn(new HashMap<>());

        rotaFileProcessorService.captureRotaFilesAndProcessEach(requester);

        verify(judiciaryScheduleEnricher, atLeastOnce()).enrichJudiciarySchedules(eq(slots), eq(records), eq(requester));
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaFileParserTest.class.getClassLoader().getResourceAsStream(file)) {

            return toByteArray(inputStream);
        }
    }

    private Map<UUID, CourtRoom> getCourtRoomsMap() throws JsonProcessingException {
        final String courtRoomsDataJsonStr = FileUtil.fileToString("/test-data/court-rooms-domain-data.json");

        return objectMapper.readValue(courtRoomsDataJsonStr, new TypeReference<List<CourtRoom>>(){})
                .stream()
                .collect(Collectors.toMap(courtRoom -> UUID.fromString(courtRoom.getCourtroomId()), c -> c));
    }
}
