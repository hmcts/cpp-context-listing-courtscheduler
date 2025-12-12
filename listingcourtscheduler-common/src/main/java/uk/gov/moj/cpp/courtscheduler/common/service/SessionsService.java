package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.BUSINESS_TYPE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.COURTROOM_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME;
import static uk.gov.moj.cpp.courtscheduler.common.utils.ProcessingDataInfoMessages.SLOT_WILL_NOT_BE_SAVED_HAVING_ADULT_PANEL;
import static uk.gov.moj.cpp.courtscheduler.common.utils.ProcessingDataInfoMessages.SLOT_WILL_NOT_BE_SAVED_HAVING_AD_SESSION;
import static uk.gov.moj.cpp.courtscheduler.common.utils.ProcessingDataInfoMessages.SLOT_WILL_NOT_BE_SAVED_HAVING_AM_OR_PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.common.utils.ProcessingDataInfoMessages.SLOT_WILL_NOT_BE_SAVED_HAVING_YOUTH_PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.PanelTypes.ADULT;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.PanelTypes.YOUTH;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.getOrElseDefaultSessionStartAndEndTimeIfEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.sessionTimeFormatter;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toLocalTime;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.converter.CourtScheduleToDeleteResponseConverter;
import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleMapper;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleDeleteResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeMigrateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeRecalculateAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SearchCourtSchedulesByIdRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.SlotAndScheduleInfo;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtMigrationRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SessionsService {

    private static final Logger logger = LoggerFactory.getLogger(SessionsService.class);

    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private CourtMigrationRepository courtMigrationRepository;
    @Inject
    private ReferenceDataCache referenceDataCache;
    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;
    @Inject
    private CourtScheduleService courtScheduleService;

    @Transactional
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
        } else if (repeatPattern.getFrequency().equals(RepeatFrequency.EVERY_MONTH)) {
            processMonthlyFrequency(sessionList, startDate, endDate, repeatPattern.getRepeatFor(), courtScheduleList, requester);
        }

        saveCourtSchedules(courtScheduleList);
    }

    public List<CourtSchedule> getCourtSchedules(CourtScheduleRequestParam courtScheduleRequestParam, Requester requester) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam);
        courtSchedules.forEach(courtSchedule -> courtSchedule.setBusinessDescription(enrichBusinessDescription(courtSchedule.getBusinessType(), requester)));
        return courtSchedules;
    }

    public Result update(final UpdateCourtSchedule updateCourtSchedule, Requester requester) {
        final String courtScheduleId = updateCourtSchedule.getCourtScheduleId();
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId);
        if (isNull(persistedCourtSchedule)) {
            return new Result(ErrorMessages.SESSION_NOT_FOUND, false);
        }

        final String persistedBusinessType = persistedCourtSchedule.getBusinessType();
        if (isBusinessTypeChangeInvalid(updateCourtSchedule, requester, persistedBusinessType)) {
            return new Result(ErrorMessages.BUSINESS_TYPE_CHANGE_NOT_ALLOWED, false);
        }

        final List<AllocatedListingEachBooked> allocatedListingEachBooked = allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId));
        Optional<Date> earliestHearingStartTime = allocatedListingEachBooked.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .min(Comparator.naturalOrder());

        Optional<Date> latestHearingStartTime = allocatedListingEachBooked.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .max(Comparator.naturalOrder());

        if (StringUtils.isNotEmpty(updateCourtSchedule.getSessionStartTime()) && StringUtils.isNotEmpty(updateCourtSchedule.getSessionEndTime())) {
            final Date sessionStartTimeWithDate = DateUtils.combineDateAndTime(persistedCourtSchedule.getSessionDate(), updateCourtSchedule.getSessionStartTime());
            final Date sessionEndTimeWithDate = DateUtils.combineDateAndTime(persistedCourtSchedule.getSessionDate(), updateCourtSchedule.getSessionEndTime());

            if (earliestHearingStartTime.isPresent() && sessionStartTimeWithDate.after(earliestHearingStartTime.get())) {
                return new Result(SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME, false);
            }

            if (latestHearingStartTime.isPresent() && sessionEndTimeWithDate.before(latestHearingStartTime.get())) {
                return new Result(SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME, false);
            }
        }

        boolean isChanged = checkEditValuesModified(updateCourtSchedule, persistedCourtSchedule);

        if (isChanged) {
            return new Result(ErrorMessages.SESSION_EDIT_ANOTHER_USER, false);
        }

        if (nonNull(persistedCourtSchedule.getSupportAdSplit()) && TRUE.equals(persistedCourtSchedule.getSupportAdSplit() != updateCourtSchedule.isAllDaySplit())) {
            return new Result(ErrorMessages.ALL_DAY_SPLIT_CHANGE_NOT_ALLOWED, false);
        }

        if (StringUtils.isNotEmpty(updateCourtSchedule.getSessionStartTime()) && StringUtils.isNotEmpty(updateCourtSchedule.getSessionEndTime())) {
            final LocalTime sessionStartTime = toLocalTime(updateCourtSchedule.getSessionStartTime());
            final LocalTime sessionEndTime = toLocalTime(updateCourtSchedule.getSessionEndTime());

            if (sessionStartTime.isAfter(sessionEndTime)) {
                return new Result(ErrorMessages.SESSION_START_TIME_CANNOT_BE_LATER_THAN_END_TIME, false);
            }
            if (AM_SESSION.equals(updateCourtSchedule.getSessionType()) && sessionEndTime.isAfter(LocalTime.of(13, 0))) {
                return new Result(ErrorMessages.AM_SESSION_END_TIME_CANNOT_EXCEED, false);
            } else if ((AM_SESSION.equals(updateCourtSchedule.getSessionType()) || ALL_DAY.equals(updateCourtSchedule.getSessionType())) &&
                    sessionStartTime.isBefore(LocalTime.of(1, 0))) {
                return new Result(format(ErrorMessages.SESSION_START_TIME_CANNOT_BE_EARLIER, updateCourtSchedule.getSessionType()), false);
            } else if (PM_SESSION.equals(updateCourtSchedule.getSessionType()) && sessionStartTime.isBefore(LocalTime.of(14, 0))) {
                return new Result(ErrorMessages.PM_SESSION_START_TIME_CANNOT_BE_EARLIER, false);
            } else if ((PM_SESSION.equals(updateCourtSchedule.getSessionType()) || ALL_DAY.equals(updateCourtSchedule.getSessionType())) &&
                    sessionEndTime.isAfter(LocalTime.of(23, 59))) {
                return new Result(format(ErrorMessages.SESSION_END_TIME_CANNOT_BE_LATER, updateCourtSchedule.getSessionType()), false);
            }
        }

        updateAvailability(updateCourtSchedule, persistedCourtSchedule);

        String courtRoomId = updateCourtSchedule.getCourtRoomId();

        final Optional<CourtRoom> courtRoom;
        if (nonNull(courtRoomId) && !courtRoomId.equalsIgnoreCase(persistedCourtSchedule.getCourtRoomId())) {
            if ("CROWN".equalsIgnoreCase(updateCourtSchedule.getJurisdiction())) {
                courtRoom = Optional.of(referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId, requester)
                        .orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + courtRoomId)));
            } else {
                courtRoom = Optional.of(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId, requester)
                        .orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + courtRoomId)));
            }
        } else {
            courtRoom = Optional.empty();
        }

        Result result;

        try {
            result = courtScheduleRepository.update(persistedCourtSchedule, updateCourtSchedule, courtRoom);
        } catch (Exception exception) {
            logger.error("update court schedule failing courScheduleId : {}", persistedCourtSchedule.getCourtScheduleId());
            result = new Result(ErrorMessages.DUPLICATE_SESSIONS, false);
        }

        return result;
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

            if (nonNull(persistedCourtSchedule.getSupportAdSplit()) && TRUE.equals(persistedCourtSchedule.getSupportAdSplit())) {
                updateCourtSchedule.setMaxDurationForMorning(updateCourtSchedule.getMaxDurationForMorning());
                updateCourtSchedule.setMaxDurationForAfternoon(updateCourtSchedule.getMaxDurationForAfternoon());
            }
        }
    }

    private boolean checkEditValuesModified(final UpdateCourtSchedule updateCourtSchedule,
                                            final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        if (!TRUE.equals(persistedCourtSchedule.getHasHearingsBooked())) {
            return false;
        }
        return !StringUtils.equals(updateCourtSchedule.getCourtRoomId(), persistedCourtSchedule.getCourtRoomId()) ||
                !StringUtils.equals(updateCourtSchedule.getSessionType(), persistedCourtSchedule.getCourtSession()) ||
                !StringUtils.equals(updateCourtSchedule.getPanel(), persistedCourtSchedule.getPanel());
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
        final List<String> sessions = isEmpty(sessionsParam.getSessions())  ? new ArrayList<>() : sessionsParam.getSessions();
        final List<CourtSchedule> allocatedCourtSchedules = courtScheduleRepository.deleteCourtSchedule(sessions);
        allocatedCourtSchedules.forEach(courtSchedule -> courtSchedule.setBusinessDescription(enrichBusinessDescription(courtSchedule.getBusinessType(), requester)));
        List<AllocatedListingEachBooked> allocatedListingEachBooked = new ArrayList<>();
        if(!isEmpty(allocatedCourtSchedules)) {
            allocatedListingEachBooked = allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(allocatedCourtSchedules.stream()
                            .map(CourtSchedule::getCourtScheduleId)
                            .toList())
                    .stream()
                    .toList();
        }
        final List<CourtScheduleDeleteResponse> courtScheduleDeleteResponses = CourtScheduleToDeleteResponseConverter.convert(allocatedCourtSchedules, allocatedListingEachBooked);
        final ListToJsonArrayConverter<CourtScheduleDeleteResponse> listToJsonArrayConverter = new ListToJsonArrayConverter<>();
        final JsonArray jsonArray = allocatedCourtSchedules.isEmpty() ? JsonValue.EMPTY_JSON_ARRAY : listToJsonArrayConverter.convert(courtScheduleDeleteResponses);
        if (jsonArray == JsonValue.EMPTY_JSON_ARRAY) {
            return Json.createObjectBuilder()
                    .add(RequestParameterConstant.SESSIONS.getLabel(), jsonArray)
                    .build();
        } else {
            return Json.createObjectBuilder()
                    .add("error", "Some sessions could not be removed. Please check again.")
                    .add(RequestParameterConstant.SESSIONS.getLabel(), jsonArray)
                    .build();
        }
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

    @Transactional
    public Result ouCodesRecalculateAvailability(OuCodeRecalculateAvailabilityRequest request) {

        final int rowsAffected = courtScheduleRepository.getInconsistentCourtSchedulersByOucode(request.getOuCode());
        return new Result(format("Recalculate availability for %s ouCode(s) affected %d rows", request.getOuCode(), rowsAffected), true);
    }

    public CourtScheduleMatcherInfo findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(final String courtRoomId,
                                                                                                  final LocalDate sessionDate,
                                                                                                  final String businessType,
                                                                                                  final String courtSession) {
        return courtScheduleRepository.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(courtRoomId, sessionDate, businessType, courtSession);
    }

    public List<CourtSchedule> getExtractedCourtSchedules(final List<String> ouCodes, final LocalDate startDate, final LocalDate endDate) {
        if (isNotEmpty(ouCodes)) {
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = courtScheduleRepository.getExtractedCourtSchedules(ouCodes, startDate, endDate);
            return courtScheduleEntities.stream()
                    .map(CourtScheduleMapper::toDomain)
                    .toList();
        }
        return emptyList();
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
    public void updateSlotsAndSchedules(final SlotAndScheduleInfo slotAndScheduleInfo,
                                        final Map<String, CourtSchedule> slotsForMigrated,
                                        final Collection<CourtScheduleJudiciary> updatedSchedules,
                                        final Map<String, BusinessType> businessTypeMap,
                                        final List<String> ouCodes,
                                        final List<CourtSchedule> existingCourtSchedules) {
        logger.info("DD-15703:CourtScheduleRepository: update process started");

        logger.info("DD-15703:CourtScheduleRepository: before saveSlots");
        final List<String> courtScheduleIdsOfSavedSlots = saveSlots(slotAndScheduleInfo.newSlots().values(), businessTypeMap, existingCourtSchedules);
        logger.info("DD-15703:CourtScheduleRepository: after saveSlots with numberOfSavedSlots: {}", courtScheduleIdsOfSavedSlots.size());

        for (final CourtSchedule courtSchedule : slotAndScheduleInfo.slotsToUpdate()) {
            if (!slotAndScheduleInfo.newSlots().containsKey(courtSchedule.getListingProfileId())) {
                slotAndScheduleInfo.newSlots().put(courtSchedule.getListingProfileId(), courtSchedule);
                courtScheduleIdsOfSavedSlots.add(courtSchedule.getCourtScheduleId());
            }
        }

        logger.info("DD-15703:CourtScheduleRepository: before saveJudiciarySchedule");
        final int numberOfSavedJudiciarySchedules = saveJudiciarySchedule(slotAndScheduleInfo.newSlots(), slotAndScheduleInfo.newCourtScheduleJudiciaries(), courtScheduleIdsOfSavedSlots, false, ouCodes);
        logger.info("DD-15703:CourtScheduleRepository: after saveJudiciarySchedule with numberOfSavedJudiciarySchedules: {}", numberOfSavedJudiciarySchedules);

        logger.info("DD-15703:CourtScheduleRepository: before saveJudiciarySchedule for existing migrated slots");
        final int numberOfSavedJudiciarySchedulesForMigratedExistingSlots = saveJudiciarySchedule(slotsForMigrated, slotAndScheduleInfo.courtScheduleJudiciariesForMigratedExistingSlots(), emptyList(), true, ouCodes);
        logger.info("DD-15703:CourtScheduleRepository: after saveJudiciarySchedule with numberOfSavedJudiciarySchedulesForMigratedExistingSlots: {}", numberOfSavedJudiciarySchedulesForMigratedExistingSlots);

        logger.info("DD-15703:CourtScheduleRepository: before updateSlots");
        final int numberOfUpdatedSlots = updateSlots(slotAndScheduleInfo.slotsToUpdate(), businessTypeMap);
        logger.info("DD-15703:CourtScheduleRepository: after updateSlots with numberOfUpdatedSlots: {}", numberOfUpdatedSlots);

        logger.info("DD-15703:CourtScheduleRepository: before updateJudiciarySchedule");
        updateJudiciarySchedule(slotAndScheduleInfo.schedulesToUpdateMap(), updatedSchedules, slotAndScheduleInfo.relatedJudiciarySchedules());
        logger.info("DD-15703:CourtScheduleRepository: after updateJudiciarySchedule");

        if (isNotEmpty(slotAndScheduleInfo.confirmedSlotIdsToDelete())) {
            logger.info("DD-15703:CourtScheduleRepository: before deleteSlots");
            final int numberOfDeletedSlots = deleteSlots(slotAndScheduleInfo.confirmedSlotIdsToDelete());
            logger.info("DD-15703:CourtScheduleRepository: after deleteSlots with numberOfDeletedSlots : {}", numberOfDeletedSlots);

            logger.info("DD-15703:CourtScheduleRepository: before deleteSchedules");
            final int numberOfDeletedSchedules = deleteSchedules(slotAndScheduleInfo.confirmedSlotIdsToDelete());
            logger.info("DD-15703:CourtScheduleRepository: after deleteSchedules with numberOfDeletedSchedules : {}", numberOfDeletedSchedules);
        }

        logger.info("DD-15703:CourtScheduleRepository: update process completed");
    }

    private List<String> saveSlots(final Collection<CourtSchedule> slots,
                                   final Map<String, BusinessType> businessTypeMap,
                                   final List<CourtSchedule> existingCourtSchedules) {
        final List<String> courtScheduleIdsOfSavedSlots = new ArrayList<>();
        slots.forEach(slot -> {

            final boolean toBePersisted = decideIfToBePersisted(existingCourtSchedules, slot);
            if (toBePersisted) {
                logger.debug("slot decided to be persisted with ouCode: {}, courtRoomNumber: {}, businessType: {}, courtSession: {}, panel: {}, sessionDate: {}",
                        slot.getOuCode(), slot.getCourtRoomNumber(), slot.getBusinessType(), slot.getCourtSession(), slot.getPanel(), slot.getSessionDate());
                final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity = CourtScheduleMapper.toEntity(slot);
                if (isNull(courtScheduleEntity.getCreatedOn())) {
                    courtScheduleEntity.setCreatedOn(Calendar.getInstance().getTime());
                }
                courtScheduleEntity.setUpdatedOn(Calendar.getInstance().getTime());
                courtScheduleEntity.setSlotBased(businessTypeMap.get(slot.getBusinessType()).isSlot());
                courtScheduleIdsOfSavedSlots.add(courtScheduleEntity.getCourtScheduleId());
                courtScheduleService.saveSlot(courtScheduleEntity);
            }
        });

        return courtScheduleIdsOfSavedSlots;
    }

    private static boolean decideIfToBePersisted(final List<CourtSchedule> existingCourtSchedules, final CourtSchedule slot) {
        return decideIfToBePersistedForCourtSession(existingCourtSchedules, slot) && decideIfToBePersistedForPanel(existingCourtSchedules, slot);
    }

    private static boolean decideIfToBePersistedForPanel(final List<CourtSchedule> existingCourtSchedules, final CourtSchedule slot) {
        boolean toBePersisted;
        if (ADULT.name().equals(slot.getPanel())) {
            toBePersisted = existingCourtSchedules.stream()
                    .noneMatch(existingCourtSchedule -> existingCourtSchedule.getOuCode().equals(slot.getOuCode())
                            && existingCourtSchedule.getBusinessType().equals(slot.getBusinessType())
                            && existingCourtSchedule.getSessionDate().equals(slot.getSessionDate())
                            && existingCourtSchedule.getCourtRoomNumber().equals(slot.getCourtRoomNumber())
                            && YOUTH.name().equals(existingCourtSchedule.getPanel())
                    );

            if (!toBePersisted) {
                logger.error(SLOT_WILL_NOT_BE_SAVED_HAVING_YOUTH_PANEL, slot.getPanel(), slot.getOuCode(), slot.getBusinessType(), slot.getSessionDate(), slot.getCourtRoomNumber());
                return false;
            }
        } else if (YOUTH.name().equals(slot.getPanel())) {
            toBePersisted = existingCourtSchedules.stream()
                    .noneMatch(existingCourtSchedule -> existingCourtSchedule.getOuCode().equals(slot.getOuCode())
                            && existingCourtSchedule.getBusinessType().equals(slot.getBusinessType())
                            && existingCourtSchedule.getSessionDate().equals(slot.getSessionDate())
                            && existingCourtSchedule.getCourtRoomNumber().equals(slot.getCourtRoomNumber())
                            && ADULT.name().equals(existingCourtSchedule.getPanel())
                    );

            if (!toBePersisted) {
                logger.error(SLOT_WILL_NOT_BE_SAVED_HAVING_ADULT_PANEL, slot.getPanel(), slot.getOuCode(), slot.getBusinessType(), slot.getSessionDate(), slot.getCourtRoomNumber());
                return false;
            }
        }
        return true;
    }

    private static boolean decideIfToBePersistedForCourtSession(final List<CourtSchedule> existingCourtSchedules, final CourtSchedule slot) {
        boolean toBePersisted;
        if (ALL_DAY.equals(slot.getCourtSession())) {
            toBePersisted = existingCourtSchedules.stream()
                    .noneMatch(existingCourtSchedule -> existingCourtSchedule.getOuCode().equals(slot.getOuCode())
                            && existingCourtSchedule.getBusinessType().equals(slot.getBusinessType())
                            && existingCourtSchedule.getSessionDate().equals(slot.getSessionDate())
                            && existingCourtSchedule.getCourtRoomNumber().equals(slot.getCourtRoomNumber())
                            && (AM_SESSION.equals(existingCourtSchedule.getCourtSession()) || PM_SESSION.equals(existingCourtSchedule.getCourtSession()))
                    );

            if (!toBePersisted) {
                logger.error(SLOT_WILL_NOT_BE_SAVED_HAVING_AM_OR_PM_SESSION, slot.getCourtSession(), slot.getOuCode(), slot.getBusinessType(), slot.getSessionDate(), slot.getCourtRoomNumber());
                return false;
            }
        } else if (AM_SESSION.equals(slot.getCourtSession()) || PM_SESSION.equals(slot.getCourtSession())) {
            toBePersisted = existingCourtSchedules.stream()
                    .noneMatch(existingCourtSchedule -> existingCourtSchedule.getOuCode().equals(slot.getOuCode())
                            && existingCourtSchedule.getBusinessType().equals(slot.getBusinessType())
                            && existingCourtSchedule.getSessionDate().equals(slot.getSessionDate())
                            && existingCourtSchedule.getCourtRoomNumber().equals(slot.getCourtRoomNumber())
                            && ALL_DAY.equals(existingCourtSchedule.getCourtSession())
                    );

            if (!toBePersisted) {
                logger.error(SLOT_WILL_NOT_BE_SAVED_HAVING_AD_SESSION, slot.getCourtSession(), slot.getOuCode(), slot.getBusinessType(), slot.getSessionDate(), slot.getCourtRoomNumber());
                return false;
            }
        }
        return true;
    }

    private int updateSlots(final Collection<CourtSchedule> slotsToUpdate, final Map<String, BusinessType> businessTypeMap) {
        final AtomicInteger numberOfUpdatedSlots = new AtomicInteger();
        slotsToUpdate.forEach(slotToUpdate -> {
            final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule slotToUpdateEntity = CourtScheduleMapper.toEntity(slotToUpdate);
            final BusinessType businessType = businessTypeMap.get(slotToUpdate.getBusinessType());
            if (nonNull(businessType)) {
                slotToUpdateEntity.setUpdatedOn(Calendar.getInstance().getTime());
                slotToUpdateEntity.setActive(true);
                slotToUpdate.setSlotBased(businessTypeMap.get(slotToUpdate.getBusinessType()).isSlot());

                final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtSchedule = courtScheduleRepository.update(slotToUpdateEntity, true);
                if (nonNull(courtSchedule)) {
                    numberOfUpdatedSlots.incrementAndGet();
                }
            } else {
                logger.warn("missing business type on update slot - {}", slotToUpdate.getBusinessType());
            }
        });

        return numberOfUpdatedSlots.get();
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
                    if (nonNull(courtScheduleIdAndOuCodePair)) {
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
                                      final List<String> courtScheduleIdsOfSavedSlots,
                                      final boolean forMigrated,
                                      final List<String> ouCodes) {

        final AtomicInteger numberOfSavedJudiciaries = new AtomicInteger();
        scheduleJudiciaries.forEach(scheduleJudiciary -> {
            final CourtSchedule courtSchedule = newRecords.get(scheduleJudiciary.getCourtListingProfileId());

            if (nonNull(courtSchedule) && (forMigrated || courtScheduleIdsOfSavedSlots.contains(courtSchedule.getCourtScheduleId()))) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary courtScheduleJudiciaryEntity = CourtScheduleJudiciaryMapper.toEntity(scheduleJudiciary);
                courtScheduleJudiciaryEntity.setUpdatedOn(Calendar.getInstance().getTime());
                if (!forMigrated) {
                    courtScheduleJudiciaryEntity.getId().setCourtScheduleId(courtSchedule.getCourtScheduleId());
                }
                courtScheduleJudiciaryRepository.save(courtScheduleJudiciaryEntity);
                numberOfSavedJudiciaries.incrementAndGet();
            }
        });

        logger.info("numberOfSavedJudiciaries: {} for ouCodes: {}", numberOfSavedJudiciaries.get(), ouCodes);
        return numberOfSavedJudiciaries.get();
    }

    private String enrichBusinessDescription(final String businessType, final Requester requester) {
        return referenceDataCache.getRotaBusinessTypeByCode(businessType, requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + businessType)).getTypeDescription();
    }

    private void processOnceFrequency(List<Session> sessionList, LocalDate startDate, List<CourtSchedule> courtScheduleList, Requester requester) {
        for (Session session : sessionList) {
            for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
                LocalDate sessionDateCandidate = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
                CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate, requester, session.getSessionStartTime(), session.getSessionEndTime());
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
                    CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate, requester, session.getSessionStartTime(), session.getSessionEndTime());
                    courtScheduleList.add(courtSchedule);
                }
            }
        }
    }

    private void processMonthlyFrequency(List<Session> sessionList, LocalDate startDate, LocalDate endDate, int repeatFor, List<CourtSchedule> courtScheduleList, Requester requester) {
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            for (Session session : sessionList) {
                populateCourtScheduleListForMonth(session, currentDate, endDate, courtScheduleList, requester);
            }
            currentDate = currentDate.plusMonths(repeatFor).withDayOfMonth(1);
        }
    }

    private void populateCourtScheduleListForMonth(Session session, LocalDate monthStart, LocalDate endDate, List<CourtSchedule> courtScheduleList, Requester requester) {
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        if (monthEnd.isAfter(endDate)) {
            monthEnd = endDate;
        }

        for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
            LocalDate sessionDateCandidate = findNthOccurrenceOfDayInMonth(dayOfWeek, monthStart, session.getIndex());

            if (sessionDateCandidate != null && !sessionDateCandidate.isAfter(endDate) &&
                    !sessionDateCandidate.isAfter(monthEnd) &&
                    !sessionDateCandidate.isBefore(monthStart)) {

                CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate, requester, session.getSessionStartTime(), session.getSessionEndTime());
                courtScheduleList.add(courtSchedule);
            }
        }
    }

    private LocalDate findNthOccurrenceOfDayInMonth(DayOfWeek dayOfWeek, LocalDate monthStart, Integer index) {
        LocalDate firstDayOfMonth = monthStart.withDayOfMonth(1);
        LocalDate firstOccurrence = firstDayOfMonth.with(TemporalAdjusters.nextOrSame(dayOfWeek));

        if (index == 1) {
            return firstOccurrence;
        }

        LocalDate nthOccurrence = firstOccurrence.plusWeeks( (long) index - 1);

        // Check if the nth occurrence is still within the same month
        if (nthOccurrence.getMonth() == firstDayOfMonth.getMonth()) {
            return nthOccurrence;
        }

        // If index is 5 and the month doesn't have 5 occurrences, fallback to index 4
        if (index == 5) {
            LocalDate fourthOccurrence = firstOccurrence.plusWeeks(3);
            if (fourthOccurrence.getMonth() == firstDayOfMonth.getMonth()) {
                return fourthOccurrence;
            }
        }

        return null;
    }

    private CourtSchedule buildCourtSchedule(Session session, LocalDate sessionDateCandidate, Requester requester, String sessionStartTime, String sessionEndTime) {
        final CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = new CourtSchedule.CourtScheduleBuilder();

        final DateUtils.SessionStartAndEndTime sessionStartAndEndTime = getOrElseDefaultSessionStartAndEndTimeIfEmpty(session.getSessionType(), sessionStartTime, sessionEndTime);
        final Date sessionStartDate = combineDateAndTime(sessionDateCandidate, sessionStartAndEndTime.sessionStartTime());

        courtScheduleBuilder.withCourtScheduleId(UUID.randomUUID().toString())
                .withBusinessType(session.getBusinessType())
                .withCourtHouseId(session.getCourtCentreId())
                .withCourtRoomId(session.getCourtRoomId())
                .withActive(true)
                .withSessionDate(sessionDateCandidate)
                .withCourtSession(session.getSessionType())
                .withPanel(session.getPanelType())
                .withAllDaySplit(TRUE.equals(session.isAllDaySplit()))
                .withMaxDurationForMorning(session.getMaxDurationForMorning())
                .withMaxDurationForAfternoon(session.getMaxDurationForAfternoon())
                .withSessionStartTime(sessionStartDate)
                .withSessionEndTime(combineDateAndTime(sessionDateCandidate, sessionStartAndEndTime.sessionEndTime()))
                .withIsOverbookingAllowed(TRUE.equals(session.isOverbookingAllowed()))
                .withNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDateCandidate))
                .withIsDraft(!isNull(session.isDraft()) && session.isDraft())
                .withJurisdiction(!isNull(session.getJurisdiction()) ? session.getJurisdiction() : MAGISTRATES.getJurisdiction());
        enrichSession(courtScheduleBuilder, session.getSlotsOrDuration(), requester);
        return courtScheduleBuilder.build();
    }

    private void saveCourtSchedules(List<CourtSchedule> courtScheduleList) {
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = courtScheduleList.stream()
                .map(CourtScheduleMapper::toEntity)
                .toList();
        courtScheduleRepository.saveCourtSchedules(courtScheduleEntities);
    }

    private void enrichSession(CourtSchedule.CourtScheduleBuilder builder, int maxSlotsOrDuration, Requester requester) {
        final BusinessType businessType = referenceDataCache.getRotaBusinessTypeByCode(builder.getBusinessType(), requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + builder.getBusinessType()));
        CourtRoom courtRoom;
        if ("CROWN".equalsIgnoreCase(builder.getJurisdiction())) {
            courtRoom = referenceDataCache.getCpCourtRoomByCourtRoomId(builder.getCourtRoomId(), requester).orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + builder.getCourtRoomId()));
        } else {
            courtRoom = referenceDataCache.getRotaCourtRoomByCourtRoomId(builder.getCourtRoomId(), requester).orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + builder.getCourtRoomId()));
        }

        if (businessType.isSlot()) {
            builder.withSlotBased(true);
            builder.withMaxSlots(maxSlotsOrDuration);
            builder.withAvailableSlots(maxSlotsOrDuration);
            builder.withMaxDuration(0);
            builder.withAvailableDuration(0);
        } else {
            builder.withSlotBased(false);
            builder.withMaxSlots(0);
            builder.withAvailableSlots(0);
            builder.withAvailableDuration(0);
            if (!builder.isAllDaySplit()) {
                builder.withMaxDuration(maxSlotsOrDuration);
                builder.withAvailableDuration(maxSlotsOrDuration);
            }
        }

        if (nonNull(courtRoom)) {
            builder.withOuCode(courtRoom.getOucode());
            builder.withCourtRoomName(courtRoom.getCourtroomName());
            builder.withCourtRoomNumber(courtRoom.getCppCourtRoomId());
            builder.withCourtHouseName(courtRoom.getOucodeL3Name());
            builder.withOperationalUnit(courtRoom.getOucodeL2Code());
        }
    }

    public JsonObject validateSessionIntegrity(final Session session, final LocalDate startDate, final LocalDate endDate, final Integer repeatFor) {
        logger.info("validateSessionIntegrity to check session integrity");
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> sessionsToCompare = courtScheduleRepository
                .getSimilarSessions(session.getCourtCentreId(), session.getCourtRoomId(), session.getBusinessType(), startDate, endDate);
        // session.repeatDays is a set, if it includes dayofweekvalue of sessionsToCompare
        for (uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule sessionToCompare : sessionsToCompare) {
            logger.debug("validateSessionIntegrity sessionToCompare : {}", sessionToCompare);
            //if either of the new session or DB session is AD, we can't add AM,PM or, AD session for the same date
            if (validatedWeeklyFrequency(session, sessionToCompare, startDate, endDate, repeatFor)) {
                return buildErrorResponse(format(ErrorMessages.DUPLICATE_SESSIONS, sessionToCompare.getCourtScheduleId()));
            }
        }
        return JsonValue.EMPTY_JSON_OBJECT;
    }

    private boolean validatedWeeklyFrequency(final Session session, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule sessionToCompare,
                                             LocalDate startDate, LocalDate endDate, Integer repeatFor) {
        //Method validates the hearing slots available for the EVERY_WEEK frequency considering repeatFor and repeatDays parameter
        //These params are needed to skip the weeks based on the frequency
        boolean violated = false;
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        for (long weekNumber = 0; weekNumber <= weeksBetween; weekNumber += repeatFor) {
            for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
                LocalDate sessionDateCandidate = startDate.plusWeeks(weekNumber).with(TemporalAdjusters.nextOrSame(dayOfWeek));
                logger.debug("validatedWeeklyFrequency sessionDateCandidate : {}", sessionDateCandidate);
                logger.debug("validatedWeeklyFrequency sessionToCompare.getSessionDate : {}", sessionToCompare.getSessionDate());
                if (session.getCourtCentreId().equals(sessionToCompare.getCourtHouseId()) &&
                        session.getCourtRoomId().equals(sessionToCompare.getCourtRoomId()) &&
                        session.getBusinessType().equals(sessionToCompare.getBusinessType()) &&
                        session.getRepeatDays().contains(DayOfWeek.of(sessionToCompare.getSessionDate().getDayOfWeek().getValue())) &&
                        sessionToCompare.getSessionDate().equals(sessionDateCandidate)) {
                    logger.debug("validatedWeeklyFrequency condition met");
                    violated = session.getSessionType().equals(sessionToCompare.getCourtSession()) || session.getSessionType().equals("AD") || sessionToCompare.getCourtSession().equals("AD");
                }
            }

        }
        return violated;
    }

    public List<CourtSchedule> getCourtSchedulesById(final SearchCourtSchedulesByIdRequestParam requestParam) {
        return courtScheduleRepository.getCourtSchedulesByIdList(requestParam.getCourtScheduleIds());
    }

    @Transactional
    public uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomResponse assignCourtroom(
            final uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomRequest request, final Requester requester) {

        final uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomResponse response =
                new uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomResponse();

        if (isEmpty(request.getCourtScheduleIds())) {
            return response;
        }

        // Get all sessions by IDs
        final List<CourtSchedule> sessions = courtScheduleRepository.getCourtSchedulesByIdList(request.getCourtScheduleIds());
        final Map<String, CourtSchedule> sessionMap = sessions.stream()
                .collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, s -> s));

        // Get all allocated listings to check for hearings
        final List<String> sessionIds = request.getCourtScheduleIds();
        final List<AllocatedListingEachBooked> allAllocatedListings =
                allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(sessionIds);
        final Map<String, List<AllocatedListingEachBooked>> allocatedListingsBySessionId =
                allAllocatedListings.stream()
                        .collect(Collectors.groupingBy(AllocatedListingEachBooked::getCourtScheduleId));

        // Get courtroom details
        final Optional<CourtRoom> courtRoom = referenceDataCache.getRotaCourtRoomByCourtRoomId(
                request.getCourtRoomId(), requester);

        if (courtRoom.isEmpty()) {
            // All sessions are ineligible if courtroom not found
            request.getCourtScheduleIds().forEach(id -> {
                final uk.gov.moj.cpp.courtscheduler.domain.IneligibleSession ineligible =
                        new uk.gov.moj.cpp.courtscheduler.domain.IneligibleSession(
                                id, "Courtroom not found");
                response.getIneligibleSessions().add(ineligible);
            });
            return response;
        }

        // Categorize sessions
        final List<CourtSchedule> eligibleSessions = new ArrayList<>();
        final List<uk.gov.moj.cpp.courtscheduler.domain.IneligibleSession> ineligibleSessions =
                new ArrayList<>();

        for (final String sessionId : request.getCourtScheduleIds()) {
            final CourtSchedule session = sessionMap.get(sessionId);

            if (isNull(session)) {
                ineligibleSessions.add(new uk.gov.moj.cpp.courtscheduler.domain.IneligibleSession(
                        sessionId, "Session not found"));
                continue;
            }

            // Check if session has hearings
            final List<AllocatedListingEachBooked> allocatedListings =
                    allocatedListingsBySessionId.getOrDefault(sessionId, emptyList());
            final boolean hasHearings = !isEmpty(allocatedListings);

            // Eligibility check based on acceptance criteria:
            // - Draft with hearings: YES (eligible)
            // - Draft without hearings: YES (eligible)
            // - Assigned with hearings: NO (not eligible)
            // - Assigned without hearings: YES (eligible)
            final boolean isDraft = session.isDraft();
            final boolean isAssigned = !isDraft;

            if (isAssigned && hasHearings) {
                // Scenario 4: Assigned with hearings - NOT eligible
                ineligibleSessions.add(new uk.gov.moj.cpp.courtscheduler.domain.IneligibleSession(
                        sessionId, "Cannot assign courtroom to an assigned session with hearings"));
            } else {
                // All other cases are eligible
                eligibleSessions.add(session);
            }
        }

        // Convert eligible sessions to view
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleView> eligibleSessionViews =
                eligibleSessions.stream()
                        .map(s -> {
                            s.setBusinessDescription(enrichBusinessDescription(s.getBusinessType(), requester));
                            return convertToView(s);
                        })
                        .collect(Collectors.toList());
        response.setEligibleSessions(eligibleSessionViews);
        response.setIneligibleSessions(ineligibleSessions);

        // Apply courtroom to eligible sessions
        for (final CourtSchedule session : eligibleSessions) {
            try {
                final UpdateCourtSchedule updateRequest = new UpdateCourtSchedule.UpdateCourtScheduleBuilder()
                        .withCourtScheduleId(session.getCourtScheduleId())
                        .withCourtRoomId(request.getCourtRoomId())
                        .withBusinessType(session.getBusinessType())
                        .withSessionType(session.getCourtSession())
                        .withPanel(session.getPanel())
                        .withMaxSlots(session.getMaxSlots())
                        .withMaxDuration(session.getMaxDuration())
                        .withMaxDurationForMorning(session.getMaxDurationForMorning())
                        .withMaxDurationForAfternoon(session.getMaxDurationForAfternoon())
                        .withAllDaySplit(session.isAllDaySplit())
                        .withSessionStartTime(session.getSessionStartTime() != null
                                ? sessionTimeFormatter(session.getSessionStartTime()) : null)
                        .withSessionEndTime(session.getSessionEndTime() != null
                                ? sessionTimeFormatter(session.getSessionEndTime()) : null)
                        .withIsOverbookingAllowed(session.isOverbookingAllowed())
                        .withJurisdiction(session.getJurisdiction() != null ? session.getJurisdiction() : "MAGISTRATES")
                        .withIsDraft(session.isDraft())
                        .build();

                final Result result = update(updateRequest, requester);

                if (!result.isSuccess()) {
                    response.getFailedSessions().add(new uk.gov.moj.cpp.courtscheduler.domain.FailedSession(
                            session.getCourtScheduleId(), result.getMsg()));
                }
            } catch (Exception e) {
                logger.error("Failed to assign courtroom to session {}: {}",
                        session.getCourtScheduleId(), e.getMessage());
                response.getFailedSessions().add(new uk.gov.moj.cpp.courtscheduler.domain.FailedSession(
                        session.getCourtScheduleId(),
                        "Failed to assign courtroom: " + (e.getMessage() != null ? e.getMessage() : "Unknown error")));
            }
        }

        return response;
    }

    private uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleView convertToView(final CourtSchedule session) {
        return new uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleView.CourtScheduleViewBuilder()
                .withCourtScheduleId(session.getCourtScheduleId())
                .withActive(session.isActive())
                .withTotalBooked(session.getTotalBooked())
                .withSlotBased(session.isSlotBased())
                .withAvailableDuration(session.getAvailableDuration())
                .withAvailableSlots(session.getAvailableSlots())
                .withBusinessType(session.getBusinessType())
                .withBusinessDescription(session.getBusinessDescription())
                .withCourtHouseId(session.getCourtHouseId())
                .withCourtHouseName(session.getCourtHouseName())
                .withCourtRoomNumber(session.getCourtRoomNumber())
                .withCourtRoomId(session.getCourtRoomId())
                .withCourtRoomName(session.getCourtRoomName())
                .withCourtSession(session.getCourtSession())
                .withListingProfileId(session.getListingProfileId())
                .withMaxDuration(session.getMaxDuration())
                .withMaxSlots(session.getMaxSlots())
                .withOperationalUnit(session.getOperationalUnit())
                .withOuCode(session.getOuCode())
                .withPanel(session.getPanel())
                .withSessionDate(session.getSessionDate())
                .withAllDaySplit(session.isAllDaySplit())
                .withMaxDurationForMorning(session.getMaxDurationForMorning())
                .withMaxDurationForAfternoon(session.getMaxDurationForAfternoon())
                .withTotalBookedForMorning(session.getTotalBookedForMorning())
                .withTotalBookedForAfternoon(session.getTotalBookedForAfternoon())
                .withAvailableDurationForMorning(session.getAvailableDurationForMorning())
                .withAvailableDurationForAfternoon(session.getAvailableDurationForAfternoon())
                .withMinHearingTime(session.getMinHearingTime())
                .withMaxHearingTime(session.getMaxHearingTime())
                .withSessionStartTime(session.getSessionStartTime() != null
                        ? sessionTimeFormatter(session.getSessionStartTime()) : null)
                .withSessionEndTime(session.getSessionEndTime() != null
                        ? sessionTimeFormatter(session.getSessionEndTime()) : null)
                .withIsOverbookingAllowed(session.isOverbookingAllowed())
                .withIsDraft(session.isDraft())
                .build();
    }
}