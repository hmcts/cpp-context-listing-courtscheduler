package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.START_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileNamePrefix;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime;

import org.springframework.beans.factory.annotation.Value;
// (removed) replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.DateRange;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.JudiciaryScheduleEnricher;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.MissingReferenceDataMappingLogger;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.RotaDataEnricher;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.RotaPeriodDateInfoProvider;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RotaFileProcessorService {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileProcessorService.class);

    @Inject
    private AzureBlobClientService azureBlobClientService;

    @Inject
    private RotaFileParser rotaFileParser;

    @Inject
    private RotaDataEnricher rotaDataEnricher;

    @Inject
    private JudiciaryScheduleEnricher judiciaryScheduleEnricher;

    @Inject
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @Inject
    private RotaFileProcessHistoryService rotaFileProcessHistoryService;

    @Inject
    private SessionsService sessionsService;

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private RotaFilePartialProcessor rotaFilePartialProcessor;

    @Inject
    private MissingReferenceDataMappingLogger missingReferenceDataMappingLogger;

    @Value("${rota.months.of.provisional.data.to.populate:6}")
    private String rotaMonthsOfProvisionalDataToPopulate;

    @Value("${rota.cycle.to.populate.length:28}")
    private String rotaCycleToPopulateLength;

    private static final String SNAPSHOT_NAME_PART = "_snapshot_";
    private static final String DUMMY_NAME_PART = "dummysupport";
    private static final String XML_NAME_PART = ".xml";
    private static final int TIMESTAMP_STRING_LENGTH = 16;

    private Map<String, Boolean> migratedMap = new ConcurrentHashMap<>();


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void downloadAndProcessForEachFile(final BlobContent blobContent, final String blobName, final String leaseId) {
        logger.info("downloadAndProcessForEachFile called for blob with name: {}", blobName);
        final byte[] blobByteArray = blobContent.getBlobByteArray();
        try {
            final long processStart = System.nanoTime();
            process(blobName, blobByteArray);
            final long processEnd = System.nanoTime();
            logger.info("PRF: Processing completed for blob {} in {} ms", blobName, (processEnd - processStart) / 1_000_000);
            logger.info("rota file process completed for blob with name: {}", blobName);
            final long fileLength = blobByteArray.length;
            // upload the files processed into archive container
            final long uploadStart = System.nanoTime();
            azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(blobByteArray), fileLength, blobName, empty());
            final long uploadEnd = System.nanoTime();
            logger.info("PRF: Upload completed for blob {} in {} ms", blobName, (uploadEnd - uploadStart) / 1_000_000);
            logger.info("rota file upload to output container completed for blob with name: {}", blobName);
            azureBlobClientService.releaseLease(blobName, leaseId, false);
            azureBlobClientService.deleteFile(blobName, empty());
            logger.info("rota file deletion from input container completed for blob with name: {}", blobName);
        } catch (Exception storageException) {
            azureBlobClientService.releaseLease(blobName, leaseId, true);

        }
    }

    private void process(final String fileName, final byte[] content) {
        RotaFileProcessHistory rotaFileProcessHistory = null;
        String executionId = randomUUID().toString();
        if (fileName.contains(SNAPSHOT_NAME_PART)) {
            if (checkFileDateTimeFieldAndIfNewerVersionOfSnapshotFileProcessed(fileName)) {
                return;
            }
            logger.info("DD-15703:processSnapshotRotaFile: before rotaFileProcessHistoryRepository.save");
            final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
            final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
            rotaFileProcessHistory = rotaFileProcessHistoryService.save(fileNamePrefix, fileDateTime, content, executionId);
            logger.info("DD-15703:processSnapshotRotaFile: after rotaFileProcessHistoryRepository.save");
        }
        this.migratedMap = sessionsService.migratedMapByOuCode();
        final Long parsingStartTime = System.nanoTime();
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(fileName, content);
        final Long parsingEndTime = System.nanoTime();
        logger.info("Time taken to parse the file: {} ms", (parsingEndTime - parsingStartTime) / 1000000);
        logger.info("File parsed successfully and parsed now enriching it.. for file: {}", fileName);
        if (fileName.contains(DUMMY_NAME_PART)) {
            logger.warn("Received dummy support file, hence skipping file processing, for file: {}", fileName);
            return;
        }

        final RotaPeriodDateInfoProvider rotaPeriodDateInfoProvider = new RotaPeriodDateInfoProvider(records);
        final LocalDate rotaPeriodStartDate = rotaPeriodDateInfoProvider.getRotaPeriodStartDate();
        final LocalDate rotaPeriodEndDate = rotaPeriodDateInfoProvider.getRotaPeriodEndDate();
        logger.info("rotaPeriodStartDate: {}, rotaPeriodEndDate: {}, rotaPeriodStartDay: {}, rotaPeriodEndDay: {}, masterRotaPeriodCutOffDate: {}, monthsBetweenRotaPeriod: {}", rotaPeriodStartDate, rotaPeriodEndDate,
                rotaPeriodDateInfoProvider.getRotaPeriodStartDay(), rotaPeriodDateInfoProvider.getRotaPeriodEndDay(), rotaPeriodEndDate, rotaPeriodDateInfoProvider.getMonthsBetweenRotaPeriod());

        // Create rota_file_process_history record for master rota files (same as snapshot files)
        if (!fileName.contains(SNAPSHOT_NAME_PART)) {
            logger.info("DD-15703:processMasterRotaFile: before rotaFileProcessHistoryRepository.save");
            final String fileNamePrefix = fileName.endsWith(".xml") ? fileName.substring(0, fileName.length() - (TIMESTAMP_STRING_LENGTH + XML_NAME_PART.length())) : fileName;
            final OffsetDateTime fileDateTime = FileUtil.getLJAFileTimeStampAsOffsetDateTime(fileName);

            executionId = randomUUID().toString();
            rotaFileProcessHistory = rotaFileProcessHistoryService.save(fileNamePrefix, fileDateTime, content, executionId);
            logger.info("DD-15703:processMasterRotaFile: after rotaFileProcessHistoryRepository.save - executionId: {}", executionId);
        }

        final List<String> locations = getLocationFromRecords(records);

        logger.info("DD-15703:RotaFileProcessor: Before getOuCodeFromCourtRoomMappingsByLocationId");
        final long ouCodesStart = System.nanoTime();
        final List<String> ouCodes = getOuCodesFromCourtRoomMappingsByLocationId(locations);
        final long ouCodesEnd = System.nanoTime();
        logger.info("PRF: Resolved OU codes for {} locations in {} ms", locations.size(), (ouCodesEnd - ouCodesStart) / 1_000_000);
        final List<String> nonMigratedOuCodes = ouCodes.stream().filter(ouCode -> FALSE.equals(migratedMap.get(ouCode))).toList();
        final List<String> migratedOuCodes = ouCodes.stream().filter(ouCode -> TRUE.equals(migratedMap.get(ouCode))).toList();
        logger.info("DD-15703:RotaFileProcessor: After getOuCodeFromCourtRoomMappingsByLocationId, ouCodes: {}, nonMigratedOuCodes: {}, migratedOuCodes: {}", ouCodes, nonMigratedOuCodes, migratedOuCodes);

        // Shared maps for aggregating missing data across migrated and non-migrated processing
        final Map<String, String> allMissingVenues = new ConcurrentHashMap<>();
        final Map<String, String> allMissingJudiciaries = new ConcurrentHashMap<>();
        final Map<String, List<String>> allMissingSessionsByOuCode = new ConcurrentHashMap<>();

        final long extractNonMigratedStart = System.nanoTime();
        final List<CourtSchedule> activeCourtSchedulesForNonMigratedOuCodesWithinRotaPeriod = sessionsService.getExtractedCourtSchedules(nonMigratedOuCodes, rotaPeriodStartDate, rotaPeriodEndDate);
        final long extractNonMigratedEnd = System.nanoTime();
        logger.info("PRF: Fetched existing non-migrated schedules: {} rows in {} ms", activeCourtSchedulesForNonMigratedOuCodesWithinRotaPeriod.size(), (extractNonMigratedEnd - extractNonMigratedStart) / 1_000_000);
        final long slotsNonMigratedStart = System.nanoTime();
        final Map<String, CourtSchedule> slotsForNonMigrated = receiveSlots(records, rotaPeriodEndDate, migratedMap, FALSE, activeCourtSchedulesForNonMigratedOuCodesWithinRotaPeriod, executionId, allMissingVenues);
        final long slotsNonMigratedEnd = System.nanoTime();
        logger.info("PRF: Enriched slots for non-migrated: {} entries in {} ms", slotsForNonMigrated.size(), (slotsNonMigratedEnd - slotsNonMigratedStart) / 1_000_000);

        final long extractMigratedStart = System.nanoTime();
        final List<CourtSchedule> activeCourtSchedulesForMigratedOuCodesWithinDateRange = sessionsService.getExtractedCourtSchedules(migratedOuCodes, rotaPeriodStartDate, rotaPeriodEndDate);
        final long extractMigratedEnd = System.nanoTime();
        logger.info("PRF: Fetched existing migrated schedules: {} rows in {} ms", activeCourtSchedulesForMigratedOuCodesWithinDateRange.size(), (extractMigratedEnd - extractMigratedStart) / 1_000_000);
        final long slotsMigratedStart = System.nanoTime();
        final Map<String, CourtSchedule> slotsForMigrated = receiveSlots(records, rotaPeriodEndDate, migratedMap, TRUE, activeCourtSchedulesForMigratedOuCodesWithinDateRange, executionId, allMissingVenues);
        final long slotsMigratedEnd = System.nanoTime();
        logger.info("PRF: Enriched slots for migrated: {} entries in {} ms", slotsForMigrated.size(), (slotsMigratedEnd - slotsMigratedStart) / 1_000_000);
        logger.info("received slots with slotsForNonMigrated size: {} of nonMigratedOuCodes: {} and slotsForMigrated: {} of migratedOuCodes: {}", slotsForNonMigrated.size(), nonMigratedOuCodes, slotsForMigrated.size(), migratedOuCodes);

        final long enrichNonMigratedStart = System.nanoTime();
        final Collection<CourtScheduleJudiciary> schedulesForNonMigrated = judiciaryScheduleEnricher.enrichJudiciarySchedules(slotsForNonMigrated, records, FALSE, activeCourtSchedulesForNonMigratedOuCodesWithinRotaPeriod, executionId, allMissingJudiciaries, allMissingSessionsByOuCode);
        final long enrichNonMigratedEnd = System.nanoTime();
        logger.info("PRF: Enriched judiciary schedules (non-migrated): {} in {} ms", schedulesForNonMigrated.size(), (enrichNonMigratedEnd - enrichNonMigratedStart) / 1_000_000);
        final long enrichMigratedStart = System.nanoTime();
        final Collection<CourtScheduleJudiciary> schedulesForMigrated = judiciaryScheduleEnricher.enrichJudiciarySchedules(slotsForMigrated, records, TRUE, activeCourtSchedulesForMigratedOuCodesWithinDateRange, executionId, allMissingJudiciaries, allMissingSessionsByOuCode);
        final long enrichMigratedEnd = System.nanoTime();
        logger.info("PRF: Enriched judiciary schedules (migrated): {} in {} ms", schedulesForMigrated.size(), (enrichMigratedEnd - enrichMigratedStart) / 1_000_000);
        logger.info("received schedules with schedules size: {} and schedulesForMigrated: {}", schedulesForNonMigrated.size(), schedulesForMigrated.size());
        logger.info("Enriched {} , saving it to DB..", slotsForNonMigrated.size());

        // Aggregate and log missing data once per execution
        if (!allMissingVenues.isEmpty() && isNotEmpty(executionId)) {
            missingReferenceDataMappingLogger.logMissingVenueMessages(allMissingVenues, executionId);
        }
        if (!allMissingJudiciaries.isEmpty() && isNotEmpty(executionId)) {
            missingReferenceDataMappingLogger.logJudiciaryMissingMessage(allMissingJudiciaries.values(), executionId);
        }
        if (!allMissingSessionsByOuCode.isEmpty() && isNotEmpty(executionId)) {
            missingReferenceDataMappingLogger.logMissingCourtSessions(allMissingSessionsByOuCode, executionId);
        }

        logger.info("DD-15703:RotaFileProcessor: Before checking for rota period");
        if (rotaPeriodStartDate.isBefore(LocalDate.now())) {
            logger.warn("process Rota File rota period start date is in the past. Rota period start date : {}", rotaPeriodStartDate);
        }

        final long businessTypeStart = System.nanoTime();
        final Map<String, BusinessType> businessTypesMap = referenceDataMapperService.getBusinessTypeMap();
        final long businessTypeEnd = System.nanoTime();
        logger.info("PRF: Loaded business type map with {} entries in {} ms", businessTypesMap.size(), (businessTypeEnd - businessTypeStart) / 1_000_000);

        if (isEmpty(ouCodes)) {
            logger.warn("process Rota File execution cancelled ----- ouCodes are null or empty. Unable to find court mappings for locations: {}", locations);
            return;
        }
        int partIndex = 1;
        if (fileName.contains(SNAPSHOT_NAME_PART)) {
            logger.info("DD-15703:RotaFileProcessor: Before  processSnapshotRotaFile");
            final List<DateRange> dateRanges = weeksCovering(rotaPeriodStartDate, rotaPeriodEndDate);
            // Collect the @Async per-week futures so we can wait for ALL weeks to finish
            // before declaring the file processed. Without this, downstream callers
            // (downloadAndProcessForEachFile → archive + delete from input) race ahead
            // while per-week deletes/inserts are still landing, leading to inconsistent
            // counts and the test flake where post-processing assertions see partial state.
            final List<CompletableFuture<Void>> weekFutures = new ArrayList<>();
            for(int i = 0; i < dateRanges.size(); i++) {
                final DateRange dateRange = dateRanges.get(i);
                final boolean isLastDateRange = (i == dateRanges.size() - 1);
                final Map<String, LocalDate> startAndEndDate = new HashMap<>();
                startAndEndDate.put(START_DATE.getLabel(), dateRange.getStart());
                startAndEndDate.put(END_DATE.getLabel(), dateRange.getEnd());
                final Map<String, CourtSchedule> filteredSlots = filterSlots(slotsForNonMigrated, dateRange);
                logger.info("Filtered Slots for Snapshot : {} within dateRange: {} - {}", filteredSlots.keySet(), dateRange.getStart(), dateRange.getEnd());
                weekFutures.add(rotaFilePartialProcessor.processSnapshotRotaFile(filteredSlots, slotsForMigrated, schedulesForNonMigrated, schedulesForMigrated, startAndEndDate, ouCodes, nonMigratedOuCodes, businessTypesMap, migratedMap, executionId, rotaFileProcessHistory, isLastDateRange));
                logger.info("snapshot rota file {} processing part number: {} within dateRange: {} - {}", fileName, partIndex, dateRange.getStart(), dateRange.getEnd());
                partIndex++;
            }
            // filter(nonNull) tolerates Mockito mocks returning null in unit tests; in production
            // the @Async proxy always returns a real CompletableFuture.
            CompletableFuture.allOf(weekFutures.stream().filter(Objects::nonNull).toArray(CompletableFuture[]::new)).join();
            logger.info("DD-15703:processSnapshotRotaFile: before rotaFileProcessHistoryRepository.update");
            if(rotaFileProcessHistory != null)
                rotaFileProcessHistoryService.update(rotaFileProcessHistory);
            logger.info("DD-15703:processSnapshotRotaFile: after rotaFileProcessHistoryRepository.update");
        } else {
            final List<DateRange> dateRanges = weeksCovering(rotaPeriodStartDate, rotaPeriodEndDate);
            final List<CompletableFuture<Void>> weekFutures = new ArrayList<>();
            for(int i = 0; i < dateRanges.size(); i++) {
                final DateRange dateRange = dateRanges.get(i);
                final boolean isLastDateRange = (i == dateRanges.size() - 1);
                final Map<String, CourtSchedule> filteredSlots = filterSlots(slotsForNonMigrated, dateRange);
                logger.info("Filtered Slots for Full Rota file : {}", filteredSlots.keySet());
                weekFutures.add(rotaFilePartialProcessor.processFullRotaFile(filteredSlots, slotsForMigrated, schedulesForNonMigrated, schedulesForMigrated, dateRange.getStart(), dateRange.getEnd(), ouCodes, nonMigratedOuCodes, businessTypesMap, migratedMap, executionId, rotaFileProcessHistory, isLastDateRange));
                logger.info("master rota file {} processing part number: {} within dateRange: {} - {}", fileName, partIndex, dateRange.getStart(), dateRange.getEnd());
                partIndex++;
            }
            CompletableFuture.allOf(weekFutures.stream().filter(Objects::nonNull).toArray(CompletableFuture[]::new)).join();
            logger.info("DD-15703:processMasterRotaFile: before rotaFileProcessHistoryRepository.update");
            if(rotaFileProcessHistory != null)
                rotaFileProcessHistoryService.update(rotaFileProcessHistory);
            logger.info("DD-15703:processMasterRotaFile: after rotaFileProcessHistoryRepository.update");
        }
    }

    private boolean checkFileDateTimeFieldAndIfNewerVersionOfSnapshotFileProcessed(final String fileName) {
        final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
        if (isNull(fileDateTime)) {
            logger.warn("fileDateTime part lacks of from the fileName: {}", fileName);
            return true;
        }
        final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
        final List<RotaFileProcessHistory> rotaFileProcessHistories = rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(fileNamePrefix, Timestamp.from(fileDateTime.toInstant()));
        boolean isNewerVersionOfSnapshotFileProcessed = isNotEmpty(rotaFileProcessHistories);
        if (isNewerVersionOfSnapshotFileProcessed) {
            logger.warn("There is a newer snapshot rota file has been processed already. Therefore, skipping.");
            return true;
        }
        return false;
    }

    private Map<String, CourtSchedule> receiveSlots(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                    final LocalDate rotaPeriodEndDate,
                                                    final Map<String, Boolean> migratedMap,
                                                    final Boolean migrated,
                                                    final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                                                    final String executionId,
                                                    final Map<String, String> missingReferenceDataMappingMap) {
        return rotaDataEnricher.enrichCourtListings(records, rotaPeriodEndDate, migratedMap, migrated, activeCourtSchedulesByOuCodesWithinRotaPeriod, executionId, missingReferenceDataMappingMap);
    }

    private List<String> getLocationFromRecords(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> locations = records.get(RotaPayload.LOCATION);

        return locations.entrySet().stream().flatMap(e -> e.getValue().keySet().stream()).toList();
    }

    private List<String> getOuCodesFromCourtRoomMappingsByLocationId(final List<String> locationIds) {
        final Map<String, String> locationIdOuCodeMap = new HashMap<>();
        referenceDataMapperService.getCourtRoomsMap().values()
                .forEach(courtRoom -> {
                    if(!locationIdOuCodeMap.containsKey(String.valueOf(courtRoom.getRotaLocationId()))) {
                        locationIdOuCodeMap.put(String.valueOf(courtRoom.getRotaLocationId()), courtRoom.getOucode());
                    }
                });

        final List<String> ouCodes = new ArrayList<>();
        locationIdOuCodeMap.keySet()
                .forEach(locationId -> {
                    if (locationIds.contains(locationId)) {
                        ouCodes.add(locationIdOuCodeMap.get(locationId));
                    }
                });

        return ouCodes;
    }

    public List<DateRange> weeksCovering(LocalDate start, LocalDate end) {
        final List<DateRange> result = new ArrayList<>();

        int weekIndex = 1;
        LocalDate previousWeekEnd = start;
        while (!start.isAfter(end) && (weekIndex == 1 || (weekIndex > 1 && start.isAfter(previousWeekEnd)))) {
            if(ChronoUnit.DAYS.between(start, end) > 6) {
                final LocalDate weekStart = start;
                start = start.plusDays(6);
                final LocalDate weekEnd = start;
                start = start.plusDays(1);
                result.add(new DateRange(weekStart, weekEnd));
                previousWeekEnd = weekEnd;
                logger.info("Week range of Week #{} - StartDate: {}, EndDate: {}", weekIndex, weekStart, weekEnd);
            } else {
                result.add(new DateRange(start, end));
                logger.info("Week range of Week #{} - StartDate: {}, EndDate: {}", weekIndex, start, end);
                start = start.plusDays(7);
                previousWeekEnd = end;
            }
            weekIndex++;
        }
        return result;
    }

    public Map<String, CourtSchedule> filterSlots(final Map<String, CourtSchedule> slots, final DateRange dateRange) {
        return slots.entrySet().stream()
                .filter(slot -> (slot.getValue().getSessionDate().isEqual(dateRange.getStart()) ||
                        slot.getValue().getSessionDate().isEqual(dateRange.getEnd()) ||
                        (slot.getValue().getSessionDate().isAfter(dateRange.getStart()) &&
                        slot.getValue().getSessionDate().isBefore(dateRange.getEnd()))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
