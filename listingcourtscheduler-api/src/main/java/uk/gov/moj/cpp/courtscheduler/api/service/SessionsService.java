package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.api.CommonUtils.getValidationResult;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.converter.CourtScheduleToDeleteResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.api.service.mapper.CourtScheduleMapper;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleDeleteResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeMigrateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtMigrationRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.transaction.Transactional;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.data.api.QueryInvocationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SessionsService {

    private static final Logger logger = LoggerFactory.getLogger(SessionsService.class);

    private static final String BUSINESS_TYPE_NOT_FOUND = "Business Type not found";
    private static final String COURTROOM_NOT_FOUND = "Court Room not found";
    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private CourtMigrationRepository courtMigrationRepository;
    @Inject
    private ReferenceDataCache referenceDataCache;
    @Inject
    private CourtScheduleToDeleteResponseConverter courtScheduleToDeleteResponseConverter;
    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    public void create(CreateSessionRequestParam createSessionRequestParam, Requester requester) {
        final List<CourtSchedule> courtScheduleList = new ArrayList<>();
        final List<Session> sessionList = createSessionRequestParam.getSessionList();
        final RepeatPattern repeatPattern = createSessionRequestParam.getRepeatPattern();
        final LocalDate startDate = repeatPattern.getStartDate();
        final LocalDate endDate = repeatPattern.getEndDate();

        if (repeatPattern.getFrequency().equals(RepeatFrequency.ONCE)) {
            processOnceFrequency(sessionList, startDate, courtScheduleList, requester);
        } else if (repeatPattern.getFrequency().equals(RepeatFrequency.EVERY_WEEK)) {
            processWeeklyFrequency(sessionList, startDate, endDate, repeatPattern.getRepeatFor(), courtScheduleList, requester);
        }

        saveCourtSchedules(courtScheduleList);
    }

    public List<CourtSchedule> getCourtSchedules(CourtScheduleRequestParam courtScheduleRequestParam, Requester requester) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtScheduleRequestParam);
        courtSchedules.forEach(courtSchedule -> courtSchedule.setBusinessDescription(enrichBusinessDescription(courtSchedule.getBusinessType(), requester)));
        return courtSchedules;
    }

    public Result update(UpdateCourtSchedule updateCourtSchedule, Requester requester) {
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = courtScheduleRepository.findBy(updateCourtSchedule.getCourtScheduleId());
        if (Objects.isNull(persistedCourtSchedule)) {
            return new Result("Court Schedule not found", false);
        }
        final String persistedBusinessType = persistedCourtSchedule.getBusinessType();
        if (isBusinessTypeChangeInvalid(updateCourtSchedule, requester, persistedBusinessType)) {
            return new Result("Business Type cannot be changed from Slot to Non-Slot and vice versa", false);
        }
        updateAvailability(updateCourtSchedule, persistedCourtSchedule);

        String courtRoomId = updateCourtSchedule.getCourtRoomId();

        final Optional<CourtRoom> courtRoom;
        if (nonNull(courtRoomId) && !courtRoomId.equalsIgnoreCase(persistedCourtSchedule.getCourtRoomId())) {
            courtRoom = Optional.of(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId, requester).orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + courtRoomId)));
        } else {
            courtRoom = Optional.empty();
        }


        return courtScheduleRepository.update(persistedCourtSchedule, updateCourtSchedule, courtRoom);
    }

    private void updateAvailability(final UpdateCourtSchedule updateCourtSchedule, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        final Integer totalListedDuration = allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(updateCourtSchedule.getCourtScheduleId());
        //Assuming that businessType won't be changing from slot to non-slot or vice versa
        if (persistedCourtSchedule.isSlotBased()) {
            updateCourtSchedule.setAvailableSlots(updateCourtSchedule.getMaxSlots() - (nonNull(totalListedDuration) ? totalListedDuration : 0));
            updateCourtSchedule.setMaxDuration(0);
            updateCourtSchedule.setAvailableDuration(0);
        } else {
            updateCourtSchedule.setAvailableDuration(updateCourtSchedule.getMaxDuration() - (nonNull(totalListedDuration) ? totalListedDuration : 0));
            updateCourtSchedule.setMaxSlots(0);
            updateCourtSchedule.setAvailableSlots(0);

        }
    }

    private boolean isBusinessTypeChangeInvalid(final UpdateCourtSchedule updateCourtSchedule, final Requester requester, final String persistedBusinessType) {
        return !persistedBusinessType.equals(updateCourtSchedule.getBusinessType()) && !isBusinessTypeChangeAllowed(updateCourtSchedule, requester, persistedBusinessType);
    }

    private boolean isBusinessTypeChangeAllowed(final UpdateCourtSchedule updateCourtSchedule, final Requester requester, final String persistedBusinessTypeCode) {
        final BusinessType persistedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(persistedBusinessTypeCode, requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + persistedBusinessTypeCode));
        final BusinessType updatedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(updateCourtSchedule.getBusinessType(), requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + updateCourtSchedule.getBusinessType()));
        return persistedBusinessType.isSlot() == updatedBusinessType.isSlot() && isUpdateRequestParamsAreValidForUpdate(updateCourtSchedule, updatedBusinessType.isSlot());
    }

    private static boolean isUpdateRequestParamsAreValidForUpdate(final UpdateCourtSchedule updateCourtSchedule, final boolean isSlotBased) {
        return (isSlotBased && updateCourtSchedule.getMaxDuration().equals(0)) || (!isSlotBased && updateCourtSchedule.getMaxSlots().equals(0));
    }

    public JsonObject deleteCourtScheduleSessions(final SessionsParam sessionsParam, Requester requester) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.deleteCourtSchedule(sessionsParam.getSessions());
        courtSchedules.forEach(courtSchedule -> courtSchedule.setBusinessDescription(enrichBusinessDescription(courtSchedule.getBusinessType(), requester)));
        List<CourtScheduleDeleteResponse> courtScheduleDeleteResponses = courtScheduleToDeleteResponseConverter.convert(courtSchedules);
        final ListToJsonArrayConverter<CourtScheduleDeleteResponse> listToJsonArrayConverter = new ListToJsonArrayConverter<>();
        JsonArray jsonArray = courtSchedules.isEmpty() ? JsonValue.EMPTY_JSON_ARRAY : listToJsonArrayConverter.convert(courtScheduleDeleteResponses);
        return Json.createObjectBuilder()
                .add(RequestParameterConstant.SESSIONS.getLabel(), jsonArray)
                .build();
    }

    public boolean isMigrated(final String ouCode) {
        return courtMigrationRepository.findByOuCode(ouCode).isMigrated();
    }

    public boolean isMigratedByCourtCentreId(final String courtCentreId) {
        return courtMigrationRepository.findByCourtCentreId(courtCentreId).isMigrated();
    }

    public Map<String, Boolean> migratedMapByOuCode() {
        return courtMigrationRepository.findAll().stream()
                .collect(Collectors.toMap(CourtSchedulerMigrationStatus::getOuCode, CourtSchedulerMigrationStatus::isMigrated));
    }

    public Result migrateOuCodes(OuCodeMigrateRequest ouCodeMigrateRequest) {
        List<String> ouCodes = ouCodeMigrateRequest.getOuCodes();
        boolean migrated = ouCodeMigrateRequest.isMigrated();
        List<CourtSchedulerMigrationStatus> courtSchedulerMigrationStatusList = new ArrayList<>();
        final AtomicBoolean isOuCodeNotPresent = new AtomicBoolean(false);

        ouCodes.forEach(ouCode -> {
            CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = courtMigrationRepository.findByOuCode(ouCode);
            if (isNull(courtSchedulerMigrationStatus)) {
                isOuCodeNotPresent.set(true);
            }
            courtSchedulerMigrationStatusList.add(courtSchedulerMigrationStatus);
        });

        if (isOuCodeNotPresent.get()) {
            return new Result("One of the OuCode not present for migrate", false);
        }

        courtSchedulerMigrationStatusList.forEach(courtSchedulerMigrationStatus -> {
            courtSchedulerMigrationStatus.setMigrated(migrated);
            courtMigrationRepository.save(courtSchedulerMigrationStatus);
        });

        return Result.SUCCESS();
    }

    public String findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(final String courtRoomId,
                                                                                final LocalDate sessionDate,
                                                                                final String businessType,
                                                                                final String courtSession) {
        return courtScheduleRepository.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(courtRoomId, sessionDate, businessType, courtSession);
    }

    public List<CourtSchedule> getExtractedCourtSchedules(final List<String> ouCodes, final LocalDate startDate, final LocalDate endDate) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = courtScheduleRepository.getExtractedCourtSchedules(ouCodes, startDate, endDate);
        return courtScheduleEntities.stream()
                .map(CourtScheduleMapper::toDomain)
                .toList();
    }

    public List<CourtSchedule> getExtractedCourtSchedulesForGhostRota(final List<String> ouCodes, final LocalDate startDate, final LocalDate endDate) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = courtScheduleRepository.getExtractedCourtSchedulesForGhostRota(ouCodes, startDate, endDate);
        return courtScheduleEntities.stream()
                .map(CourtScheduleMapper::toDomain)
                .toList();

    }

    public void saveCourtSchedules(final List<CourtSchedule> provisionalCourtSchedules, final Map<String, BusinessType> businessTypeMap) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> provisionalCourtScheduleEntities = provisionalCourtSchedules
                .stream()
                .map(CourtScheduleMapper::toEntity)
                .toList();

        provisionalCourtScheduleEntities.forEach(provisionalCourtScheduleEntity -> {
            provisionalCourtScheduleEntity.setUpdatedOn(Calendar.getInstance().getTime());
            provisionalCourtScheduleEntity.setSlotBased(businessTypeMap.get(provisionalCourtScheduleEntity.getBusinessType()).isSlot());
            courtScheduleRepository.save(provisionalCourtScheduleEntity);
        });

    }


    @SuppressWarnings("squid:S107")
    @Transactional
    public void updateSlotsAndSchedules(final List<String> existingNonMigratedSlotIds,
                                        final Map<String, CourtSchedule> newRecords,
                                        final Map<String, CourtSchedule> slotsForMigrated,
                                        final Collection<CourtScheduleJudiciary> newSchedules,
                                        final Collection<CourtScheduleJudiciary> schedulesForMigratedExistingSlots,
                                        final Collection<CourtSchedule> slotsToUpdate,
                                        final Map<String, Pair<String, String>> schedulesToUpdateMap,
                                        final Collection<CourtScheduleJudiciary> updatedSchedules,
                                        final Map<String, List<CourtScheduleJudiciary>> relatedJudiciarySchedules,
                                        final List<String> slotIdsToDelete,
                                        final Map<String, BusinessType> businessTypeMap,
                                        final LocalDate startDate,
                                        final LocalDate endDate,
                                        final List<String> ouCodes) {
        logger.info("DD-15703:CourtScheduleRepository: update process started");

        logger.info("DD-15703:CourtScheduleRepository: before deactivateSlots");
        deactivateSlots(existingNonMigratedSlotIds);
        logger.info("DD-15703:CourtScheduleRepository: after deactivateSlots");

        logger.info("DD-15703:CourtScheduleRepository: before deactivateSchedules");
        deactivateSchedules(existingNonMigratedSlotIds);
        logger.info("DD-15703:CourtScheduleRepository: after deactivateSchedules.update");


        logger.info("DD-15703:CourtScheduleRepository: before saveSlots");
        final int numberOfSavedSlots = saveSlots(newRecords.values(), businessTypeMap);
        logger.info("DD-15703:CourtScheduleRepository: after saveSlots with numberOfSavedSlots: {}", numberOfSavedSlots);

        for (final CourtSchedule courtSchedule : slotsToUpdate) {
            newRecords.putIfAbsent(courtSchedule.getListingProfileId(), courtSchedule);
        }

        logger.info("DD-15703:CourtScheduleRepository: before saveJudiciarySchedule");
        int numberOfSavedJudiciarySchedules = saveJudiciarySchedule(newRecords, newSchedules, false, startDate, endDate, ouCodes);
        logger.info("DD-15703:CourtScheduleRepository: after saveJudiciarySchedule with numberOfSavedJudiciarySchedules: {}", numberOfSavedJudiciarySchedules);

        logger.info("DD-15703:CourtScheduleRepository: before saveJudiciarySchedule for existing migrated slots");
        int numberOfSavedJudiciarySchedulesForMigratedExistingSlots = saveJudiciarySchedule(slotsForMigrated, schedulesForMigratedExistingSlots, true, startDate, endDate, ouCodes);
        logger.info("DD-15703:CourtScheduleRepository: after saveJudiciarySchedule with numberOfSavedJudiciarySchedulesForMigratedExistingSlots: {}", numberOfSavedJudiciarySchedulesForMigratedExistingSlots);

        logger.info("DD-15703:CourtScheduleRepository: before updateSlots");
        updateSlots(slotsToUpdate, businessTypeMap);
        logger.info("DD-15703:CourtScheduleRepository: after updateSlots");

        logger.info("DD-15703:CourtScheduleRepository: before updateJudiciarySchedule");
        updateJudiciarySchedule(schedulesToUpdateMap, updatedSchedules, relatedJudiciarySchedules);
        logger.info("DD-15703:CourtScheduleRepository: after updateJudiciarySchedule");

        if (isNotEmpty(slotIdsToDelete)) {
            logger.info("DD-15703:CourtScheduleRepository: before deleteSlots");
            final int numberOfDeletedSlots = deleteSlots(slotIdsToDelete);
            logger.info("DD-15703:CourtScheduleRepository: after deleteSlots with numberOfDeletedSlots : {}", numberOfDeletedSlots);

            logger.info("DD-15703:CourtScheduleRepository: before deleteSchedules");
            final int numberOfDeletedSchedules = deleteSchedules(slotIdsToDelete);
            logger.info("DD-15703:CourtScheduleRepository: after deleteSchedules with numberOfDeletedSchedules : {}", numberOfDeletedSchedules);
        }

        logger.info("DD-15703:CourtScheduleRepository: update process completed");
    }

    private void deactivateSlots(final List<String> snapshotSlotIds) {
        if (isNotEmpty(snapshotSlotIds)) {
            courtScheduleRepository.deactivateSlots(snapshotSlotIds, Calendar.getInstance().getTime());
        }
    }

    private void deactivateSchedules(final List<String> snapshotSlotIds) {
        if (isNotEmpty(snapshotSlotIds)) {
            courtScheduleJudiciaryRepository.deactivateSchedules(snapshotSlotIds, Calendar.getInstance().getTime());
        }
    }

    private int saveSlots(final Collection<CourtSchedule> slots,
                          final Map<String, BusinessType> businessTypeMap) {
        final AtomicInteger numberOfSaved = new AtomicInteger();
        slots.forEach(slot -> {
            final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity = CourtScheduleMapper.toEntity(slot);
            if (nonNull(courtScheduleEntity)) {
                courtScheduleEntity.setUpdatedOn(Calendar.getInstance().getTime());
                courtScheduleEntity.setSlotBased(businessTypeMap.get(slot.getBusinessType()).isSlot());
                courtScheduleRepository.save(courtScheduleEntity);

                numberOfSaved.getAndIncrement();
            }
        });

        return numberOfSaved.get();
    }

    private void updateSlots(final Collection<CourtSchedule> slotsToUpdate, final Map<String, BusinessType> businessTypeMap) {
        slotsToUpdate.forEach(slotToUpdate -> {
            final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule slotToUpdateEntity = CourtScheduleMapper.toEntity(slotToUpdate);
            slotToUpdateEntity.setUpdatedOn(Calendar.getInstance().getTime());
            slotToUpdateEntity.setActive(true);
            slotToUpdate.setSlotBased(businessTypeMap.get(slotToUpdate.getBusinessType()).isSlot());

            courtScheduleRepository.update(slotToUpdateEntity);
        });
    }

    private void updateJudiciarySchedule(final Map<String, Pair<String, String>> schedulesToUpdateMap,
                                         final Collection<CourtScheduleJudiciary> scheduleJudiciaries,
                                         final Map<String, List<CourtScheduleJudiciary>> courtScheduleJudiciariesMap) {
        if (!courtScheduleJudiciariesMap.isEmpty() && !schedulesToUpdateMap.isEmpty()) {
            for (final Map.Entry<String, List<CourtScheduleJudiciary>> slotsScheduleEntry : courtScheduleJudiciariesMap.entrySet()) {
                final String profileId = slotsScheduleEntry.getKey();
                final List<CourtScheduleJudiciary> slotsScheduleEntryValue = slotsScheduleEntry.getValue();

                slotsScheduleEntryValue.forEach(courtScheduleJudiciary -> {
                    final Pair<String, String> courtScheduleIdAndOuCodePair = schedulesToUpdateMap.get(profileId);
                    final String courtScheduleId = courtScheduleIdAndOuCodePair.getLeft();
                    if (nonNull(courtScheduleId)) {
                        scheduleJudiciaries.stream()
                                .filter(scheduleJudiciary -> scheduleJudiciary.getCourtScheduleId().equals(courtScheduleJudiciary.getCourtScheduleId())
                                        && scheduleJudiciary.getJudiciaryId().equals(courtScheduleJudiciary.getJudiciaryId()))
                                .map(CourtScheduleJudiciary::getPosition)
                                .findFirst()
                                .ifPresent(updatedPosition ->
                                        courtScheduleJudiciaryRepository.updateCourtScheduleJudiciaryPosition(updatedPosition, Calendar.getInstance().getTime(), courtScheduleJudiciary.getCourtScheduleId(), courtScheduleJudiciary.getJudiciaryId())
                                );
                    }
                });
            }
        }
    }

    private int deleteSchedules(final List<String> ids) {
        return courtScheduleJudiciaryRepository.deleteSchedules(ids);
    }

    private int deleteSlots(final List<String> courtScheduleIds) {
        return courtScheduleRepository.deleteSlots(courtScheduleIds);
    }

    private int saveJudiciarySchedule(final Map<String, CourtSchedule> newRecords,
                                      final Collection<CourtScheduleJudiciary> scheduleJudiciaries,
                                      final boolean forMigrated,
                                      final LocalDate startDate,
                                      final LocalDate endDate,
                                      final List<String> ouCodes) {
        final List<String> judiciaryIds = scheduleJudiciaries.stream().map(CourtScheduleJudiciary::getJudiciaryId).toList();
        final List<String> listingProfileIds = scheduleJudiciaries.stream().map(CourtScheduleJudiciary::getCourtListingProfileId).toList();

        final AtomicInteger numberOfSaved = new AtomicInteger();
        scheduleJudiciaries.forEach(scheduleJudiciary -> {
            final CourtSchedule courtSchedule = newRecords.get(scheduleJudiciary.getCourtListingProfileId());

            if (nonNull(courtSchedule)) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary courtScheduleJudiciaryEntity = CourtScheduleJudiciaryMapper.toEntity(scheduleJudiciary);
                courtScheduleJudiciaryEntity.setUpdatedOn(Calendar.getInstance().getTime());
                if (!forMigrated) {
                    courtScheduleJudiciaryEntity.getId().setCourtScheduleId(courtSchedule.getCourtScheduleId());
                    final String judiciaryId = courtScheduleJudiciaryEntity.getId().getJudiciaryId();
                    final String listingProfileId = courtScheduleJudiciaryEntity.getCourtListingProfileId();
                    if (judiciaryIds.contains(judiciaryId) && listingProfileIds.contains(listingProfileId)) {
                        final int numberOfDeletedScheduleJudiciariesNotInCourtSchedules = courtScheduleJudiciaryRepository.deleteCourtScheduleJudiciariesEntriesNotInCourtSchedules(startDate, endDate, ouCodes, listingProfileId, judiciaryId);
                        logger.info("numberOfDeletedScheduleJudiciariesNotInCourtSchedules: {} for judiciaryId: {} - listingProfileId: {}", numberOfDeletedScheduleJudiciariesNotInCourtSchedules, judiciaryId, listingProfileId);
                    }
                }
                courtScheduleJudiciaryRepository.save(courtScheduleJudiciaryEntity);

                numberOfSaved.getAndIncrement();
            }
        });

        return numberOfSaved.get();
    }


    private String enrichBusinessDescription(final String businessType, final Requester requester) {
        return referenceDataCache.getRotaBusinessTypeByCode(businessType, requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + businessType)).getTypeDescription();
    }

    private void processOnceFrequency(List<Session> sessionList, LocalDate startDate, List<CourtSchedule> courtScheduleList, Requester requester) {
        for (Session session : sessionList) {
            for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
                LocalDate sessionDateCandidate = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
                CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate, requester);
                courtScheduleList.add(courtSchedule);
            }
        }
    }

    private void processWeeklyFrequency(List<Session> sessionList, LocalDate startDate, LocalDate endDate, int repeatFor, List<CourtSchedule> courtScheduleList, Requester requester) {
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        for (long weekNumber = 0; weekNumber <= weeksBetween; weekNumber += repeatFor) {
            for (Session session : sessionList) {
                for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
                    LocalDate sessionDateCandidate = startDate.plusWeeks(weekNumber).with(TemporalAdjusters.nextOrSame(dayOfWeek));
                    if (sessionDateCandidate.isAfter(endDate)) {
                        continue;
                    }
                    CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate, requester);
                    courtScheduleList.add(courtSchedule);
                }
            }
        }
    }

    private CourtSchedule buildCourtSchedule(Session session, LocalDate sessionDateCandidate, Requester requester) {
        final CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = new CourtSchedule.CourtScheduleBuilder();
        courtScheduleBuilder.withCourtScheduleId(UUID.randomUUID().toString())
                .withBusinessType(session.getBusinessType())
                .withCourtHouseId(session.getCourtCentreId())
                .withCourtRoomId(session.getCourtRoomId())
                .withActive(true)
                .withSessionDate(sessionDateCandidate)
                .withCourtSession(session.getSessionType())
                .withPanel(session.getPanelType());
        enrichSession(courtScheduleBuilder, session.getSlotsOrDuration(), requester);
        return courtScheduleBuilder.build();
    }

    private void saveCourtSchedules(List<CourtSchedule> courtScheduleList) {
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = courtScheduleList.stream()
                .map(CourtScheduleMapper::toEntity)
                .toList();
        courtScheduleEntities.forEach(courtSchedule -> {
            try {
                courtScheduleRepository.save(courtSchedule);
            } catch (QueryInvocationException queryInvocationException) {
                courtScheduleRepository.update(courtSchedule);
            }
        });
    }

    private void enrichSession(CourtSchedule.CourtScheduleBuilder builder, int maxSlotsorDuration, Requester requester) {
        final BusinessType businessType = referenceDataCache.getRotaBusinessTypeByCode(builder.getBusinessType(), requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + builder.getBusinessType()));
        final CourtRoom courtRoom = referenceDataCache.getRotaCourtRoomByCourtRoomId(builder.getCourtRoomId(), requester).orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + builder.getCourtRoomId()));
        if (businessType.isSlot()) {
            builder.withSlotBased(true);
            builder.withMaxSlots(maxSlotsorDuration);
            builder.withAvailableSlots(maxSlotsorDuration);
            builder.withMaxDuration(0);
            builder.withAvailableDuration(0);
        } else {
            builder.withSlotBased(false);
            builder.withMaxDuration(maxSlotsorDuration);
            builder.withAvailableDuration(maxSlotsorDuration);
            builder.withMaxSlots(0);
            builder.withAvailableSlots(0);
        }

        if (nonNull(courtRoom)) {
            builder.withOuCode(courtRoom.getOucode());
            builder.withCourtRoomName(courtRoom.getCourtroomName());
            builder.withCourtRoomNumber(courtRoom.getCppCourtRoomId());
            builder.withCourtHouseName(courtRoom.getOucodeL3Name());
            builder.withOperationalUnit(courtRoom.getOucodeL2Code());
        }
    }

    public JsonObject validateSessionIntegrity(final Session session, final LocalDate startDate, final LocalDate endDate) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> sessionsToCompare = courtScheduleRepository.getSimilarSessions(session.getCourtCentreId(), session.getCourtRoomId(), session.getBusinessType(), startDate, endDate);
        // session.repeatDays is a set, if it includes dayofweekvalue of sessionsToCompare
        for (uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule sessionToCompare : sessionsToCompare) {
            //if either of the new session or DB session is AD, we can't add AM,PM or, AD session for the same date
            if (sameSessionViolatesAllDayRestriction(session, sessionToCompare)) {
                return getValidationResult(format("Session Integrity failure. The session you're trying to add is not compatible with a record, courtscheduleId : %s  in terms of AM/PM/AD session for the same date", sessionToCompare.getCourtScheduleId()));
            }
        }
        return JsonValue.EMPTY_JSON_OBJECT;
    }

    private static boolean sameSessionViolatesAllDayRestriction(final Session session, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule sessionToCompare) {
        boolean violated = false;
        if(session.getCourtCentreId().equals(sessionToCompare.getCourtHouseId()) &&
                session.getCourtRoomId().equals(sessionToCompare.getCourtRoomId()) &&
                session.getBusinessType().equals(sessionToCompare.getBusinessType()) &&
                session.getRepeatDays().contains(DayOfWeek.of(sessionToCompare.getSessionDate().getDayOfWeek().getValue())))
        {
            violated = session.getSessionType().equals(sessionToCompare.getCourtSession()) || session.getSessionType().equals("AD") || sessionToCompare.getCourtSession().equals("AD");

        }
        return violated;
    }
}