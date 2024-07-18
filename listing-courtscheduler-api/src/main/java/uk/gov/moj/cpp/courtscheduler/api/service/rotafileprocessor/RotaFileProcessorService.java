package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_START_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.START_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileNamePrefix;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime;

import uk.gov.justice.services.common.configuration.Value;
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
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.provisionaldata.ProvisionalDataDateInfoProvider;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.provisionaldata.ProvisionalDataExtractDateInfoProvider;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.provisionaldata.ProvisionalDataProducer;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.provisionaldata.ProvisionalSessionDateProvider;
import uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.provisionaldata.RotaPeriodDateInfoProvider;
import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
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
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private SessionsService sessionsService;

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Inject
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Inject
    private AllocatedListingService allocatedListingService;

    @Inject
    private ReferenceDataCache referenceDataCache;

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private ProvisionalDataProducer provisionalDataProducer;

    @Inject
    private BusinessTypeMatchingLogger businessTypeMatchingLogger;

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

    public void captureRotaFilesAndProcessEach(final Requester requester) {
        logger.info("RotaFileProcessorService.captureRotaFilesAndProcessEach called");
        // download all the files in the input container
        final Map<String, byte[]> downloadedBlobsByteArrayMap = azureBlobClientService.downloadFiles();
        // for each of the files process rotasl
        downloadedBlobsByteArrayMap.keySet().forEach(blobName -> {
            final byte[] blobByteArray = downloadedBlobsByteArrayMap.get(blobName);

            process(blobName, blobByteArray, requester);

            final long fileLength = blobByteArray.length;
            // upload the files processed into archive container
            azureBlobClientService.uploadProcessedFiles(new ByteArrayInputStream(blobByteArray), fileLength, blobName);
        });

    }

    private void process(final String fileName, final byte[] content, final Requester requester) {
        final Map<RotaPayload, Map<String, Map<String, String>>> records = rotaFileParser.parse(fileName, content);

        logger.info("File parsed successfully and parsed now enriching it..");
        if (fileName.contains(DUMMY_NAME_PART)) {
            logger.warn("Received dummy support file, hence skipping file processing, for file: {}", fileName);
            return;
        }

        final RotaPeriodDateInfoProvider rotaPeriodDateInfoProvider = new RotaPeriodDateInfoProvider(records);
        final LocalDate rotaPeriodStartDate = rotaPeriodDateInfoProvider.getRotaPeriodStartDate();
        final LocalDate masterRotaPeriodCutOffDate = rotaPeriodStartDate.plusDays(getRotaMasterDataDaysLength());
        final LocalDate rotaPeriodEndDate = rotaPeriodDateInfoProvider.getRotaPeriodEndDate();
        logger.info("rotaPeriodStartDate: {}, rotaPeriodEndDate: {}, rotaPeriodStartDay: {}, rotaPeriodEndDay: {}, masterRotaPeriodCutOffDate: {}, monthsBetweenRotaPeriod: {}", rotaPeriodStartDate, rotaPeriodEndDate,
                rotaPeriodDateInfoProvider.getRotaPeriodStartDay(), rotaPeriodDateInfoProvider.getRotaPeriodEndDay(), masterRotaPeriodCutOffDate, rotaPeriodDateInfoProvider.getMonthsBetweenRotaPeriod());

        final Map<String, CourtSchedule> slots = receiveSlots(fileName, records, rotaPeriodEndDate, masterRotaPeriodCutOffDate, requester);
        final Collection<CourtScheduleJudiciary> schedules = judiciaryScheduleEnricher.enrichJudiciarySchedules(slots, records, requester);

        logger.info("Enriched {} , saving it to DB..", slots.size());

        logger.info("DD-15703:RotaFileProcessor: Before checking for rota period");
        if (rotaPeriodStartDate.isBefore(LocalDate.now())) {
            logger.warn("process Rota File rota period start date is in the past. Rota period start date : {}", rotaPeriodStartDate);
        }
        final List<String> locations = getLocationFromRecords(records);

        logger.info("DD-15703:RotaFileProcessor: Before getOuCodeFromCourtRoomMappingsByLocationId");
        final String ouCodes = getOuCodesFromCourtRoomMappingsByLocationId(locations, requester);
        logger.info("DD-15703:RotaFileProcessor: After getOuCodeFromCourtRoomMappingsByLocationId, ouCodes: {}", ouCodes);

        final Map<String, BusinessType> businessTypesMap = getBusinessTypeMap(requester);

        if (StringUtils.isBlank(ouCodes)) {
            logger.warn("process Rota File execution cancelled ----- ouCodes are null or empty. Unable to find court mappings for locations: {}", locations);
            return;
        }
        if (fileName.contains(SNAPSHOT_NAME_PART)) {
            final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
            final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);

            if (nonNull(fileDateTime)) {
                final List<RotaFileProcessHistory> rotaFileProcessHistories = rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(fileNamePrefix, Timestamp.from(fileDateTime.toInstant()));
                if (isNotEmpty(rotaFileProcessHistories)) {
                    logger.warn("There is a newer snapshot rota file has been processed already. Therefore, skipping.");
                } else {
                    logger.info("DD-15703:RotaFileProcessor: Before  processSnapshotRotaFile");
                    processSnapshotRotaFile(slots, schedules, rotaPeriodStartDate, rotaPeriodEndDate, ouCodes, fileNamePrefix, fileDateTime, businessTypesMap);
                }
            }
        } else {
            processFullRotaFile(slots, schedules, rotaPeriodStartDate, masterRotaPeriodCutOffDate, ouCodes, businessTypesMap, rotaPeriodDateInfoProvider);
        }
    }

    @SuppressWarnings("squid:S1141")
    @Transactional
    private void processFullRotaFile(final Map<String, CourtSchedule> slots,
                                     final Collection<CourtScheduleJudiciary> schedules,
                                     final LocalDate startDate,
                                     final LocalDate masterRotaPeriodCutOffDate,
                                     final String ouCodes,
                                     final Map<String, BusinessType> businessTypesMap,
                                     final RotaPeriodDateInfoProvider rotaPeriodDateInfoProvider) {

        logger.info("DD-15703:processFullRotaFile: started processing");
        courtScheduleJudiciaryRepository.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(startDate, masterRotaPeriodCutOffDate, ouCodes);
        logger.info("DD-15703:processFullRotaFile: after delete UnAllocated CourtScheduleJudiciariesEntriesForRotaPeriod");

        courtScheduleRepository.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, masterRotaPeriodCutOffDate, ouCodes);
        logger.info("DD-15703:processFullRotaFile: after delete UnAllocated CourtScheduleEntriesForRotaPeriod");

        courtScheduleRepository.deleteUnAllocatedProvisionalEntries(ouCodes);
        logger.info("DD-15703:processFullRotaFile: after delete UnAllocated ProvisionalEntries");

        manageCourtSchedule(ouCodes, slots, schedules, startDate, masterRotaPeriodCutOffDate, businessTypesMap);
        logger.info("DD-15703:processFullRotaFile: after manageCourtSchedule");


        createProvisionalSchedule(ouCodes, masterRotaPeriodCutOffDate, businessTypesMap, rotaPeriodDateInfoProvider);
    }

    @SuppressWarnings({"squid:S00112,", "squid:S1141"})
    @Transactional
    protected void processSnapshotRotaFile(final Map<String, CourtSchedule> slots,
                                           final Collection<CourtScheduleJudiciary> schedules,
                                           final LocalDate startDate,
                                           final LocalDate endDate,
                                           final String ouCodes,
                                           final String fileNamePrefix,
                                           final OffsetDateTime fileDate,
                                           final Map<String, BusinessType> businessTypesMap) {
        logger.info("DD-15703:processSnapshotRotaFile: began transaction");
        int numberOfDeletedUnAllocatedCourtScheduleJudiciaries = courtScheduleJudiciaryRepository.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(startDate, endDate, ouCodes);
        logger.info("DD-15703:processSnapshotRotaFile: after delete UnAllocated CourtScheduleJudiciariesEntriesForRotaPeriod - numberOfDeletedUnAllocatedCourtScheduleJudiciaries: {}", numberOfDeletedUnAllocatedCourtScheduleJudiciaries);

        int numberOfDeletedUnAllocatedCourtSchedules = courtScheduleRepository.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, endDate, ouCodes);
        logger.info("DD-15703:processSnapshotRotaFile: after deleteUnAllocatedCourtScheduleEntriesForRotaPeriod - numberOfDeletedUnAllocatedCourtSchedules: {}", numberOfDeletedUnAllocatedCourtSchedules);

        manageCourtSchedule(ouCodes, slots, schedules, startDate, endDate, businessTypesMap);
        logger.info("DD-15703:processSnapshotRotaFile: after manageCourtSchedule");

        logger.info("DD-15703:processSnapshotRotaFile: before rotaFileProcessHistoryRepository.update");
        rotaFileProcessHistoryService.update(fileNamePrefix, fileDate);
        logger.info("DD-15703:processSnapshotRotaFile: after rotaFileProcessHistoryRepository.update");
    }

    @SuppressWarnings("squid:S00112")
    private void manageCourtSchedule(final String ouCodes,
                                     final Map<String, CourtSchedule> slots,
                                     final Collection<CourtScheduleJudiciary> schedules,
                                     final LocalDate startDate,
                                     final LocalDate endDate,
                                     final Map<String, BusinessType> businessTypesMap) {
        final Map<String, String> queryParams = new HashMap<>();
        queryParams.put(START_DATE.getLabel(), startDate.toString());
        queryParams.put(END_DATE.getLabel(), endDate.toString());

        final List<CourtSchedule> existingSlotList = sessionsService.getExtractedCourtSchedules(ouCodes, startDate, endDate);

        final List<String> incomingSlotProfileIds = slots.values().stream().map(CourtSchedule::getListingProfileId).toList();

        final List<String> existingSlotScheduleIds = existingSlotList.stream().map(CourtSchedule::getCourtScheduleId).toList();
        final List<String> incomingSlotScheduleIds = slots.values().stream().map(CourtSchedule::getCourtScheduleId).toList();

        final List<String> newSlotProfileIds = new ArrayList<>(incomingSlotProfileIds);
        final List<String> newSlotScheduleIds = new ArrayList<>(incomingSlotScheduleIds);
        final List<String> slotScheduleIdsToDelete = new ArrayList<>(existingSlotScheduleIds);
        final Set<String> allBusinessTypeCodes = getBusinessTypeCodes(businessTypesMap);

        final Set<String> missingBusinessTypes = new HashSet<>();

        newSlotScheduleIds.removeAll(existingSlotScheduleIds);
        slotScheduleIdsToDelete.removeAll(incomingSlotScheduleIds);

        final List<String> confirmedSlotIdsToDelete = confirmSlotsToDelete(existingSlotList, slotScheduleIdsToDelete);
        Map<String, Integer> allocatedListings = new HashMap<>();
        if (isNotEmpty(existingSlotScheduleIds)) {
            allocatedListings = allocatedListingService.getAllocatedListingsByCourtScheduleId(existingSlotScheduleIds);
        }

        final Collection<CourtSchedule> slotsToUpdate = calculateAvailableValues(
                slots.values().stream().filter(s -> existingSlotScheduleIds.contains(s.getCourtScheduleId()))
                        .filter(updateSlot -> filterMissingBusinessTypes(allBusinessTypeCodes, missingBusinessTypes, updateSlot))
                        .collect(toList()),
                allocatedListings, businessTypesMap);

        final Collection<CourtScheduleJudiciary> newCourtScheduleJudiciaries = schedules.stream()
                .filter(s -> newSlotProfileIds.contains(s.getCourtListingProfileId())).collect(toList());
        final Map<String, List<CourtScheduleJudiciary>> relatedJudiciarySchedules = courtScheduleJudiciaryService.findRelatedJudiciarySchedules(existingSlotScheduleIds);


        Map<String, CourtSchedule> newSlots = slots.entrySet().stream()
                .filter(newSlot -> newSlotScheduleIds.contains(newSlot.getValue().getCourtScheduleId()))
                .filter(slot -> filterMissingBusinessTypes(allBusinessTypeCodes, missingBusinessTypes, slot.getValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!missingBusinessTypes.isEmpty()) {
            businessTypeMatchingLogger.logMissingBusinessType(new ArrayList<>(missingBusinessTypes));
        }

        Map<String, Pair<String, String>> slotsToUpdateMap = slotsToUpdate.stream()
                .collect(toMap(CourtSchedule::getListingProfileId, slotToUpdate -> Pair.of(slotToUpdate.getCourtScheduleId(), slotToUpdate.getOuCode())));

        slotsToUpdateMap = filterSlotsToUpdateMapByExistingSlots(slotsToUpdateMap, existingSlotScheduleIds);
        newSlots = filterNewRecordsByExistingSlots(newSlots, existingSlotScheduleIds);


        logger.info("DD-15703:RotaFileProcessor: before courtScheduleRepository.update");

        sessionsService.updateSlotsAndSchedules(existingSlotScheduleIds,
                newSlots,
                newCourtScheduleJudiciaries,
                slotsToUpdate,
                slotsToUpdateMap,
                schedules,
                relatedJudiciarySchedules,
                confirmedSlotIdsToDelete,
                businessTypesMap);

        logger.info("DD-15703:RotaFileProcessor: after courtScheduleRepository.update");
    }

    private void createProvisionalSchedule(final String ouCodes, final LocalDate masterRotaPeriodCutOffDate, final Map<String, BusinessType> businessTypesMap, final RotaPeriodDateInfoProvider rotaPeriodDateInfoProvider) {
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

        final List<CourtSchedule> provisionalCourtSchedulesToBeProcessed = provisionalCourtSchedules.stream().filter(provisionalCourtSchedule -> !sessionsService.isMigrated(provisionalCourtSchedule.getOuCode())).toList();
        sessionsService.saveCourtSchedules(provisionalCourtSchedulesToBeProcessed, businessTypesMap);
    }

    private Map<String, CourtSchedule> receiveSlots(final String name,
                                                    final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                    final LocalDate rotaPeriodEndDate,
                                                    final LocalDate masterRotaPeriodCutOffDate,
                                                    final Requester requester) {
        if (name.contains(SNAPSHOT_NAME_PART)) {
            return rotaDataEnricher.enrichCourtListings(records, rotaPeriodEndDate, requester);
        } else {
            return rotaDataEnricher.enrichCourtListings(records, masterRotaPeriodCutOffDate, requester);
        }
    }

    private int getRotaMasterDataDaysLength() {
        return parseInt(rotaMasterDataDaysLength);
    }

    private List<String> getLocationFromRecords(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> locations = records.get(RotaPayload.LOCATION);

        return locations.entrySet().stream().flatMap(e -> e.getValue().keySet().stream()).collect(toList());
    }

    private String getOuCodesFromCourtRoomMappingsByLocationId(final List<String> locationIds, final Requester requester) {
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

        return String.join(",", ouCodes);
    }

    @SuppressWarnings({"squid:S1067"})
    private List<String> confirmSlotsToDelete(final List<CourtSchedule> existingSlotList, final List<String> slotIdsToDelete) {
        return existingSlotList
                .stream()
                .filter(existingSlot ->
                        existingSlot.isSlotBased() && slotIdsToDelete.contains(existingSlot.getCourtScheduleId()) && existingSlot.getMaxSlots().equals(existingSlot.getAvailableSlots())
                                || !existingSlot.isSlotBased() && slotIdsToDelete.contains(existingSlot.getCourtScheduleId()) && existingSlot.getMaxDuration().equals(existingSlot.getAvailableDuration()))
                .map(CourtSchedule::getCourtScheduleId)
                .collect(toList());
    }

    @SuppressWarnings({"squid:S1188"})
    protected Collection<CourtSchedule> calculateAvailableValues(final Collection<CourtSchedule> newSlotValues,
                                                                 final Map<String, Integer> allocatedListings,
                                                                 final Map<String, BusinessType> businessTypesMap) {

        final List<CourtSchedule> updatedSlots = new ArrayList<>();

        newSlotValues.forEach(courtSchedule -> {
            final int currentMaxSlots = courtSchedule.getMaxSlots();
            final int currentMaxDuration = courtSchedule.getMaxDuration();
            int newAvailableSlots = courtSchedule.getAvailableSlots();
            int newAvailableDuration = courtSchedule.getAvailableDuration();
            final boolean isSlotBased = businessTypesMap.get(courtSchedule.getBusinessType()).isSlot();
            final int totalListedAmount = getTotalListedAmountForCourtSchedule(allocatedListings, courtSchedule.getCourtScheduleId());

            if (isSlotBased) {
                newAvailableSlots = currentMaxSlots - totalListedAmount;
            } else {
                newAvailableDuration = currentMaxDuration - totalListedAmount;
            }

            final CourtSchedule updatedCourtSchedule = new CourtSchedule.CourtScheduleBuilder()
                    .withCourtScheduleId(courtSchedule.getCourtScheduleId())
                    .withListingProfileId(courtSchedule.getListingProfileId())
                    .withSessionDate(courtSchedule.getSessionDate())
                    .withOuCode(courtSchedule.getOuCode())
                    .withCourtRoomNumber(courtSchedule.getCourtRoomNumber())
                    .withCourtRoomId(courtSchedule.getCourtRoomId())
                    .withCourtHouseId(courtSchedule.getCourtHouseId())
                    .withCourtHouseName(courtSchedule.getCourtHouseName())
                    .withCourtRoomName(courtSchedule.getCourtRoomName())
                    .withOperationalUnit(courtSchedule.getOperationalUnit())
                    .withBusinessType(courtSchedule.getBusinessType())
                    .withPanel(courtSchedule.getPanel())
                    .withCourtSession(courtSchedule.getCourtSession())
                    .withMaxDuration(currentMaxDuration)
                    .withAvailableSlots(newAvailableSlots)
                    .withAvailableDuration(newAvailableDuration)
                    .withMaxSlots(currentMaxSlots)

                    .build();
            updatedSlots.add(updatedCourtSchedule);
        });
        return updatedSlots;
    }

    private Set<String> getBusinessTypeCodes(final Map<String, BusinessType> businessTypeMap) {
        if (businessTypeMap.isEmpty()) {
            return emptySet();
        }
        return businessTypeMap.keySet();
    }

    private Map<String, BusinessType> getBusinessTypeMap(final Requester requester) {
        final List<BusinessType> businessTypes = referenceDataCache.getRotaBusinessTypes(requester);
        if (isNotEmpty(businessTypes)) {
            return businessTypes.stream()
                    .collect(Collectors.toMap(BusinessType::getTypeCode, Function.identity()));
        }
        return emptyMap();
    }

    private boolean filterMissingBusinessTypes(final Set<String> allBusinessTypes, final Set<String> missingBusinessTypes, final CourtSchedule updateslot) {
        if (allBusinessTypes.contains(updateslot.getBusinessType())) {
            return true;
        } else {
            missingBusinessTypes.add(updateslot.getBusinessType());
            return false;
        }
    }

    private Map<String, Pair<String, String>> filterSlotsToUpdateMapByExistingSlots(final Map<String, Pair<String, String>> slotsToUpdate, final List<String> existingSlotIds) {
        final Map<String, Pair<String, String>> existingSlotsToUpdate = new HashMap<>();
        slotsToUpdate.keySet()
                .forEach(listingProfileId -> {
                    final Pair<String, String> courtScheduleIdAndOuCodePair = slotsToUpdate.get(listingProfileId);
                    final String courtScheduleId = courtScheduleIdAndOuCodePair.getLeft();
                    final String ouCode = courtScheduleIdAndOuCodePair.getRight();
                    if (existingSlotIds.contains(courtScheduleId) && !sessionsService.isMigrated(ouCode)) {
                        existingSlotsToUpdate.put(listingProfileId, courtScheduleIdAndOuCodePair);
                    }
                });

        return existingSlotsToUpdate;
    }

    private Map<String, CourtSchedule> filterNewRecordsByExistingSlots(final Map<String, CourtSchedule> newRecords, final List<String> existingSlotIds) {
        final Map<String, CourtSchedule> existingSlotsNewRecords = new HashMap<>();
        newRecords.keySet()
                .forEach(newRecordListingProfileId -> {
                    final CourtSchedule newRecordCourtSchedule = newRecords.get(newRecordListingProfileId);
                    if (existingSlotIds.contains(newRecordCourtSchedule.getCourtScheduleId())) {
                        existingSlotsNewRecords.put(newRecordListingProfileId, newRecordCourtSchedule);
                    }
                });

        return existingSlotsNewRecords;
    }

    private Integer getTotalListedAmountForCourtSchedule(final Map<String, Integer> allocatedListings, String courtScheduleId) {
        int totalAmount = 0;
        if (!allocatedListings.isEmpty() && allocatedListings.containsKey(courtScheduleId)) {
            totalAmount = allocatedListings.get(courtScheduleId);
        }
        return totalAmount;
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
}
