package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyMap;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_START_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.START_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileNamePrefix;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;
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
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.BusinessTypeMatchingLogger;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.JudiciaryScheduleEnricher;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.RotaDataEnricher;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.ProvisionalDataDateInfoProvider;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.ProvisionalDataExtractDateInfoProvider;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.ProvisionalDataProducer;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.ProvisionalSessionDateProvider;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;

import com.microsoft.azure.storage.AccessCondition;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlob;
import org.apache.commons.lang3.tuple.Pair;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ReferenceDataCache referenceDataCache;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private ProvisionalDataProducer provisionalDataProducer;

    @Inject
    private BusinessTypeMatchingLogger businessTypeMatchingLogger;

    @Inject
    private RotaFilePartialProcessor rotaFilePartialProcessor;

    @Inject
    @Value(key = "rota.master.data.days.length", defaultValue = "168")
    private String rotaMasterDataDaysLength;

    @Inject
    @Value(key ="rota.months.of.provisional.data.to.populate", defaultValue = "6")
    private String rotaMonthsOfProvisionalDataToPopulate;

    @Inject
    @Value(key = "rota.cycle.to.populate.length", defaultValue = "28")
    private String rotaCycleToPopulateLength;

    private static final String SNAPSHOT_NAME_PART = "_snapshot_";
    private static final String DUMMY_NAME_PART = "dummysupport";

    private Map<String, Boolean> migratedMap = new ConcurrentHashMap<>();

    private ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Asynchronous
    public void downloadAndProcessForEachFile(final Requester requester, final BlobContent blobContent, final String blobName) throws StorageException {
        logger.info("downloadAndProcessForEachFile called for blob with name: {}", blobName);
        CloudBlob blob = blobContent.getBlob();
        byte[] blobByteArray = blobContent.getBlobByteArray();
        process(blobName, blobByteArray, requester);
        AccessCondition accessCondition = new AccessCondition();
        accessCondition.setLeaseID(blobContent.getLeaseId());
        blob.releaseLease(accessCondition);
        logger.info("rota file process completed for blob with name: {}", blobName);
        final long fileLength = blobByteArray.length;
        // upload the files processed into archive container
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(blobByteArray), fileLength, blobName, empty());
        logger.info("rota file upload to output container completed for blob with name: {}", blobName);
        azureBlobClientService.deleteFile(blobName, empty());
        logger.info("rota file deletion from input container completed for blob with name: {}", blobName);
    }

    private void process(final String fileName, final byte[] content, final Requester requester) {
        final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
        if (fileName.contains(SNAPSHOT_NAME_PART)) {
            final boolean newerVersionOfSnapshotFileProcessed = checkIfNewerVersionOfSnapshotFileProcessed(fileName, fileDateTime);
            if (newerVersionOfSnapshotFileProcessed) {
                logger.warn("There is a newer snapshot rota file has been processed already. Therefore, skipping.");
                return;
            }
            if (isNull(fileDateTime)) {
                logger.warn("fileDateTime part lacks of from the fileName: {}", fileName);
                return;
            }
        }
        this.migratedMap = sessionsService.migratedMapByOuCode();
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(fileName, content);

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
        final List<String> ouCodes = getOuCodesFromCourtRoomMappingsByLocationId(locations, requester);
        final List<String> nonMigratedOuCodes = ouCodes.stream().filter(ouCode -> FALSE.equals(migratedMap.get(ouCode))).toList();
        final List<String> migratedOuCodes = ouCodes.stream().filter(ouCode -> TRUE.equals(migratedMap.get(ouCode))).toList();
        logger.info("DD-15703:RotaFileProcessor: After getOuCodeFromCourtRoomMappingsByLocationId, ouCodes: {}, nonMigratedOuCodes: {}, migratedOuCodes: {}", ouCodes, nonMigratedOuCodes, migratedOuCodes);

        final List<CourtSchedule> activeCourtSchedulesForNonMigratedOuCodesWithinRotaPeriod = sessionsService.getExtractedCourtSchedules(nonMigratedOuCodes, rotaPeriodStartDate, rotaPeriodEndDate);
        final Map<String, CourtSchedule> slotsForNonMigrated = receiveSlots(records, rotaPeriodEndDate, migratedMap, FALSE, activeCourtSchedulesForNonMigratedOuCodesWithinRotaPeriod, requester);

        final List<CourtSchedule> activeCourtSchedulesForMigratedOuCodesWithinDateRange = sessionsService.getExtractedCourtSchedules(migratedOuCodes, rotaPeriodStartDate, rotaPeriodEndDate);
        final Map<String, CourtSchedule> slotsForMigrated = receiveSlots(records, rotaPeriodEndDate, migratedMap, TRUE, activeCourtSchedulesForMigratedOuCodesWithinDateRange, requester);
        logger.info("received slots with slotsForNonMigrated size: {} and slotsForMigrated: {}", slotsForNonMigrated.size(), slotsForMigrated.size());

        final Collection<CourtScheduleJudiciary> schedulesForNonMigrated = judiciaryScheduleEnricher.enrichJudiciarySchedules(slotsForNonMigrated, records, FALSE, activeCourtSchedulesForNonMigratedOuCodesWithinRotaPeriod, requester);
        final Collection<CourtScheduleJudiciary> schedulesForMigrated = judiciaryScheduleEnricher.enrichJudiciarySchedules(slotsForMigrated, records, TRUE, activeCourtSchedulesForMigratedOuCodesWithinDateRange, requester);
        logger.info("received schedules with schedules size: {} and schedulesForMigrated: {}", schedulesForNonMigrated.size(), schedulesForMigrated.size());

        logger.info("Enriched {} , saving it to DB..", slotsForNonMigrated.size());

        logger.info("DD-15703:RotaFileProcessor: Before checking for rota period");
        if (rotaPeriodStartDate.isBefore(LocalDate.now())) {
            logger.warn("process Rota File rota period start date is in the past. Rota period start date : {}", rotaPeriodStartDate);
        }

        final Map<String, BusinessType> businessTypesMap = getBusinessTypeMap(requester);

        if (isEmpty(ouCodes)) {
            logger.warn("process Rota File execution cancelled ----- ouCodes are null or empty. Unable to find court mappings for locations: {}", locations);
            return;
        }
        int partIndex = 1;
        if (fileName.contains(SNAPSHOT_NAME_PART)) {
            final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
            logger.info("DD-15703:RotaFileProcessor: Before  processSnapshotRotaFile");
            final List<DateRange> dateRanges = weeksCovering(rotaPeriodStartDate, rotaPeriodEndDate);
            for(final DateRange dateRange: dateRanges) {
                final Map<String, LocalDate> startAndEndDate = new HashMap<>();
                startAndEndDate.put(START_DATE.getLabel(), dateRange.getStart());
                startAndEndDate.put(END_DATE.getLabel(), dateRange.getEnd());
                final Map<String, CourtSchedule> filteredSlots = filterSlots(slotsForNonMigrated, dateRange);
                logger.info("Filtered Slots for Snapshot : {}", filteredSlots.keySet());
                rotaFilePartialProcessor.processSnapshotRotaFile(filteredSlots, slotsForMigrated, schedulesForNonMigrated, schedulesForMigrated, startAndEndDate, ouCodes, nonMigratedOuCodes, businessTypesMap, migratedMap);
                logger.info("snapshot rota file processing part number: {}", partIndex);
                partIndex++;
            }
            logger.info("DD-15703:processSnapshotRotaFile: before rotaFileProcessHistoryRepository.update");
            rotaFileProcessHistoryService.update(fileNamePrefix, fileDateTime);
            logger.info("DD-15703:processSnapshotRotaFile: after rotaFileProcessHistoryRepository.update");
            logger.info("DD-15703:RotaFileProcessor: after courtScheduleRepository.update");
        } else {
            final List<DateRange> dateRanges = weeksCovering(rotaPeriodStartDate, rotaPeriodEndDate);
            for(final DateRange dateRange: dateRanges) {
                final Map<String, CourtSchedule> filteredSlots = filterSlots(slotsForNonMigrated, dateRange);
                logger.info("Filtered Slots for Full Rota file : {}", filteredSlots.keySet());
                rotaFilePartialProcessor.processFullRotaFile(filteredSlots, slotsForMigrated, schedulesForNonMigrated, schedulesForMigrated, dateRange.getStart(), dateRange.getEnd(), ouCodes, nonMigratedOuCodes, businessTypesMap, migratedMap);
                logger.info("master rota file processing part number: {}", partIndex);
                partIndex++;
            }
        }
    }

    private boolean checkIfNewerVersionOfSnapshotFileProcessed(final String fileName, final OffsetDateTime fileDateTime) {
        final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
        if (nonNull(fileDateTime)) {
            final List<RotaFileProcessHistory> rotaFileProcessHistories = rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(fileNamePrefix, Timestamp.from(fileDateTime.toInstant()));
            return isNotEmpty(rotaFileProcessHistories);
        }
        return false;
    }

    /**
     *
     * @param ouCodes
     * @param masterRotaPeriodCutOffDate
     * @param businessTypesMap
     * @param rotaPeriodDateInfoProvider
     *
     */
    private void createProvisionalSchedule(final List<String> ouCodes, final LocalDate masterRotaPeriodCutOffDate, final Map<String, BusinessType> businessTypesMap, final RotaPeriodDateInfoProvider rotaPeriodDateInfoProvider) {
        logger.info("rota.months.of.provisional.data.to.populate: {}", rotaMonthsOfProvisionalDataToPopulate);
        final int rotaFileCycleLength = getRotaFileCycleLength();
        final ProvisionalDataDateInfoProvider provisionalDataDateInfoProvider = new ProvisionalDataDateInfoProvider(rotaPeriodDateInfoProvider.getRotaPeriodEndDate(), masterRotaPeriodCutOffDate, getRotaMonthsOfProvisionalDataToPopulate(), rotaFileCycleLength);

        logger.info("rotaPeriodStartDate: {}, provisionalDataStartDay: {}", rotaPeriodDateInfoProvider.getRotaPeriodStartDate(), provisionalDataDateInfoProvider.getProvisionalDataStartDay());
        final ProvisionalDataExtractDateInfoProvider provisionalDataExtractDateInfoProvider = new ProvisionalDataExtractDateInfoProvider(
                rotaPeriodDateInfoProvider.getRotaPeriodStartDate(),
                provisionalDataDateInfoProvider.getProvisionalDataStartDay(),
                rotaFileCycleLength);

        final ProvisionalSessionDateProvider provisionalSessionDateProvider = new ProvisionalSessionDateProvider(provisionalDataExtractDateInfoProvider, provisionalDataDateInfoProvider, rotaFileCycleLength);
        logger.info("provisionalDataExtractDateInfoProvider[provisionalDataExtractStartDate: {}, provisionalDataExtractStartDay: {},"
                        + "provisionalDataExtractEndDate: {}, provisionalDataExtractEndDay: {}, provisionalDataExtractCountToPopulate: {}",
                provisionalDataExtractDateInfoProvider.getProvisionalDataExtractStartDate(),
                provisionalDataExtractDateInfoProvider.getProvisionalDataExtractStartDay(),
                provisionalDataExtractDateInfoProvider.getProvisionalDataExtractEndDate(),
                provisionalDataExtractDateInfoProvider.getProvisionalDataExtractEndDay(),
                provisionalDataExtractDateInfoProvider.getProvisionalDataExtractDaysCountToPopulate()
        );

        final Map<String, String> queryParams = queryParams(provisionalDataExtractDateInfoProvider);
        logger.info("sessionStartDate: {}, sessionEndDate: {}, cycleToPopulate: {}, ouCodes: {}", queryParams.get(SESSION_START_DATE.getLabel()), queryParams.get(SESSION_END_DATE.getLabel()), provisionalDataDateInfoProvider.getCyclesToPopulate(), ouCodes);
        final List<CourtSchedule> provisionalCourtSchedules = provisionalDataProducer.produceProvisionalData(provisionalDataExtractDateInfoProvider.getProvisionalDataExtractStartDate(), provisionalDataExtractDateInfoProvider.getProvisionalDataExtractEndDate(),
                provisionalDataDateInfoProvider.getCyclesToPopulate(), ouCodes, provisionalSessionDateProvider);

        if (!containsAllBusinessTypesOnTheseSlots(provisionalCourtSchedules, businessTypesMap)) {
            businessTypeMatchingLogger.logMissingBusinessType(getBusinessTypesNotConsistOnTheSystem(provisionalCourtSchedules, businessTypesMap));
        }

        final List<CourtSchedule> provisionalCourtSchedulesToBeProcessed = provisionalCourtSchedules.stream().filter(provisionalCourtSchedule -> !migratedMap.get(provisionalCourtSchedule.getOuCode())).toList();
        sessionsService.saveCourtSchedules(provisionalCourtSchedulesToBeProcessed, businessTypesMap);
    }

    private Map<String, CourtSchedule> receiveSlots(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                    final LocalDate rotaPeriodEndDate,
                                                    final Map<String, Boolean> migratedMap,
                                                    final Boolean migrated,
                                                    final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                                                    final Requester requester) {
        return rotaDataEnricher.enrichCourtListings(records, rotaPeriodEndDate, migratedMap, migrated, activeCourtSchedulesByOuCodesWithinRotaPeriod, requester);
    }

    private int getRotaMasterDataDaysLength() {
        return parseInt(rotaMasterDataDaysLength);
    }

    private List<String> getLocationFromRecords(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> locations = records.get(RotaPayload.LOCATION);

        return locations.entrySet().stream().flatMap(e -> e.getValue().keySet().stream()).toList();
    }

    private List<String> getOuCodesFromCourtRoomMappingsByLocationId(final List<String> locationIds, final Requester requester) {
        final Map<String, String> locationIdOuCodeMap = new HashMap<>();
        referenceDataService.getCourtRoomsMap(requester).values()
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

    private Map<String, BusinessType> getBusinessTypeMap(final Requester requester) {
        final List<BusinessType> businessTypes = referenceDataCache.getRotaBusinessTypes(requester);
        if (isNotEmpty(businessTypes)) {
            return businessTypes.stream()
                    .collect(Collectors.toMap(BusinessType::getTypeCode, Function.identity()));
        }
        return emptyMap();
    }

    private Integer getRotaMonthsOfProvisionalDataToPopulate() {
        if (nonNull(rotaMonthsOfProvisionalDataToPopulate)) {
            return Integer.valueOf(rotaMonthsOfProvisionalDataToPopulate);
        } else {
            return 6;
        }
    }

    private Map<String, String> queryParams(final ProvisionalDataExtractDateInfoProvider provisionalDataExtractDateInfoProvider) {
        final String extractStartDate = provisionalDataExtractDateInfoProvider.getProvisionalDataExtractStartDate().toString();
        final String extractEndDate = provisionalDataExtractDateInfoProvider.getProvisionalDataExtractEndDate().toString();
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put("sessionStartDate", extractStartDate);
        queryParams.put("sessionEndDate", extractEndDate);
        return queryParams;
    }

    private boolean containsAllBusinessTypesOnTheseSlots(final Collection<CourtSchedule> courtSchedules, final Map<String, BusinessType> businessTypesMap) {
        final Set<String> allBusinessTypesOnTheseSlots = courtSchedules.
                stream().collect(groupingBy(CourtSchedule::getBusinessType)).keySet();

        return businessTypesMap.keySet().containsAll(allBusinessTypesOnTheseSlots);
    }

    private List<String> getBusinessTypesNotConsistOnTheSystem(final Collection<CourtSchedule> courtSchedules, final Map<String, BusinessType> businessTypesMap) {
        final Set<String> allBusinessTypesOnTheseSlots = courtSchedules.
                stream().collect(groupingBy(CourtSchedule::getBusinessType)).keySet();

        final List<String> businessTypesNotConsistOnTheSystem = new ArrayList<>();
        final Set<String> allBusinessTypes = businessTypesMap.keySet();
        allBusinessTypesOnTheseSlots.forEach(
                slotBusinessType -> {
                    if (!allBusinessTypes.contains(slotBusinessType)) {
                        businessTypesNotConsistOnTheSystem.add(slotBusinessType);
                    }
                }
        );
        return businessTypesNotConsistOnTheSystem;
    }

    private int getRotaFileCycleLength() {
        final int DEFAULT_VALUE = 28;
        try {
            if (nonNull(rotaCycleToPopulateLength)) {
                return parseInt(rotaCycleToPopulateLength);
            }
            return DEFAULT_VALUE;
        } catch (final NumberFormatException numberFormatException) {
            logger.error("numberFormatException whilst converting rotaCycleToPopulateLength to integer. default value {} will be used", DEFAULT_VALUE, numberFormatException);
            return DEFAULT_VALUE;
        }
    }

    public List<DateRange> weeksCovering(LocalDate start, LocalDate end) {
        final List<DateRange> result = new ArrayList<>();

        int weekIndex = 1;
        while (!start.isAfter(end)) {
            if(ChronoUnit.DAYS.between(start, end) > 6) {
                final LocalDate weekStart = start;
                start = start.plusDays(6);
                final LocalDate weekEnd = start;
                start = start.plusDays(1);
                result.add(new DateRange(weekStart, weekEnd));
                logger.info("Week range of Week #{} - StartDate: {}, EndDate: {}", weekIndex, weekStart, weekEnd);
            } else {
                result.add(new DateRange(start, end));
                logger.info("Week range of Week #{} - StartDate: {}, EndDate: {}", weekIndex, start, end);
                start = start.plusDays(6);
            }
            weekIndex++;
        }
        return result;
    }

    public Map<String, CourtSchedule> filterSlots(Map<String, CourtSchedule> slots, DateRange dateRange) {
        return slots.entrySet().stream()
                .filter(slot -> (slot.getValue().getSessionDate().isEqual(dateRange.getStart()) ||
                        slot.getValue().getSessionDate().isEqual(dateRange.getEnd()) ||
                        (slot.getValue().getSessionDate().isAfter(dateRange.getStart()) &&
                        slot.getValue().getSessionDate().isBefore(dateRange.getEnd()))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
