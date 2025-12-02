package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.util.Objects.isNull;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.START_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileNamePrefix;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.justice.services.core.requester.Requester;
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
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.JudiciaryScheduleEnricher;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.RotaDataEnricher;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.RotaPeriodDateInfoProvider;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
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
    @Value(key ="rota.months.of.provisional.data.to.populate", defaultValue = "6")
    private String rotaMonthsOfProvisionalDataToPopulate;

    @Inject
    @Value(key = "rota.cycle.to.populate.length", defaultValue = "28")
    private String rotaCycleToPopulateLength;

    private static final String SNAPSHOT_NAME_PART = "_snapshot_";
    private static final String DUMMY_NAME_PART = "dummysupport";


    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public void downloadAndProcessForEachFile(final Requester requester, final BlobContent blobContent, final String blobName, final String leaseId) {
        logger.info("downloadAndProcessForEachFile called for blob with name: {}", blobName);
        final byte[] blobByteArray = blobContent.getBlobByteArray();
        try {
            final long processStart = System.nanoTime();
            process(blobName, blobByteArray, requester);
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

    private void process(final String fileName, final byte[] content, final Requester requester) {
        RotaFileProcessHistory rotaFileProcessHistory = null;
        String executionId = "";
        if (fileName.contains(SNAPSHOT_NAME_PART)) {
            if (checkFileDateTimeFieldAndIfNewerVersionOfSnapshotFileProcessed(fileName)) {
                return;
            }
            logger.info("DD-15703:processSnapshotRotaFile: before rotaFileProcessHistoryRepository.save");
            final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
            final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
            executionId = randomUUID().toString();
            rotaFileProcessHistory = rotaFileProcessHistoryService.save(fileNamePrefix, fileDateTime, content, executionId);
            logger.info("DD-15703:processSnapshotRotaFile: after rotaFileProcessHistoryRepository.save");
        }
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

        final List<String> locations = getLocationFromRecords(records);

        logger.info("DD-15703:RotaFileProcessor: Before getOuCodeFromCourtRoomMappingsByLocationId");
        final long ouCodesStart = System.nanoTime();
        final List<String> ouCodes = getOuCodesFromCourtRoomMappingsByLocationId(locations, requester);
        final long ouCodesEnd = System.nanoTime();
        logger.info("PRF: Resolved OU codes for {} locations in {} ms", locations.size(), (ouCodesEnd - ouCodesStart) / 1_000_000);
        logger.info("DD-15703:RotaFileProcessor: After getOuCodeFromCourtRoomMappingsByLocationId, ouCodes: {}", ouCodes);

        final long extractStart = System.nanoTime();
        final List<CourtSchedule> activeCourtSchedulesWithinRotaPeriod = sessionsService.getExtractedCourtSchedules(ouCodes, rotaPeriodStartDate, rotaPeriodEndDate);
        final long extractEnd = System.nanoTime();
        logger.info("PRF: Fetched existing schedules: {} rows in {} ms", activeCourtSchedulesWithinRotaPeriod.size(), (extractEnd - extractStart) / 1_000_000);
        final long slotsStart = System.nanoTime();
        final Map<String, CourtSchedule> slots = receiveSlots(records, rotaPeriodEndDate, activeCourtSchedulesWithinRotaPeriod, requester, executionId);
        final long slotsEnd = System.nanoTime();
        logger.info("PRF: Enriched slots: {} entries in {} ms", slots.size(), (slotsEnd - slotsStart) / 1_000_000);
        logger.info("received slots with size: {} for ouCodes: {}", slots.size(), ouCodes);

        final long enrichStart = System.nanoTime();
        final Collection<CourtScheduleJudiciary> schedules = judiciaryScheduleEnricher.enrichJudiciarySchedules(slots, records, activeCourtSchedulesWithinRotaPeriod, requester, executionId);
        final long enrichEnd = System.nanoTime();
        logger.info("PRF: Enriched judiciary schedules: {} in {} ms", schedules.size(), (enrichEnd - enrichStart) / 1_000_000);
        logger.info("received schedules with size: {}", schedules.size());
        logger.info("Enriched {} , saving it to DB..", slots.size());

        logger.info("DD-15703:RotaFileProcessor: Before checking for rota period");
        if (rotaPeriodStartDate.isBefore(LocalDate.now())) {
            logger.warn("process Rota File rota period start date is in the past. Rota period start date : {}", rotaPeriodStartDate);
        }

        final long businessTypeStart = System.nanoTime();
        final Map<String, BusinessType> businessTypesMap = referenceDataMapperService.getBusinessTypeMap(requester);
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
            for(final DateRange dateRange: dateRanges) {
                final Map<String, LocalDate> startAndEndDate = new HashMap<>();
                startAndEndDate.put(START_DATE.getLabel(), dateRange.getStart());
                startAndEndDate.put(END_DATE.getLabel(), dateRange.getEnd());
                final Map<String, CourtSchedule> filteredSlots = filterSlots(slots, dateRange);
                logger.info("Filtered Slots for Snapshot : {} within dateRange: {} - {}", filteredSlots.keySet(), dateRange.getStart(), dateRange.getEnd());
                rotaFilePartialProcessor.processSnapshotRotaFile(filteredSlots, schedules, startAndEndDate, ouCodes, businessTypesMap, executionId);
                logger.info("snapshot rota file {} processing part number: {} within dateRange: {} - {}", fileName, partIndex, dateRange.getStart(), dateRange.getEnd());
                partIndex++;
            }
            logger.info("DD-15703:processSnapshotRotaFile: before rotaFileProcessHistoryRepository.update");
            if(rotaFileProcessHistory != null)
                rotaFileProcessHistoryService.update(rotaFileProcessHistory);
            logger.info("DD-15703:processSnapshotRotaFile: after rotaFileProcessHistoryRepository.update");
        } else {
            final List<DateRange> dateRanges = weeksCovering(rotaPeriodStartDate, rotaPeriodEndDate);
            for(final DateRange dateRange: dateRanges) {
                final Map<String, CourtSchedule> filteredSlots = filterSlots(slots, dateRange);
                logger.info("Filtered Slots for Full Rota file : {}", filteredSlots.keySet());
                rotaFilePartialProcessor.processFullRotaFile(filteredSlots, schedules, dateRange.getStart(), dateRange.getEnd(), ouCodes, businessTypesMap, executionId);
                logger.info("master rota file {} processing part number: {} within dateRange: {} - {}", fileName, partIndex, dateRange.getStart(), dateRange.getEnd());
                partIndex++;
            }
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
                                                    final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                                                    final Requester requester,
                                                    final String executionId) {
        return rotaDataEnricher.enrichCourtListings(records, rotaPeriodEndDate, activeCourtSchedulesByOuCodesWithinRotaPeriod, requester, executionId);
    }

    private List<String> getLocationFromRecords(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> locations = records.get(RotaPayload.LOCATION);

        return locations.entrySet().stream().flatMap(e -> e.getValue().keySet().stream()).toList();
    }

    private List<String> getOuCodesFromCourtRoomMappingsByLocationId(final List<String> locationIds, final Requester requester) {
        final Map<String, String> locationIdOuCodeMap = new HashMap<>();
        referenceDataMapperService.getCourtRoomsMap(requester).values()
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
