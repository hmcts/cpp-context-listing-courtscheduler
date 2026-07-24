package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.time.LocalDate.parse;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.RandomStringUtils.random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.START_DATE;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.domain.rota.SlotAndScheduleInfo;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.BusinessTypeMatchingLogger;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFilePartialProcessorTest {

    @InjectMocks
    private RotaFilePartialProcessor rotaFilePartialProcessor;

    @Mock
    private CourtScheduleService courtScheduleService;

    @Mock
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private AllocatedListingService allocatedListingService;

    @Mock
    private BusinessTypeMatchingLogger businessTypeMatchingLogger;

    @Mock
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @Captor
    private ArgumentCaptor<List<String>> missingBusinessTypeCaptor;

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    private static final String PSV_AS_EXISTING_BUSINESS_TYPE = "PSV";
    private static final String NCPT_AS_EXISTING_BUSINESS_TYPE = "NCPT";
    private static final String CJU_AS_MISSING_BUSINESS_TYPE = "CJU";

    @Test
    void shouldCaptureMasterRotaFileAndProcess() throws IOException {
        final Map<String, CourtSchedule> filteredSlots = getSlotsForMigrated();
        final Map<String, CourtSchedule> slotsForMigrated = getSlotsForMigrated();
        final LocalDate partialStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate partialEndDate = LocalDate.of(2019, 10, 7);
        final List<String> ouCodes = List.of("B01KR00", "B53DJ00");
        final List<String> nonMigratedOuCodes = List.of("B01KR00", "B53DJ00");
        final Map<String, BusinessType> businessTypeMap = getRotaBusinessTypes().stream().collect(Collectors.toMap(BusinessType::getTypeCode, b -> b));
        final Map<String, Boolean> migratedMap = Map.of("CABC90", false);

        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList<>();
        final List<String> businessTypes = List.of(PSV_AS_EXISTING_BUSINESS_TYPE, CJU_AS_MISSING_BUSINESS_TYPE);
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), businessTypes.get(i % 2), true));
        }

        final Map<String, CourtSchedule> slots = new HashMap<>();
        IntStream.range(0, 5).forEach(index -> {
            final CourtSchedule courtSchedule = extractedSchedules.get(index);
            slots.put(courtSchedule.getListingProfileId(), courtSchedule);
        });

        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(eq(partialStartDate), eq(partialEndDate), anyList())).thenReturn(0);
        when(courtScheduleService.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(eq(partialStartDate), eq(partialEndDate), anyList())).thenReturn(0);
        doNothing().when(sessionsService).updateSlotsAndSchedules(any(uk.gov.moj.cpp.courtscheduler.domain.rota.SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), anyList(), anyList());
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(anyList())).thenReturn(emptyMap());

        rotaFilePartialProcessor.processFullRotaFile(filteredSlots, slotsForMigrated, emptyList(), emptyList(), partialStartDate, partialEndDate, ouCodes, nonMigratedOuCodes, businessTypeMap, migratedMap, randomUUID().toString(), null, false);

        verify(sessionsService, atLeastOnce()).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), anyList(), anyList());
        verify(businessTypeMatchingLogger, times(1)).logMissingBusinessType(missingBusinessTypeCaptor.capture(), anyString());
        verify(courtScheduleJudiciaryService, atLeastOnce()).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(eq(partialStartDate), eq(partialEndDate), anyList());
        verify(allocatedListingService, atLeastOnce()).getAllocatedListingsByCourtScheduleId(anyList());

        final List<List<String>> missingBusinessTypes = missingBusinessTypeCaptor.getAllValues();
        assertEquals(1, missingBusinessTypes.size());
        assertEquals(1, missingBusinessTypes.get(0).size());
        assertEquals(CJU_AS_MISSING_BUSINESS_TYPE, missingBusinessTypes.get(0).get(0));
    }

    @Test
    void shouldCaptureMasterRotaFileAndProcessForDurationBasedSlots() throws IOException {
        final Map<String, CourtSchedule> filteredSlots = getSlotsForMigratedDurationBased();
        final Map<String, CourtSchedule> slotsForMigrated = getSlotsForMigratedDurationBased();
        final LocalDate partialStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate partialEndDate = LocalDate.of(2019, 10, 7);
        final List<String> ouCodes = List.of("B01KR00", "B53DJ00");
        final Map<String, BusinessType> businessTypeMap = getRotaBusinessTypes().stream().collect(Collectors.toMap(BusinessType::getTypeCode, b -> b));
        final Map<String, Boolean> migratedMap = Map.of("CABC90", false);

        final LocalDate extractStartDate = LocalDate.of(2019, 10, 1);
        final List<CourtSchedule> extractedSchedules = new ArrayList<>();
        final List<String> businessTypes = List.of(NCPT_AS_EXISTING_BUSINESS_TYPE, CJU_AS_MISSING_BUSINESS_TYPE);
        for (int i = 0; i < 28; i++) {
            extractedSchedules.add(courtSchedule(extractStartDate.plusDays(i).toString(), businessTypes.get(i % 2), false));
        }

        final Map<String, CourtSchedule> slots = new HashMap<>();
        IntStream.range(0, 5).forEach(index -> {
            final CourtSchedule courtSchedule = extractedSchedules.get(index);
            slots.put(courtSchedule.getListingProfileId(), courtSchedule);
        });

        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(extractedSchedules);
        doNothing().when(sessionsService).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), anyList(), anyList());
        when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(eq(partialStartDate), eq(partialEndDate), anyList())).thenReturn(0);
        when(allocatedListingService.getAllocatedListingsByCourtScheduleId(anyList())).thenReturn(emptyMap());

        rotaFilePartialProcessor.processFullRotaFile(filteredSlots, slotsForMigrated, emptyList(), emptyList(), partialStartDate, partialEndDate, ouCodes, emptyList(), businessTypeMap, migratedMap, randomUUID().toString(), null, false);

        verify(sessionsService, atLeastOnce()).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), anyList(), anyList());
        verify(businessTypeMatchingLogger, atLeastOnce()).logMissingBusinessType(missingBusinessTypeCaptor.capture(), anyString());
        verify(courtScheduleJudiciaryService, atLeastOnce()).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(eq(partialStartDate), eq(partialEndDate), anyList());
        verify(allocatedListingService, atLeastOnce()).getAllocatedListingsByCourtScheduleId(anyList());
    }

    @Test
    void shouldCaptureSnapshotFileAndProcess() throws IOException {
        final Map<String, CourtSchedule> filteredSlots = getSlotsForMigrated();
        final Map<String, CourtSchedule> slotsForMigrated = getSlotsForMigrated();
        final LocalDate partialStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate partialEndDate = LocalDate.of(2019, 10, 7);
        final Map<String, LocalDate> startAndEndDate = new HashMap<>();
        startAndEndDate.put(START_DATE.getLabel(), partialStartDate);
        startAndEndDate.put(END_DATE.getLabel(), partialEndDate);
        final List<String> ouCodes = List.of("B01KR00", "B53DJ00");
        final Map<String, BusinessType> businessTypeMap = getRotaBusinessTypes().stream().collect(Collectors.toMap(BusinessType::getTypeCode, b -> b));
        final Map<String, Boolean> migratedMap = Map.of("CABC90", false);

        final LocalDate rotaPeriodStartDate = LocalDate.of(2019, 10, 1);
        final LocalDate rotaPeriodEndDate = LocalDate.of(2020, 3, 31);

        final Map<String, String> rotaDetails = new HashMap<>();
        rotaDetails.putIfAbsent("rotaPeriodStartDate", rotaPeriodStartDate.toString());
        rotaDetails.putIfAbsent("rotaPeriodEndDate", rotaPeriodEndDate.toString());

        when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(eq(partialStartDate), eq(partialEndDate), anyList())).thenReturn(0);

        rotaFilePartialProcessor.processSnapshotRotaFile(filteredSlots, slotsForMigrated, emptyList(), emptyList(), startAndEndDate, ouCodes, emptyList(), businessTypeMap, migratedMap, randomUUID().toString(), null, false);

        verify(sessionsService, atLeastOnce()).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), anyList(), anyList());
        verify(courtScheduleJudiciaryService, atLeastOnce()).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(eq(partialStartDate), eq(partialEndDate), anyList());
    }

    @Test
    void shouldUpdateProcessHistoryAfterFullRotaProcessingWhenLastDateRange() {
        final Map<String, CourtSchedule> slots = new HashMap<>();
        final Map<String, CourtSchedule> slotsForMigrated = new HashMap<>();
        final LocalDate startDate = LocalDate.of(2024, 1, 1);
        final LocalDate endDate = startDate.plusDays(6);
        final List<String> ouCodes = List.of("OU1");
        final Map<String, BusinessType> businessTypeMap = new HashMap<>();
        final Map<String, Boolean> migratedMap = Map.of("OU1", false);
        final RotaFileProcessHistory history = new RotaFileProcessHistory();

        when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(eq(startDate), eq(endDate), anyList())).thenReturn(0);
        when(courtScheduleService.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(eq(startDate), eq(endDate), anyList())).thenReturn(0);
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(emptyList());
        doNothing().when(sessionsService).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), anyList(), anyList());

        rotaFilePartialProcessor.processFullRotaFile(slots, slotsForMigrated, emptyList(), emptyList(), startDate, endDate, ouCodes, ouCodes, businessTypeMap, migratedMap, randomUUID().toString(), history, true);

        verify(rotaFileProcessHistoryService, times(1)).update(history);
    }

    @Test
    void shouldNotUpdateProcessHistoryWhenNotLastDateRangeForSnapshotProcessing() {
        final Map<String, CourtSchedule> slots = new HashMap<>();
        final Map<String, CourtSchedule> slotsForMigrated = new HashMap<>();
        final Map<String, LocalDate> startAndEndDate = Map.of(
                START_DATE.getLabel(), LocalDate.of(2024, 1, 1),
                END_DATE.getLabel(), LocalDate.of(2024, 1, 7)
        );
        final List<String> ouCodes = List.of("OU1");
        final Map<String, BusinessType> businessTypeMap = new HashMap<>();
        final Map<String, Boolean> migratedMap = Map.of("OU1", false);
        final RotaFileProcessHistory history = new RotaFileProcessHistory();

        when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(any(LocalDate.class), any(LocalDate.class), anyList())).thenReturn(0);
        when(courtScheduleService.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(any(LocalDate.class), any(LocalDate.class), anyList())).thenReturn(0);
        when(sessionsService.getExtractedCourtSchedules(anyList(), any(LocalDate.class), any(LocalDate.class))).thenReturn(emptyList());
        doNothing().when(sessionsService).updateSlotsAndSchedules(any(SlotAndScheduleInfo.class), anyMap(), anyCollection(), anyMap(), anyList(), anyList());

        rotaFilePartialProcessor.processSnapshotRotaFile(slots, slotsForMigrated, emptyList(), emptyList(), startAndEndDate, ouCodes, ouCodes, businessTypeMap, migratedMap, randomUUID().toString(), history, false);

        verify(rotaFileProcessHistoryService, never()).update(history);
    }

    private List<BusinessType> getRotaBusinessTypes() throws JsonProcessingException {
        final String businessTypesJsonStr = getPayload("test-data/business-types.json");
        return objectMapper.readValue(businessTypesJsonStr, new TypeReference<List<BusinessType>>(){});
    }

    private Map<String, CourtSchedule> getSlotsForMigrated() throws JsonProcessingException {
        final String slotsForMigratedJsonStr = getPayload("rotafileprocessor/partial-process/slots-for-migrated.json");
        return objectMapper.readValue(slotsForMigratedJsonStr, new TypeReference<Map<String, CourtSchedule>>(){});
    }

    private Map<String, CourtSchedule> getSlotsForMigratedDurationBased() throws JsonProcessingException {
        final String slotsForMigratedJsonStr = getPayload("rotafileprocessor/partial-process/slots-for-migrated-duration-based.json");
        return objectMapper.readValue(slotsForMigratedJsonStr, new TypeReference<Map<String, CourtSchedule>>(){});
    }

    private CourtSchedule courtSchedule(final String sessionDate, final String businessType, final Boolean slotBased) {
        return courtSchedule(sessionDate, null, random(10), businessType, null, null, null, null, slotBased);
    }

    private CourtSchedule courtSchedule(final String sessionDate,
                                        final String courtScheduleId,
                                        final String listingProfileId,
                                        final String businessType,
                                        final Integer maxDuration,
                                        final Integer availableSlots,
                                        final Integer availableDuration,
                                        final Integer maxSlots,
                                        final Boolean slotBased) {

        final String scheduleId = courtScheduleId != null ? courtScheduleId : randomUUID().toString();
        final String profileId = listingProfileId;
        final Integer mDuration = maxDuration != null ? maxDuration : 182;
        final Integer avSlots = availableSlots != null ? availableSlots : 125;
        final Integer avDuration = availableDuration != null ? availableDuration : 182;
        final Integer mSlots = maxSlots != null ? maxSlots : 125;

        return new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId(scheduleId)
                .withListingProfileId(profileId)
                .withSessionDate(parse(sessionDate))
                .withOuCode("CABC90")
                .withCourtRoomId("001c067d-eaca-4ce5-ad90-a366ef3e4bb6")
                .withCourtRoomNumber(1234)
                .withCourtHouseName("Liverpool Mags Court")
                .withCourtHouseId("0b9417b8-91b4-385d-9e01-069855777c4f")
                .withCourtRoomName("Court name1")
                .withOperationalUnit("ANC")
                .withBusinessType(businessType)
                .withPanel("PANEL")
                .withCourtSession("AM")
                .withMaxDuration(mDuration)
                .withAvailableSlots(avSlots)
                .withAvailableDuration(avDuration)
                .withMaxSlots(mSlots)
                .withSlotBased(slotBased)
                .build();
    }
}
