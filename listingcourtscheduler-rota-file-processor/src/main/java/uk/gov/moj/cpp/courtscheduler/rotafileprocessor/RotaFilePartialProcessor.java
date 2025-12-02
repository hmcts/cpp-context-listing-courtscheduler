package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;
import static javax.transaction.Transactional.TxType.REQUIRES_NEW;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.START_DATE;

import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.SlotAndScheduleInfo;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.BusinessTypeMatchingLogger;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
public class RotaFilePartialProcessor {

    private static final Logger logger = LoggerFactory.getLogger(RotaFilePartialProcessor.class);

    @Inject
    private CourtScheduleService courtScheduleService;

    @Inject
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Inject
    private SessionsService sessionsService;

    @Inject
    private AllocatedListingService allocatedListingService;

    @Inject
    private BusinessTypeMatchingLogger businessTypeMatchingLogger;

    @Asynchronous
    @Transactional(REQUIRES_NEW)
    public void processFullRotaFile(final Map<String, CourtSchedule> slots,
                                    final Collection<CourtScheduleJudiciary> schedules,
                                    final LocalDate startDate,
                                    final LocalDate endDate,
                                    final List<String> ouCodes,
                                    final Map<String, BusinessType> businessTypesMap,
                                    final String executionId) {
        logger.info("DD-15703:processFullRotaFile: started processing");
        final int numberOfDeletedUnAllocatedCourtScheduleJudiciaries = courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(startDate, endDate, ouCodes);
        logger.info("DD-15703:processFullRotaFile: after delete UnAllocated CourtScheduleJudiciariesEntriesForRotaPeriod with numberOfDeletedUnAllocatedCourtScheduleJudiciaries: {}", numberOfDeletedUnAllocatedCourtScheduleJudiciaries);

        final int numberOfDeletedUnAllocatedCourtSchedules = courtScheduleService.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, endDate, ouCodes);
        logger.info("DD-15703:processFullRotaFile: after delete UnAllocated CourtScheduleEntriesForRotaPeriod - numberOfDeletedUnAllocatedCourtSchedules: {} for ouCodes: {}", numberOfDeletedUnAllocatedCourtSchedules, ouCodes);

        final SlotAndScheduleInfo slotAndScheduleInfo = getExtractAndReceiveSlotAndScheduleInfo(ouCodes, slots, schedules, startDate, endDate, businessTypesMap, executionId);
        manageCourtSchedule(ouCodes, slots, schedules, businessTypesMap, slotAndScheduleInfo, startDate, endDate);
        logger.info("DD-15703:processFullRotaFile: after manageCourtSchedule");
    }

    @SuppressWarnings({"squid:S00112,", "squid:S1141"})
    @Asynchronous
    @Transactional(REQUIRES_NEW)
    public void processSnapshotRotaFile(final Map<String, CourtSchedule> slots,
                                        final Collection<CourtScheduleJudiciary> schedules,
                                        final Map<String, LocalDate> startAndEndDate,
                                        final List<String> ouCodes,
                                        final Map<String, BusinessType> businessTypesMap,
                                        final String executionId) {
        final LocalDate startDate = startAndEndDate.get(START_DATE.getLabel());
        final LocalDate endDate = startAndEndDate.get(END_DATE.getLabel());

        final long deleteUnallocatedCourtScheduleJudiciariesStartTime = System.currentTimeMillis();
        final int numberOfDeletedUnAllocatedCourtScheduleJudiciaries = courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(startDate, endDate, ouCodes);

        logger.info("DD-15703:processSnapshotRotaFile: after delete UnAllocated CourtScheduleJudiciariesEntriesForRotaPeriod with numberOfDeletedUnAllocatedCourtScheduleJudiciaries: {}", numberOfDeletedUnAllocatedCourtScheduleJudiciaries);
        final int numberOfDeletedUnAllocatedCourtSchedules = courtScheduleService.deleteUnAllocatedCourtScheduleEntriesForRotaPeriod(startDate, endDate, ouCodes);
        logger.info("DD-15703:processSnapshotRotaFile: after deleteUnAllocatedCourtScheduleEntriesForRotaPeriod - numberOfDeletedUnAllocatedCourtSchedules: {}", numberOfDeletedUnAllocatedCourtSchedules);
        final long deleteUnallocatedCourtScheduleJudiciariesEndTime = System.currentTimeMillis();
        logger.info("DD-15703:processSnapshotRotaFile: after delete UnAllocated CourtScheduleJudiciariesEntriesForRotaPeriod with numberOfDeletedUnAllocatedCourtScheduleJudiciaries: {} in {} ms", numberOfDeletedUnAllocatedCourtScheduleJudiciaries, deleteUnallocatedCourtScheduleJudiciariesEndTime - deleteUnallocatedCourtScheduleJudiciariesStartTime);
        final long extractAndReceiveSlotAndScheduleInfoStartTime = System.currentTimeMillis();
        final SlotAndScheduleInfo slotAndScheduleInfo = getExtractAndReceiveSlotAndScheduleInfo(ouCodes, slots, schedules, startDate, endDate, businessTypesMap, executionId);
        final long extractAndReceiveSlotAndScheduleInfoEndTime = System.currentTimeMillis();
        logger.info("DD-15703:processSnapshotRotaFile: after getExtractAndReceiveSlotAndScheduleInfo in {} ms", extractAndReceiveSlotAndScheduleInfoEndTime - extractAndReceiveSlotAndScheduleInfoStartTime);
        manageCourtSchedule(ouCodes, slots, schedules, businessTypesMap, slotAndScheduleInfo, startDate, endDate);
        logger.info("DD-15703:processSnapshotRotaFile: after manageCourtSchedule");
    }

    private SlotAndScheduleInfo getExtractAndReceiveSlotAndScheduleInfo(final List<String> ouCodes,
                                                                        final Map<String, CourtSchedule> slots,
                                                                        final Collection<CourtScheduleJudiciary> schedules,
                                                                        final LocalDate startDate,
                                                                        final LocalDate endDate,
                                                                        final Map<String, BusinessType> businessTypesMap,
                                                                        final String executionId) {
        final List<CourtSchedule> existingSlotList = sessionsService.getExtractedCourtSchedules(ouCodes, startDate, endDate);
        final List<Object[]> allocatedScheduleJudiciaries = courtScheduleJudiciaryService.getAllocatedScheduleJudiciaryInfo(startDate, endDate, ouCodes);
        final List<String> allocatedScheduleJudiciaryScheduleIds = isNotEmpty(allocatedScheduleJudiciaries) ? allocatedScheduleJudiciaries.stream().map(object -> (String) (object)[0]).toList() : emptyList();
        final List<String> allocatedScheduleJudiciaryIds = isNotEmpty(allocatedScheduleJudiciaries) ? allocatedScheduleJudiciaries.stream().map(object -> (String) (object)[1]).toList() : emptyList();

        final List<String> incomingSlotProfileIds = slots.values().stream().map(CourtSchedule::getListingProfileId).toList();
        final Map<String, CourtSchedule> existingSlotMap = existingSlotList.stream().collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, courtSchedule -> courtSchedule));

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
                        .toList(),
                allocatedListings, businessTypesMap);

        final Collection<CourtSchedule> schedulesToUpdate = calculateAvailableValues(
                slots.values().stream().filter(s -> existingSlotScheduleIds.contains(s.getCourtScheduleId()))
                        .filter(updateSlot -> filterMissingBusinessTypes(allBusinessTypeCodes, missingBusinessTypes, updateSlot))
                        .toList(),
                allocatedListings, businessTypesMap);

        final Collection<CourtScheduleJudiciary> newCourtScheduleJudiciaries = schedules.stream()
                .filter(schedule -> newSlotProfileIds.contains(schedule.getCourtListingProfileId())).toList();
        final Collection<CourtScheduleJudiciary> courtScheduleJudiciariesForExistingSlots = new ArrayList<>();
        schedules
                .stream()
                .filter(courtSchedule -> existingSlotScheduleIds.contains(courtSchedule.getCourtScheduleId())
                        && !(allocatedScheduleJudiciaryScheduleIds.contains(courtSchedule.getCourtScheduleId()) && allocatedScheduleJudiciaryIds.contains(courtSchedule.getJudiciaryId()))
                )
                .forEach(courtScheduleJudiciary -> {
                    final CourtSchedule existingSlotCourtSchedule = existingSlotMap.get(courtScheduleJudiciary.getCourtScheduleId());
                    if (nonNull(existingSlotCourtSchedule) && nonNull(existingSlotCourtSchedule.getCourtScheduleId())) {
                        courtScheduleJudiciary.setCourtScheduleId(existingSlotCourtSchedule.getCourtScheduleId());
                    }
                    courtScheduleJudiciariesForExistingSlots.add(courtScheduleJudiciary);
                });
        final Map<String, List<CourtScheduleJudiciary>> relatedJudiciarySchedules = courtScheduleJudiciaryService.findRelatedJudiciarySchedules(existingSlotScheduleIds);

        Map<String, CourtSchedule> newSlots = slots.entrySet().stream()
                .filter(newSlot -> newSlotScheduleIds.contains(newSlot.getValue().getCourtScheduleId()))
                .filter(slot -> filterMissingBusinessTypes(allBusinessTypeCodes, missingBusinessTypes, slot.getValue()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!missingBusinessTypes.isEmpty()) {
            businessTypeMatchingLogger.logMissingBusinessType(new ArrayList<>(missingBusinessTypes), executionId);
        }

        Map<String, Pair<String, String>> schedulesToUpdateMap= schedulesToUpdate.stream()
                .collect(toMap(CourtSchedule::getListingProfileId, scheduleToUpdate -> Pair.of(scheduleToUpdate.getCourtScheduleId(), scheduleToUpdate.getOuCode())));

        schedulesToUpdateMap = filterSlotsToUpdateMapByExistingSlots(schedulesToUpdateMap, existingSlotScheduleIds);
        newSlots = filterNewRecordsByExistingSlots(newSlots, existingSlotScheduleIds);
        return new SlotAndScheduleInfo(existingSlotScheduleIds, confirmedSlotIdsToDelete, slotsToUpdate, newCourtScheduleJudiciaries, courtScheduleJudiciariesForExistingSlots, relatedJudiciarySchedules, newSlots, schedulesToUpdateMap);
    }

    @SuppressWarnings("squid:S00112")
    @Transactional
    private void manageCourtSchedule(final List<String> ouCodes,
                                     final Map<String, CourtSchedule> slots,
                                     final Collection<CourtScheduleJudiciary> schedules,
                                     final Map<String, BusinessType> businessTypesMap,
                                     final SlotAndScheduleInfo slotAndScheduleInfo,
                                     final LocalDate startDate,
                                     final LocalDate endDate) {
        final long getAndUpdateSlotAndScheduleInfoStartTime = System.currentTimeMillis();
        final List<CourtSchedule> existingCourtSchedules = sessionsService.getExtractedCourtSchedules(ouCodes, startDate, endDate);
        sessionsService.updateSlotsAndSchedules(slotAndScheduleInfo, emptyMap(), schedules, businessTypesMap, ouCodes, existingCourtSchedules);
        final long getAndUpdateSlotAndScheduleInfoEndTime = System.currentTimeMillis();
        logger.info("DD-15703:manageCourtSchedule: after updateSlotsAndSchedules in {} ms", getAndUpdateSlotAndScheduleInfoEndTime - getAndUpdateSlotAndScheduleInfoStartTime);
    }

    private Map<String, Pair<String, String>> filterSlotsToUpdateMapByExistingSlots(final Map<String, Pair<String, String>> slotsToUpdate, final List<String> existingSlotIds) {
        final Map<String, Pair<String, String>> existingSlotsToUpdate = new HashMap<>();
        slotsToUpdate.keySet()
                .forEach(listingProfileId -> {
                    final Pair<String, String> courtScheduleIdAndOuCodePair = slotsToUpdate.get(listingProfileId);
                    final String courtScheduleId = courtScheduleIdAndOuCodePair.getLeft();
                    if (existingSlotIds.contains(courtScheduleId)) {
                        existingSlotsToUpdate.put(listingProfileId, courtScheduleIdAndOuCodePair);
                    }
                });

        return existingSlotsToUpdate;
    }

    private Map<String, CourtSchedule> filterNewRecordsByExistingSlots(final Map<String, CourtSchedule> newRecords, final List<String> existingSlotIds) {
        return newRecords;
    }

    @SuppressWarnings({"squid:S1067"})
    private List<String> confirmSlotsToDelete(final List<CourtSchedule> existingSlotList, final List<String> slotIdsToDelete) {
        return existingSlotList
                .stream()
                .filter(existingSlot ->
                        (existingSlot.isSlotBased() && slotIdsToDelete.contains(existingSlot.getCourtScheduleId()) && existingSlot.getMaxSlots().equals(existingSlot.getAvailableSlots()))
                                || (!existingSlot.isSlotBased() && slotIdsToDelete.contains(existingSlot.getCourtScheduleId()) && existingSlot.getMaxDuration().equals(existingSlot.getAvailableDuration())))
                .map(CourtSchedule::getCourtScheduleId)
                .toList();
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
                    .withCourtSchedule(courtSchedule)
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

    private boolean filterMissingBusinessTypes(final Set<String> allBusinessTypes, final Set<String> missingBusinessTypes, final CourtSchedule updateslot) {
        if (allBusinessTypes.contains(updateslot.getBusinessType())) {
            return true;
        } else {
            missingBusinessTypes.add(updateslot.getBusinessType());
            return false;
        }
    }

    private Integer getTotalListedAmountForCourtSchedule(final Map<String, Integer> allocatedListings, String courtScheduleId) {
        int totalAmount = 0;
        if (!allocatedListings.isEmpty() && allocatedListings.containsKey(courtScheduleId)) {
            totalAmount = allocatedListings.get(courtScheduleId);
        }
        return totalAmount;
    }
}
