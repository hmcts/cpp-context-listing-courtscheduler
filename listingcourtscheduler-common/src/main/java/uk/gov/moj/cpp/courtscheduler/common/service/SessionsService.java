package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.CROWN;
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
import static uk.gov.moj.cpp.courtscheduler.domain.utils.BookingUtils.updateTotalBooked;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.getOrElseDefaultSessionStartAndEndTimeIfEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.normaliseToHourMinute;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.resolveSessionTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.sessionTimeFormatter;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toLocalTime;

// (removed) replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.converter.CourtScheduleToDeleteResponseConverter;
import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleMapper;
import uk.gov.moj.cpp.courtscheduler.openapi.model.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleDeleteResponseItem;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.BusinessType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleDeleteResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.OrganisationUnit;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Result;
import uk.gov.moj.cpp.courtscheduler.openapi.model.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.SlotAndScheduleInfo;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DayOfWeekConverter;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import org.springframework.transaction.annotation.Transactional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SessionsService {

    private static final Logger logger = LoggerFactory.getLogger(SessionsService.class);
    private static final int SESSION_AVAILABILITY_DEFAULT_DURATION = 180;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private AllocatedListingService allocatedListingService;
    @Inject
    private CourtMigrationRepository courtMigrationRepository;
    @Inject
    private ReferenceDataCache referenceDataCache;
    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;
    @Inject
    private CourtScheduleService courtScheduleService;

    @Transactional
    public void create(CreateSessionRequestParam createSessionRequestParam) {
        final List<CourtSchedule> courtScheduleList = new ArrayList<>();
        final List<Session> sessionList = createSessionRequestParam.getSessions();
        final RepeatPattern repeatPattern = createSessionRequestParam.getRepeatPattern();
        final LocalDate startDate = DateUtils.parseRepeatPatternDate(repeatPattern.getStartDate());
        final String normalisedEndDate = DateUtils.normaliseRepeatPatternEndDate(repeatPattern.getEndDate());
        final LocalDate endDate = normalisedEndDate != null ? DateUtils.parseRepeatPatternDate(normalisedEndDate) : null;
        final RepeatFrequency frequency = RepeatFrequency.valueOf(repeatPattern.getFrequency().trim().toUpperCase());

        if (frequency.equals(RepeatFrequency.ONCE)) {
            processOnceFrequency(sessionList, startDate, courtScheduleList);
        } else if (frequency.equals(RepeatFrequency.EVERY_WEEK)) {
            processWeeklyFrequency(sessionList, startDate, endDate, repeatPattern.getRepeatFor(), courtScheduleList);
        } else if (frequency.equals(RepeatFrequency.EVERY_MONTH)) {
            processMonthlyFrequency(sessionList, startDate, endDate, repeatPattern.getRepeatFor(), courtScheduleList);
        }

        saveCourtSchedules(courtScheduleList);
    }

    public List<CourtSchedule> getCourtSchedules(CourtScheduleRequestParam courtScheduleRequestParam) {
        final List<CourtSchedule> courtSchedules = courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam);
        courtScheduleRepository.enrichWithJudiciary(courtSchedules);
        courtSchedules.forEach(courtSchedule ->
                courtSchedule.setBusinessDescription(enrichBusinessDescription(courtSchedule.getBusinessType())));
        return courtSchedules;
    }

    public Result update(final UpdateCourtSchedule updateCourtSchedule) {
        final String courtScheduleId = updateCourtSchedule.getCourtScheduleId();
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId);
        if (isNull(persistedCourtSchedule)) {
            return new Result().msg(ErrorMessages.SESSION_NOT_FOUND).success(false);
        }

        // Check if jurisdiction is being changed - jurisdiction cannot be changed
        String persistedJurisdiction = nonNull(persistedCourtSchedule.getJurisdiction())
                ? persistedCourtSchedule.getJurisdiction()
                : MAGISTRATES.getJurisdiction();
        
        if (nonNull(updateCourtSchedule.getJurisdiction()) 
                && !persistedJurisdiction.equalsIgnoreCase(updateCourtSchedule.getJurisdiction())) {
            return new Result().msg("Jurisdiction cannot be changed").success(false);
        }

        final String persistedBusinessType = persistedCourtSchedule.getBusinessType();
        
        // Check if the updated business type exists
        Optional<BusinessType> updatedBusinessTypeOpt = referenceDataCache.getRotaBusinessTypeByCode(updateCourtSchedule.getBusinessType());
        if (updatedBusinessTypeOpt.isEmpty()) {
            return new Result().msg("Invalid business type").success(false);
        }
        
        BusinessType updatedBusinessType = updatedBusinessTypeOpt.get();
        
        // Check if business type jurisdiction matches the persisted jurisdiction
        // (We've already validated that update jurisdiction, if provided, matches persisted)
        String businessTypeJurisdiction = updatedBusinessType.getJurisdiction();
        if (nonNull(businessTypeJurisdiction)) {
            if (MAGISTRATES.equalsIgnoreCase(persistedJurisdiction) && !MAGISTRATES.equalsIgnoreCase(businessTypeJurisdiction)) {
                return new Result().msg("Business Type jurisdiction " + businessTypeJurisdiction + " does not match session jurisdiction " + persistedJurisdiction).success(false);
            }
            if (CROWN.equalsIgnoreCase(persistedJurisdiction) && !CROWN.equalsIgnoreCase(businessTypeJurisdiction)) {
                return new Result().msg("Business Type jurisdiction " + businessTypeJurisdiction + " does not match session jurisdiction " + persistedJurisdiction).success(false);
            }
        }
        
        if (isBusinessTypeChangeInvalid(updateCourtSchedule, persistedBusinessType, updatedBusinessType)) {
            return new Result().msg(ErrorMessages.BUSINESS_TYPE_CHANGE_NOT_ALLOWED).success(false);
        }

        final List<AllocatedListingEachBooked> allocatedListingEachBooked = allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(singletonList(courtScheduleId));
        Optional<Date> earliestHearingStartTime = allocatedListingEachBooked.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .filter(java.util.Objects::nonNull)
                .map(offsetDateTime -> Date.from(offsetDateTime.toInstant()))
                .min(Comparator.naturalOrder());

        Optional<Date> latestHearingStartTime = allocatedListingEachBooked.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .filter(java.util.Objects::nonNull)
                .map(offsetDateTime -> Date.from(offsetDateTime.toInstant()))
                .max(Comparator.naturalOrder());

        // When the update payload omits sessionStartTime/sessionEndTime, fall back to the persisted
        // values so the min/max-hearing-time validation still fires against existing allocated listings.
        final Date sessionStartTimeWithDate = StringUtils.isNotEmpty(updateCourtSchedule.getSessionStartTime())
                ? DateUtils.combineDateAndTime(persistedCourtSchedule.getSessionDate(), updateCourtSchedule.getSessionStartTime())
                : persistedCourtSchedule.getSessionStartTime();
        final Date sessionEndTimeWithDate = StringUtils.isNotEmpty(updateCourtSchedule.getSessionEndTime())
                ? DateUtils.combineDateAndTime(persistedCourtSchedule.getSessionDate(), updateCourtSchedule.getSessionEndTime())
                : persistedCourtSchedule.getSessionEndTime();

        if (sessionStartTimeWithDate != null && earliestHearingStartTime.isPresent()
                && sessionStartTimeWithDate.after(earliestHearingStartTime.get())) {
            return new Result().msg(SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME).success(false);
        }

        if (sessionEndTimeWithDate != null && latestHearingStartTime.isPresent()
                && sessionEndTimeWithDate.before(latestHearingStartTime.get())) {
            return new Result().msg(SESSION_END_TIME_CANNOT_BE_CHANGED_TO_BEFORE_HEARING_TIME).success(false);
        }

        boolean isChanged = checkEditValuesModified(updateCourtSchedule, persistedCourtSchedule);

        if (isChanged) {
            return new Result().msg(ErrorMessages.SESSION_EDIT_ANOTHER_USER).success(false);
        }

        if (nonNull(persistedCourtSchedule.getSupportAdSplit()) && TRUE.equals(persistedCourtSchedule.getSupportAdSplit() != Boolean.TRUE.equals(updateCourtSchedule.getAllDaySplit()))) {
            return new Result().msg(ErrorMessages.ALL_DAY_SPLIT_CHANGE_NOT_ALLOWED).success(false);
        }

        if (StringUtils.isNotEmpty(updateCourtSchedule.getSessionStartTime()) && StringUtils.isNotEmpty(updateCourtSchedule.getSessionEndTime())) {
            final LocalTime sessionStartTime = toLocalTime(updateCourtSchedule.getSessionStartTime());
            final LocalTime sessionEndTime = toLocalTime(updateCourtSchedule.getSessionEndTime());

            if (sessionStartTime.isAfter(sessionEndTime)) {
                return new Result().msg(ErrorMessages.SESSION_START_TIME_CANNOT_BE_LATER_THAN_END_TIME).success(false);
            }
            if (AM_SESSION.equals(updateCourtSchedule.getCourtSession()) && sessionEndTime.isAfter(LocalTime.of(13, 0))) {
                return new Result().msg(ErrorMessages.AM_SESSION_END_TIME_CANNOT_EXCEED).success(false);
            } else if ((AM_SESSION.equals(updateCourtSchedule.getCourtSession()) || ALL_DAY.equals(updateCourtSchedule.getCourtSession())) &&
                    sessionStartTime.isBefore(LocalTime.of(1, 0))) {
                return new Result().msg(format(ErrorMessages.SESSION_START_TIME_CANNOT_BE_EARLIER, updateCourtSchedule.getCourtSession())).success(false);
            } else if (PM_SESSION.equals(updateCourtSchedule.getCourtSession()) && sessionStartTime.isBefore(LocalTime.of(14, 0))) {
                return new Result().msg(ErrorMessages.PM_SESSION_START_TIME_CANNOT_BE_EARLIER).success(false);
            } else if ((PM_SESSION.equals(updateCourtSchedule.getCourtSession()) || ALL_DAY.equals(updateCourtSchedule.getCourtSession())) &&
                    sessionEndTime.isAfter(LocalTime.of(23, 59))) {
                return new Result().msg(format(ErrorMessages.SESSION_END_TIME_CANNOT_BE_LATER, updateCourtSchedule.getCourtSession())).success(false);
            }
        }

        updateAvailability(updateCourtSchedule, persistedCourtSchedule);

        String courtRoomId = updateCourtSchedule.getCourtRoomId();

        final Optional<CourtRoom> courtRoom;
        if (nonNull(courtRoomId) && !courtRoomId.equalsIgnoreCase(persistedCourtSchedule.getCourtRoomId())) {
            // Use persisted jurisdiction (we've already validated that update jurisdiction, if provided, matches persisted)
            String jurisdiction = persistedJurisdiction;

            if (CROWN.equalsIgnoreCase(jurisdiction)) {
                courtRoom = Optional.of(getCpCourtRoomForCourtCentre(courtRoomId, persistedCourtSchedule.getCourtHouseId()));
            } else {
                courtRoom = Optional.of(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId).orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + courtRoomId)));
            }
        } else {
            courtRoom = empty();
        }

        Result result;

        try {
            result = courtScheduleRepository.update(persistedCourtSchedule, updateCourtSchedule, courtRoom);
        } catch (Exception exception) {
            logger.error("update court schedule failing courScheduleId : {}", persistedCourtSchedule.getCourtScheduleId(), exception);
            result = new Result().msg(ErrorMessages.DUPLICATE_SESSIONS).success(false);
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
                !StringUtils.equals(updateCourtSchedule.getCourtSession(), persistedCourtSchedule.getCourtSession()) ||
                !StringUtils.equals(updateCourtSchedule.getPanel(), persistedCourtSchedule.getPanel());
    }

    private boolean isBusinessTypeChangeInvalid(final UpdateCourtSchedule updateCourtSchedule, final String persistedBusinessType, final BusinessType updatedBusinessType) {
        return !persistedBusinessType.equals(updateCourtSchedule.getBusinessType()) && !isBusinessTypeChangeAllowed(updateCourtSchedule, persistedBusinessType, updatedBusinessType);
    }

    /**
     * SPRDT-1351: a session persisted under a retired business type (the SPRDT-1291 consolidation
     * retires CROWN "FWT") can no longer have its slot/duration nature resolved from reference data.
     * Moving such a session onto a current type is exactly the correction the court needs to make, so
     * the change is permitted and only the update's own parameters are validated against the new
     * type. Known persisted types keep the slot-nature-must-match rule.
     */
    private boolean isBusinessTypeChangeAllowed(final UpdateCourtSchedule updateCourtSchedule, final String persistedBusinessTypeCode, final BusinessType updatedBusinessType) {
        final Optional<BusinessType> persistedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(persistedBusinessTypeCode);
        if (persistedBusinessType.isEmpty()) {
            logger.warn("{}{} - session persisted under a retired business type; allowing the change to {}",
                    BUSINESS_TYPE_NOT_FOUND, persistedBusinessTypeCode, updatedBusinessType.getTypeCode());
            return isUpdateRequestParamsAreValidForUpdate(updateCourtSchedule, Boolean.TRUE.equals(updatedBusinessType.getSlot()));
        }
        return Boolean.TRUE.equals(persistedBusinessType.get().getSlot()) == Boolean.TRUE.equals(updatedBusinessType.getSlot())
                && isUpdateRequestParamsAreValidForUpdate(updateCourtSchedule, Boolean.TRUE.equals(updatedBusinessType.getSlot()));
    }

    private static boolean isUpdateRequestParamsAreValidForUpdate(final UpdateCourtSchedule updateCourtSchedule, final boolean isSlotBased) {
        return (isSlotBased && updateCourtSchedule.getMaxDuration().equals(0)) || (!isSlotBased && updateCourtSchedule.getMaxSlots().equals(0));
    }

    public CourtScheduleDeleteResponse deleteCourtScheduleSessions(final SessionsParam sessionsParam) {
        final List<String> sessions = isEmpty(sessionsParam.getSessions())  ? new ArrayList<>() : sessionsParam.getSessions();
        final List<CourtSchedule> allocatedCourtSchedules = courtScheduleRepository.deleteCourtSchedule(sessions);
        allocatedCourtSchedules.forEach(courtSchedule -> courtSchedule.setBusinessDescription(enrichBusinessDescription(courtSchedule.getBusinessType())));
        List<AllocatedListingEachBooked> allocatedListingEachBooked = new ArrayList<>();
        if(!isEmpty(allocatedCourtSchedules)) {
            allocatedListingEachBooked = allocatedListingRepository.getAllocatedListingsEachBookedByCourtScheduleId(allocatedCourtSchedules.stream()
                            .map(CourtSchedule::getCourtScheduleId)
                            .toList())
                    .stream()
                    .toList();
        }
        final List<CourtScheduleDeleteResponseItem> courtScheduleDeleteResponses = CourtScheduleToDeleteResponseConverter.convert(allocatedCourtSchedules, allocatedListingEachBooked);
        if (allocatedCourtSchedules.isEmpty()) {
            return new CourtScheduleDeleteResponse().sessions(courtScheduleDeleteResponses);
        } else {
            return new CourtScheduleDeleteResponse()
                    .error("Some sessions could not be removed. Please check again.")
                    .sessions(courtScheduleDeleteResponses);
        }
    }

    public boolean isMigratedByCourtCentreId(final String courtCentreId) {
        return courtMigrationRepository.findByCourtCentreId(courtCentreId).isMigrated();
    }

    public Map<String, Boolean> migratedMapByOuCode() {
        return courtMigrationRepository.findAll().stream()
                .collect(Collectors.toMap(CourtSchedulerMigrationStatus::getOuCode, CourtSchedulerMigrationStatus::isMigrated));
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
            provisionalCourtScheduleEntity.setSlotBased(businessTypeMap.get(provisionalCourtScheduleEntity.getBusinessType()).getSlot());
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
                courtScheduleEntity.setSlotBased(businessTypeMap.get(slot.getBusinessType()).getSlot());
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
                slotToUpdate.setSlotBased(businessTypeMap.get(slotToUpdate.getBusinessType()).getSlot());

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

    /**
     * SPRDT-1351: display enrichment must survive a retired business type. Sessions persisted under a
     * code that reference data no longer carries (the SPRDT-1291 consolidation retires CROWN "FWT")
     * would otherwise fail every read, search and delete response that enriches them. The code itself
     * stands in for the missing description so the session stays visible and editable.
     */
    private String enrichBusinessDescription(final String businessType) {
        return referenceDataCache.getRotaBusinessTypeByCode(businessType)
                .map(BusinessType::getTypeDescription)
                .orElseGet(() -> {
                    logger.warn("{}{} - session persisted under a retired business type; using the code as its description",
                            BUSINESS_TYPE_NOT_FOUND, businessType);
                    return businessType;
                });
    }

    private static Set<DayOfWeek> repeatDaysOf(final Session session) {
        return session.getRepeatDays() == null
                ? new HashSet<>()
                : DayOfWeekConverter.convert(String.join(",", session.getRepeatDays()));
    }

    private void processOnceFrequency(List<Session> sessionList, LocalDate startDate, List<CourtSchedule> courtScheduleList) {
        for (Session session : sessionList) {
            for (DayOfWeek dayOfWeek : repeatDaysOf(session)) {
                LocalDate sessionDateCandidate = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
                CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate, session.getSessionStartTime(), session.getSessionEndTime());
                courtScheduleList.add(courtSchedule);
            }
        }
    }

    private void processWeeklyFrequency(List<Session> sessionList, LocalDate startDate, LocalDate endDate, int repeatFor, List<CourtSchedule> courtScheduleList) {
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        for (long weekNumber = 0; weekNumber <= weeksBetween; weekNumber += repeatFor) {
            for (Session session : sessionList) {
                for (DayOfWeek dayOfWeek : repeatDaysOf(session)) {
                    LocalDate sessionDateCandidate = startDate.plusWeeks(weekNumber).with(TemporalAdjusters.nextOrSame(dayOfWeek));
                    if (sessionDateCandidate.isAfter(endDate)) {
                        continue;
                    }
                    CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate, session.getSessionStartTime(), session.getSessionEndTime());
                    courtScheduleList.add(courtSchedule);
                }
            }
        }
    }

    private void processMonthlyFrequency(List<Session> sessionList, LocalDate startDate, LocalDate endDate, int repeatFor, List<CourtSchedule> courtScheduleList) {
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            for (Session session : sessionList) {
                populateCourtScheduleListForMonth(session, currentDate, endDate, courtScheduleList);
            }
            currentDate = currentDate.plusMonths(repeatFor).withDayOfMonth(1);
        }
    }

    private void populateCourtScheduleListForMonth(Session session, LocalDate monthStart, LocalDate endDate, List<CourtSchedule> courtScheduleList) {
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        if (monthEnd.isAfter(endDate)) {
            monthEnd = endDate;
        }

        for (DayOfWeek dayOfWeek : repeatDaysOf(session)) {
            LocalDate sessionDateCandidate = findNthOccurrenceOfDayInMonth(dayOfWeek, monthStart, session.getIndex());

            if (sessionDateCandidate != null && !sessionDateCandidate.isAfter(endDate) &&
                    !sessionDateCandidate.isAfter(monthEnd) &&
                    !sessionDateCandidate.isBefore(monthStart)) {

                CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate, session.getSessionStartTime(), session.getSessionEndTime());
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

        // If the nth occurrence doesn't exist in the month, return null (no session created for this month)
        return null;
    }

    /**
     * Returns the 1-based occurrence index of the given date's day-of-week within its month
     * (e.g. 1 for 1st Monday of month, 4 for 4th Friday).
     */
    private int getOccurrenceIndexOfDayInMonth(LocalDate date) {
        LocalDate firstDayOfMonth = date.withDayOfMonth(1);
        LocalDate firstOccurrence = firstDayOfMonth.with(TemporalAdjusters.nextOrSame(date.getDayOfWeek()));
        return (date.getDayOfMonth() - firstOccurrence.getDayOfMonth()) / 7 + 1;
    }

    private CourtSchedule buildCourtSchedule(Session session, LocalDate sessionDateCandidate, String sessionStartTime, String sessionEndTime) {
        final CourtSchedule courtSchedule = new CourtSchedule();

        courtSchedule.courtScheduleId(UUID.randomUUID().toString())
                .businessType(session.getBusinessType())
                .courtHouseId(session.getCourtCentreId())
                .courtRoomId(session.getCourtRoomId())
                .active(true)
                .sessionDate(sessionDateCandidate)
                .courtSession(session.getSessionType())
                .panel(session.getPanel())
                .allDaySplit(TRUE.equals(session.getAllDaySplit()))
                .maxDurationForMorning(session.getMaxDurationForMorning())
                .maxDurationForAfternoon(session.getMaxDurationForAfternoon())
                .overbookingAllowed(TRUE.equals(session.getIsOverbookingAllowed()))
                .nationalBreakTime(toOffsetDateTime(TimezoneUtils.calculateNationalBreakTime(sessionDateCandidate)))
                .draft(!isNull(session.getIsDraft()) && session.getIsDraft())
                .jurisdiction(!isNull(session.getJurisdiction()) ? session.getJurisdiction() : MAGISTRATES.getJurisdiction());
        enrichSession(courtSchedule, isNull(session.getDuration()) ? 0 : session.getDuration());

        applyResolvedSessionTimes(courtSchedule, session, sessionDateCandidate, sessionStartTime, sessionEndTime);
        return courtSchedule;
    }

    /**
     * Resolves the session start/end times (SPRDT-809):
     * <ul>
     *   <li><b>End time</b> is always the fixed per-session-type default (AM 13:00, PM 17:00, AD 17:00) —
     *       reference data is never consulted for the end.</li>
     *   <li><b>Start time</b> for AM and ALL_DAY comes from the court centre's organisation-unit
     *       {@code defaultStartTime} (looked up by {@code session.getCourtCentreId()}, the same UUID as
     *       {@code organisation_unit.id}), falling back to the hardcoded default when absent. PM always
     *       starts at the fixed afternoon default and never queries reference data.</li>
     *   <li>A custom start/end supplied on the API request overrides its own field regardless of type.</li>
     * </ul>
     */
    private void applyResolvedSessionTimes(final CourtSchedule courtSchedule,
                                           final Session session,
                                           final LocalDate sessionDate,
                                           final String customStartTime,
                                           final String customEndTime) {
        final String sessionType = session.getSessionType();

        final String refDataStartTime = resolveRefDataStartTime(session);

        final DateUtils.SessionStartAndEndTime defaults = getOrElseDefaultSessionStartAndEndTimeIfEmpty(sessionType, null, null);
        final String resolvedStart = resolveSessionTime(customStartTime, refDataStartTime, defaults.sessionStartTime());
        final String resolvedEnd = resolveSessionTime(customEndTime, null, defaults.sessionEndTime());

        courtSchedule.sessionStartTime(toOffsetDateTime(combineDateAndTime(sessionDate, resolvedStart)))
                .sessionEndTime(toOffsetDateTime(combineDateAndTime(sessionDate, resolvedEnd)));
    }

    /**
     * Court centre (organisation-unit) default start time — consulted for AM and ALL_DAY only. PM
     * always starts at the fixed afternoon default and never queries reference data.
     * <p>
     * The upstream defaultStartTime has been observed in both {@code HH:mm} and {@code HH:mm:ss}
     * form (confirmed live on ns-ste-ccm-22: {@code "10:30:00"}) — normalised via
     * {@link DateUtils#normaliseToHourMinute} before use. An unparseable value falls back to the
     * hardcoded default (logged at WARN) rather than failing {@code combineDateAndTime} downstream.
     */
    private String resolveRefDataStartTime(final Session session) {
        if (PM_SESSION.equals(session.getSessionType())) {
            return null;
        }
        final String rawStartTime = referenceDataCache.getOrganisationUnit(session.getCourtCentreId())
                .map(OrganisationUnit::getDefaultStartTime)
                .orElse(null);
        final String normalisedStartTime = normaliseToHourMinute(rawStartTime);
        if (StringUtils.isNotBlank(rawStartTime) && isNull(normalisedStartTime)) {
            logger.warn("Unparseable organisation-unit defaultStartTime '{}' for courtCentreId {} - falling back to default start",
                    rawStartTime, session.getCourtCentreId());
        }
        return normalisedStartTime;
    }

    private void saveCourtSchedules(List<CourtSchedule> courtScheduleList) {
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = courtScheduleList.stream()
                .map(CourtScheduleMapper::toEntity)
                .toList();
        courtScheduleRepository.saveCourtSchedules(courtScheduleEntities);
    }

    private void enrichSession(CourtSchedule courtSchedule, int maxSlotsOrDuration) {
        final BusinessType businessType = referenceDataCache.getRotaBusinessTypeByCode(courtSchedule.getBusinessType()).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + courtSchedule.getBusinessType()));
        CourtRoom courtRoom;
        if ("CROWN".equalsIgnoreCase(courtSchedule.getJurisdiction())) {
            courtRoom = getCpCourtRoomForCourtCentre(courtSchedule.getCourtRoomId(), courtSchedule.getCourtHouseId());
        } else {
            courtRoom = referenceDataCache.getRotaCourtRoomByCourtRoomId(courtSchedule.getCourtRoomId()).orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + courtSchedule.getCourtRoomId()));
        }

        if (Boolean.TRUE.equals(businessType.getSlot())) {
            courtSchedule.slotBased(true);
            courtSchedule.maxSlots(maxSlotsOrDuration);
            courtSchedule.availableSlots(maxSlotsOrDuration);
            courtSchedule.maxDuration(0);
            courtSchedule.availableDuration(0);
        } else {
            courtSchedule.slotBased(false);
            courtSchedule.maxSlots(0);
            courtSchedule.availableSlots(0);
            courtSchedule.availableDuration(0);
            if (!Boolean.TRUE.equals(courtSchedule.getAllDaySplit())) {
                courtSchedule.maxDuration(maxSlotsOrDuration);
                courtSchedule.availableDuration(maxSlotsOrDuration);
            }
        }

        if (nonNull(courtRoom)) {
            courtSchedule.ouCode(courtRoom.getOucode());
            courtSchedule.courtRoomName(courtRoom.getCourtroomName());
            courtSchedule.courtRoomNumber(courtRoom.getCppCourtRoomId());
            courtSchedule.courtHouseName(courtRoom.getOucodeL3Name());
            courtSchedule.operationalUnit(courtRoom.getOucodeL2Code());
        }
    }

    /**
     * A CP courtroom shared between court centres has one membership per centre, each carrying
     * that centre's OU-derived fields (ouCode, court house name, operational unit). The
     * membership for the session's own centre must be used; enriching from another centre's
     * membership would persist the wrong court house details. Validation guarantees a matching
     * membership exists, so a miss here means stale reference data and the request must fail.
     * Only a session with no court centre id at all (legacy data) falls back to an arbitrary
     * membership, as there is nothing to match against.
     */
    private CourtRoom getCpCourtRoomForCourtCentre(final String courtRoomId, final String courtCentreId) {
        if (isNull(courtCentreId)) {
            logger.warn("No court centre id for session using courtroom {}; enriching from an arbitrary court centre membership", courtRoomId);
            return referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId)
                    .orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + courtRoomId));
        }
        return referenceDataCache.getCpCourtRoomByCourtRoomIdAndCourtCentreId(courtRoomId, courtCentreId)
                .orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + courtRoomId + " in court centre " + courtCentreId));
    }

    public JsonObject validateSessionIntegrity(final Session session, final LocalDate startDate, final LocalDate endDate, final Integer repeatFor) {
        return validateSessionIntegrity(session, startDate, endDate, repeatFor, null);
    }

    public JsonObject validateSessionIntegrity(final Session session, final LocalDate startDate, final LocalDate endDate, final Integer repeatFor, final RepeatFrequency frequency) {
        final long totalStartTime = System.currentTimeMillis();
        logger.info("validateSessionIntegrity to check session integrity");
        
        final String jurisdiction = nonNull(session.getJurisdiction()) ? session.getJurisdiction() : MAGISTRATES.getJurisdiction();
        
        long stepStart = System.currentTimeMillis();
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> sessionsToCompare = courtScheduleRepository
                .getSimilarSessions(session.getCourtCentreId(), session.getCourtRoomId(), session.getBusinessType(), startDate, endDate, jurisdiction);
        logger.info("[PERF] getSimilarSessions DB query took {} ms, returned {} records", System.currentTimeMillis() - stepStart, sessionsToCompare.size());
        
        stepStart = System.currentTimeMillis();
        // session.repeatDays is a set, if it includes dayofweekvalue of sessionsToCompare
        for (uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule sessionToCompare : sessionsToCompare) {
            logger.debug("validateSessionIntegrity sessionToCompare : {}", sessionToCompare);
            //if either of the new session or DB session is AD, we can't add AM,PM or, AD session for the same date
            boolean isDuplicate = false;
            if (frequency == RepeatFrequency.EVERY_MONTH && session.getIndex() != null) {
                isDuplicate = validatedMonthlyFrequency(session, sessionToCompare, startDate, endDate, repeatFor);
            } else {
                isDuplicate = validatedWeeklyFrequency(session, sessionToCompare, startDate, endDate, repeatFor);
            }
            if (isDuplicate) {
                logger.info("[PERF] validateSessionIntegrity comparison loop took {} ms (found duplicate)", System.currentTimeMillis() - stepStart);
                logger.info("[PERF] validateSessionIntegrity total took {} ms", System.currentTimeMillis() - totalStartTime);
                return buildErrorResponse(format(ErrorMessages.DUPLICATE_SESSIONS, sessionToCompare.getCourtScheduleId()));
            }
        }
        logger.info("[PERF] validateSessionIntegrity comparison loop took {} ms (no duplicates)", System.currentTimeMillis() - stepStart);
        logger.info("[PERF] validateSessionIntegrity total took {} ms", System.currentTimeMillis() - totalStartTime);
        return JsonValue.EMPTY_JSON_OBJECT;
    }

    private boolean validatedWeeklyFrequency(final Session session, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule sessionToCompare,
                                             LocalDate startDate, LocalDate endDate, Integer repeatFor) {
        //Method validates the hearing slots available for the EVERY_WEEK frequency considering repeatFor and repeatDays parameter
        //These params are needed to skip the weeks based on the frequency
        boolean violated = false;
        final Set<DayOfWeek> repeatDays = repeatDaysOf(session);
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        for (long weekNumber = 0; weekNumber <= weeksBetween; weekNumber += repeatFor) {
            for (DayOfWeek dayOfWeek : repeatDays) {
                LocalDate sessionDateCandidate = startDate.plusWeeks(weekNumber).with(TemporalAdjusters.nextOrSame(dayOfWeek));
                logger.debug("validatedWeeklyFrequency sessionDateCandidate : {}", sessionDateCandidate);
                logger.debug("validatedWeeklyFrequency sessionToCompare.getSessionDate : {}", sessionToCompare.getSessionDate());
                if (StringUtils.equals(session.getCourtCentreId(), sessionToCompare.getCourtHouseId()) &&
                        StringUtils.equals(session.getCourtRoomId(), sessionToCompare.getCourtRoomId()) &&
                        StringUtils.equals(session.getBusinessType(), sessionToCompare.getBusinessType()) &&
                        repeatDays.contains(DayOfWeek.of(sessionToCompare.getSessionDate().getDayOfWeek().getValue())) &&
                        sessionDateCandidate.equals(sessionToCompare.getSessionDate())) {
                    logger.debug("validatedWeeklyFrequency condition met");
                    violated = StringUtils.equals(session.getSessionType(), sessionToCompare.getCourtSession()) || 
                            StringUtils.equals(session.getSessionType(), "AD") || 
                            StringUtils.equals(sessionToCompare.getCourtSession(), "AD");
                }
            }

        }
        return violated;
    }

    private boolean validatedMonthlyFrequency(final Session session, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule sessionToCompare,
                                             LocalDate startDate, LocalDate endDate, Integer repeatFor) {
        //Method validates the hearing slots available for the EVERY_MONTH frequency considering repeatFor, repeatDays, and index parameter
        //Calculates the actual dates that would be created (e.g., 4th Friday of each month) and checks for conflicts
        boolean violated = false;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            LocalDate monthStart = currentDate.withDayOfMonth(1);
            LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
            if (monthEnd.isAfter(endDate)) {
                monthEnd = endDate;
            }

            for (DayOfWeek dayOfWeek : repeatDaysOf(session)) {
                LocalDate sessionDateCandidate = findNthOccurrenceOfDayInMonth(dayOfWeek, monthStart, session.getIndex());

                if (sessionDateCandidate != null && !sessionDateCandidate.isAfter(endDate) &&
                        !sessionDateCandidate.isAfter(monthEnd) &&
                        !sessionDateCandidate.isBefore(monthStart)) {
                    logger.debug("validatedMonthlyFrequency sessionDateCandidate : {}", sessionDateCandidate);
                    logger.debug("validatedMonthlyFrequency sessionToCompare.getSessionDate : {}", sessionToCompare.getSessionDate());
                    // Same date implies same occurrence (sessionDateCandidate is computed from session.getIndex()).
                    // Days with indexes: only flag when existing session's date is same occurrence (e.g. 4th Friday).
                    final boolean sameOccurrenceIndex = session.getIndex() == null
                            || getOccurrenceIndexOfDayInMonth(sessionToCompare.getSessionDate()) == session.getIndex();
                    if (StringUtils.equals(session.getCourtCentreId(), sessionToCompare.getCourtHouseId()) &&
                            StringUtils.equals(session.getCourtRoomId(), sessionToCompare.getCourtRoomId()) &&
                            StringUtils.equals(session.getBusinessType(), sessionToCompare.getBusinessType()) &&
                            sessionDateCandidate.equals(sessionToCompare.getSessionDate()) &&
                            sameOccurrenceIndex) {
                        logger.debug("validatedMonthlyFrequency condition met (same day and index)");
                        violated = StringUtils.equals(session.getSessionType(), sessionToCompare.getCourtSession()) ||
                                StringUtils.equals(session.getSessionType(), "AD") ||
                                StringUtils.equals(sessionToCompare.getCourtSession(), "AD");
                        if (violated) {
                            return true; // Early return on first duplicate found
                        }
                    }
                }
            }
            currentDate = currentDate.plusMonths(repeatFor).withDayOfMonth(1);
        }
        return violated;
    }

    public List<CourtSchedule> getCourtSchedulesById(final List<String> courtScheduleIds) {
        return courtScheduleRepository.getCourtSchedulesByIdList(courtScheduleIds);
    }

    public Optional<String> validateSessionAvailabilityListMode(final List<String> courtScheduleIds, final Integer requestedDuration) {
        Optional<String> error = validateListModeIdsNotEmpty(courtScheduleIds);
        if (error.isPresent()) {
            return error;
        }
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules =
                courtScheduleRepository.findByCourtScheduleIds(courtScheduleIds);
        error = validateListModeSchedulesFound(courtScheduleIds, courtSchedules);
        if (error.isPresent()) {
            return error;
        }
        error = validateListModeAllIdsFound(courtScheduleIds, courtSchedules);
        if (error.isPresent()) {
            return error;
        }
        error = validateListModeAllSameType(courtSchedules);
        if (error.isPresent()) {
            return error;
        }
        error = validateListModeAllSameCentre(courtSchedules);
        if (error.isPresent()) {
            return error;
        }
        error = validateListModeAllSameJurisdiction(courtSchedules);
        if (error.isPresent()) {
            return error;
        }
        if (courtSchedules.get(0).isSlotBased()) {
            return validateListModeSlotBased(courtSchedules, courtScheduleIds);
        }
        return validateListModeDurationBased(courtSchedules, requestedDuration);
    }

    private Optional<String> validateListModeIdsNotEmpty(final List<String> courtScheduleIds) {
        if (courtScheduleIds == null || courtScheduleIds.isEmpty()) {
            return of("Court Schedule Ids cannot be empty");
        }
        return empty();
    }

    private Optional<String> validateListModeSchedulesFound(final List<String> courtScheduleIds,
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules) {
        if (courtSchedules == null || courtSchedules.isEmpty()) {
            return of("Court Schedule Ids not found: " + courtScheduleIds);
        }
        return empty();
    }

    private Optional<String> validateListModeAllIdsFound(final List<String> courtScheduleIds,
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules) {
        Set<String> retrievedIds = courtSchedules.stream()
                .map(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule::getCourtScheduleId)
                .collect(toSet());
        List<String> missingIds = courtScheduleIds.stream()
                .filter(id -> !retrievedIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            return of("Court Schedule Ids not found: " + missingIds);
        }
        return empty();
    }

    private Optional<String> validateListModeAllSameType(final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules) {
        boolean firstIsSlotBased = courtSchedules.get(0).isSlotBased();
        boolean allSameType = courtSchedules.stream()
                .allMatch(schedule -> schedule.isSlotBased() == firstIsSlotBased);
        if (!allSameType) {
            return of("All court schedules should be either slot-based or duration-based");
        }
        return empty();
    }

    private Optional<String> validateListModeAllSameCentre(
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules) {
        String firstCourtHouseId = courtSchedules.get(0).getCourtHouseId();
        boolean allSameCentre = courtSchedules.stream()
                .allMatch(schedule -> Objects.equals(firstCourtHouseId, schedule.getCourtHouseId()));
        if (!allSameCentre) {
            return of("All court schedules must belong to the same court centre");
        }
        return empty();
    }

    private Optional<String> validateListModeAllSameJurisdiction(
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules) {
        String firstJurisdiction = courtSchedules.get(0).getJurisdiction();
        boolean allSameJurisdiction = courtSchedules.stream()
                .allMatch(schedule -> Objects.equals(firstJurisdiction, schedule.getJurisdiction()));
        if (!allSameJurisdiction) {
            return of("All court schedules must belong to the same jurisdiction");
        }
        return empty();
    }

    private Optional<String> validateListModeSlotBased(
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules,
            final List<String> courtScheduleIds) {
        Map<String, Integer> allocatedMap = allocatedListingService.getAllocatedListingsByCourtScheduleId(courtScheduleIds);
        for (uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule schedule : courtSchedules) {
            if (Boolean.TRUE.equals(schedule.getIsOverbookingAllowed())) {
                continue;
            }
            int totalBooked = allocatedMap.getOrDefault(schedule.getCourtScheduleId(), 0);
            int maxSlots = schedule.getMaxSlots();
            if (totalBooked >= maxSlots) {
                return of("One or more schedules are no longer available, please reschedule your hearing");
            }
        }
        return empty();
    }

    private Optional<String> validateListModeDurationBased(
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtSchedules,
            final Integer requestedDuration) {
        if (requestedDuration == null || requestedDuration < 1) {
            return of("Duration is mandatory and should be greater than 0");
        }
        if (requestedDuration > 360) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule anchor = courtSchedules.get(0);
            if (CROWN.equalsIgnoreCase(anchor.getJurisdiction())) {
                return validateListModeMultiDay(anchor, requestedDuration);
            }
        }
        for (uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule schedule : courtSchedules) {
            if (Boolean.TRUE.equals(schedule.getIsOverbookingAllowed())) {
                continue;
            }
            AvailabilityBreakdown availability = evaluateAvailability(schedule);
            if (availability.total() < requestedDuration) {
                return of(formatInsufficientAvailability(schedule.getCourtScheduleId(), availability, requestedDuration));
            }
        }
        return empty();
    }

    private Optional<String> validateListModeMultiDay(
            final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule anchor,
            final int requestedDuration) {
        int daysNeeded = requestedDuration / 360;
        LocalDate startDate = anchor.getSessionDate();
        if (startDate == null) {
            return of("Court schedule has no session date");
        }
        String courtRoomId = anchor.getCourtRoomId();
        String businessType = anchor.getBusinessType();
        String courtSession = anchor.getCourtSession();
        if (courtRoomId == null || businessType == null || courtSession == null) {
            return of("Court schedule is missing courtRoomId, businessType or courtSession");
        }
        LocalDate endDate = advanceByWeekdays(startDate, daysNeeded - 1);
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> schedules =
                courtScheduleRepository.findActiveByCourtRoomIdBetweenDates(
                        courtRoomId, startDate, endDate, businessType, courtSession);
        Map<LocalDate, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> scheduleByDate = schedules.stream()
                .filter(cs -> cs.getSessionDate() != null)
                .collect(Collectors.toMap(
                        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule::getSessionDate,
                        cs -> cs,
                        (a, b) -> Boolean.TRUE.equals(a.getIsOverbookingAllowed()) ? b : a));
        for (LocalDate d = startDate; !d.isAfter(endDate); d = nextWeekday(d)) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule daySchedule = scheduleByDate.get(d);
            if (daySchedule == null) {
                return of(format("No active court schedule found for %s in court room %s (businessType %s, courtSession %s).",
                        d, courtRoomId, businessType, courtSession));
            }
            if (Boolean.TRUE.equals(daySchedule.getIsOverbookingAllowed())) {
                continue;
            }
            AvailabilityBreakdown availability = evaluateAvailability(daySchedule);
            if (availability.total() < 360) {
                return of(format("On %s: %s", d,
                        formatInsufficientAvailability(daySchedule.getCourtScheduleId(), availability, 360)));
            }
        }
        return empty();
    }

    private AvailabilityBreakdown evaluateAvailability(
            final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs) {
        if (TRUE.equals(cs.getSupportAdSplit())) {
            AtomicInteger totalBookedForMorning = new AtomicInteger(0);
            AtomicInteger totalBookedForAfternoon = new AtomicInteger(0);
            List<AllocatedListingEachBooked> bookings =
                    allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(cs.getCourtScheduleId());
            calculateTotalBookedForSessionAvailability(bookings, cs, totalBookedForMorning, totalBookedForAfternoon);
            int morningMax = cs.getMaxAdMorningDuration() != null ? cs.getMaxAdMorningDuration() : 0;
            int afternoonMax = cs.getMaxAdAfternoonDuration() != null ? cs.getMaxAdAfternoonDuration() : 0;
            int morning = Math.max(0, morningMax - totalBookedForMorning.get());
            int afternoon = Math.max(0, afternoonMax - totalBookedForAfternoon.get());
            return new AvailabilityBreakdown(morning + afternoon, morning, afternoon, true);
        }
        int available = cs.getAvailableDuration() != null ? cs.getAvailableDuration() : 0;
        return new AvailabilityBreakdown(available, 0, 0, false);
    }

    private static String formatInsufficientAvailability(
            final String courtScheduleId, final AvailabilityBreakdown availability, final int requested) {
        if (availability.adSplit()) {
            return format("Schedule %s has %d minutes available across morning (%d) and afternoon (%d), but %d minutes are required.",
                    courtScheduleId, availability.total(), availability.morning(), availability.afternoon(), requested);
        }
        return format("Schedule %s has %d minutes available but %d minutes are required.",
                courtScheduleId, availability.total(), requested);
    }

    private record AvailabilityBreakdown(int total, int morning, int afternoon, boolean adSplit) {
    }

    static LocalDate advanceByWeekdays(final LocalDate start, final int weekdaysToAdvance) {
        LocalDate date = start;
        for (int i = 0; i < weekdaysToAdvance; i++) {
            date = nextWeekday(date);
        }
        return date;
    }

    static LocalDate nextWeekday(final LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next;
    }

    private void calculateTotalBookedForSessionAvailability(
            List<AllocatedListingEachBooked> allocatedListingEachBookedForThisSchedule,
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule,
            AtomicInteger totalBookedForMorning, AtomicInteger totalBookedForAfternoon) {
        allocatedListingEachBookedForThisSchedule.forEach(eachBooked -> {
            if (isMorningSessionForSessionAvailability(eachBooked, persistedCourtSchedule)) {
                updateTotalBooked(eachBooked.getDuration(), totalBookedForMorning, totalBookedForAfternoon, SESSION_AVAILABILITY_DEFAULT_DURATION);
            } else {
                totalBookedForAfternoon.set(totalBookedForAfternoon.get() + eachBooked.getDuration());
            }
        });
    }

    private boolean isMorningSessionForSessionAvailability(AllocatedListingEachBooked eachBooked,
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        final java.time.OffsetDateTime hearingStartTime = eachBooked.getHearingStartTime();
        final java.time.OffsetDateTime sessionStartTime = DateUtils.toOffsetDateTime(persistedCourtSchedule.getSessionStartTime());
        final java.time.OffsetDateTime afternoonStartTime = DateUtils.toOffsetDateTime(
                combineDateAndTime(persistedCourtSchedule.getSessionDate(), DEFAULT_AFTERNOON_START_TIME));
        return (hearingStartTime.isAfter(sessionStartTime) || hearingStartTime.isEqual(sessionStartTime))
                && hearingStartTime.isBefore(afternoonStartTime);
    }

    public uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomResponse assignCourtroom(
            final uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomRequest request) {

        // Get all sessions by IDs
        final List<CourtSchedule> sessions = courtScheduleRepository.getCourtSchedulesByIdList(request.getCourtScheduleIds());
        final Map<String, CourtSchedule> sessionMap = sessions.stream()
                .collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, s -> s));


        // Get courtroom details; a courtroom shared between court centres has one entry per centre membership
        final List<CourtRoom> courtRoomMemberships = referenceDataCache.getCpCourtRoomsByCourtRoomId(
                request.getCourtRoomId());

        if (courtRoomMemberships.isEmpty()) {
            // All sessions are ineligible if courtroom not found
            final List<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleView> notFoundSessions =
                    request.getCourtScheduleIds().stream()
                            .map(id -> {
                                final CourtSchedule notFoundSession = new CourtSchedule();
                                notFoundSession.setCourtScheduleId(id);
                                return convertToView(notFoundSession);
                            })
                            .toList();
            final List<uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomErrorGroup> errorGroups = List.of(
                    new uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomErrorGroup()
                            .sessions(notFoundSessions)
                            .error("Courtroom not found"));
            return new uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomResponse()
                    .errorGroups(errorGroups);
        }

        final Set<String> courtRoomCourtCentreIds = courtRoomMemberships.stream()
                .map(CourtRoom::getOucodeUUID)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // Track sessions with their error reasons
        final List<Pair<CourtSchedule, String>> sessionsWithErrors = new ArrayList<>();
        final List<CourtSchedule> eligibleSessions = new ArrayList<>();

        for (final String sessionId : request.getCourtScheduleIds()) {
            final CourtSchedule session = sessionMap.get(sessionId);

            if (isNull(session)) {
                // Create a minimal session object for "Session not found" case
                final CourtSchedule notFoundSession = new CourtSchedule();
                notFoundSession.setCourtScheduleId(sessionId);
                sessionsWithErrors.add(Pair.of(notFoundSession, "Session not found"));
                continue;
            }

            // Check if session is CROWN jurisdiction
            final String jurisdiction = session.getJurisdiction();
            if (isNull(jurisdiction) || !CROWN.equalsIgnoreCase(jurisdiction)) {
                sessionsWithErrors.add(Pair.of(session, "assign.courtroom endpoint is only valid for CROWN jurisdiction sessions"));
                continue;
            }

            // Check if courtroom belongs to the same court centre as the session
            final String sessionCourtCentreId = session.getCourtHouseId();
            if (isNull(sessionCourtCentreId) || !courtRoomCourtCentreIds.contains(sessionCourtCentreId)) {
                sessionsWithErrors.add(Pair.of(session, "The new courtroom must belong to the same court centre as the session"));
                continue;
            }

            // Check for duplicate sessions
            final String courtRoomId = request.getCourtRoomId();
            final String sessionCourtSession = session.getCourtSession();
            final List<String> duplicateSessionTypes = getDuplicateSessionTypes(sessionCourtSession);
            
            if (isNotEmpty(duplicateSessionTypes) && nonNull(courtRoomId) && nonNull(session.getSessionDate()) 
                    && nonNull(session.getBusinessType()) && nonNull(session.getCourtHouseId())) {
                final List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> duplicateSessions = 
                        courtScheduleRepository.findDuplicateSessionsForAssignCourtroom(
                                courtRoomId,
                                session.getSessionDate(),
                                session.getBusinessType(),
                                duplicateSessionTypes,
                                session.getCourtHouseId(),
                                session.getCourtScheduleId());
                
                if (isNotEmpty(duplicateSessions)) {
                    sessionsWithErrors.add(Pair.of(session, "Duplicate session already exists with same business type, session date, and courtroom"));
                    continue;
                }
            }

            // Eligibility check based on acceptance criteria:
            // - Draft with hearings: NO (not eligible) - Business Rule: Cannot assign courtroom to draft session with hearings
            // - Draft without hearings: YES (eligible)
            // - Assigned with hearings: NO (not eligible) - Business Rule 5
            // - Assigned without hearings: NO (not eligible) - Business Rule 5
            final boolean isDraft = Boolean.TRUE.equals(session.getDraft());
            final boolean isAssigned = !isDraft;

            if (isAssigned) {
                // Scenario 5: Assigned session - NOT eligible (regardless of hearings)
                sessionsWithErrors.add(Pair.of(session, "Cannot assign courtroom to an assigned session"));
            } else if (isDraft) {
                // Check if draft session has hearings booked
                List<AllocatedListingEachBooked> allocatedListings = allocatedListingRepository
                        .getAllocatedListingsEachBookedByCourtScheduleId(List.of(session.getCourtScheduleId()));
                if (!allocatedListings.isEmpty()) {
                    // Draft session with hearings booked - NOT eligible
                    sessionsWithErrors.add(Pair.of(session, "Cannot assign courtroom to a CROWN draft session with hearings booked"));
                } else {
                    // Draft without hearings - eligible
                    eligibleSessions.add(session);
                }
            } else {
                // Draft without hearings - eligible
                eligibleSessions.add(session);
            }
        }

        // Apply courtroom to eligible sessions and track failures
        for (final CourtSchedule session : eligibleSessions) {
            try {
                // eligible sessions passed the centre check, so a membership for their court house always exists
                final CourtRoom courtRoomForSession = courtRoomMemberships.stream()
                        .filter(c -> session.getCourtHouseId().equals(c.getOucodeUUID()))
                        .findFirst()
                        .orElse(courtRoomMemberships.get(0));
                assignCourtroomToSession(session.getCourtScheduleId(), request.getCourtRoomId(), courtRoomForSession);

                // Success - no error to add
            } catch (Exception e) {
                logger.error("Failed to assign courtroom to session {}: {}",
                        session.getCourtScheduleId(), e.getMessage());
                sessionsWithErrors.add(Pair.of(session,
                        "Failed to assign courtroom: " + (e.getMessage() != null ? e.getMessage() : "Unknown error")));
            }
        }

        // Group sessions by error reason and convert to views
        final Map<String, List<CourtSchedule>> sessionsByError = sessionsWithErrors.stream()
                .collect(Collectors.groupingBy(
                        Pair::getRight,
                        Collectors.mapping(Pair::getLeft, Collectors.toList())
                ));

        final List<uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomErrorGroup> errorGroups = new ArrayList<>();
        for (final Map.Entry<String, List<CourtSchedule>> entry : sessionsByError.entrySet()) {
            final List<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleView> sessionViews = entry.getValue().stream()
                    .map(s -> {
                        if (nonNull(s.getBusinessType())) {
                            try {
                                s.setBusinessDescription(enrichBusinessDescription(s.getBusinessType()));
                            } catch (RuntimeException e) {
                                // If business type not found in reference data, set description to null
                                // This allows the error group to be created even if reference data is incomplete
                                logger.warn("Business type not found for session {}: {}", s.getCourtScheduleId(), s.getBusinessType());
                                s.setBusinessDescription(null);
                            }
                        }
                        return convertToView(s);
                    })
                    .toList();
            errorGroups.add(new uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomErrorGroup()
                    .sessions(sessionViews)
                    .error(entry.getKey()));
        }

        return new uk.gov.moj.cpp.courtscheduler.openapi.model.AssignCourtroomResponse()
                .errorGroups(errorGroups);
    }

    /**
     * Assigns a courtroom to a session by updating only the courtroomId and isDraft fields.
     * No other validations are performed - this is a direct update operation.
     *
     * @param courtScheduleId The ID of the court schedule to update
     * @param courtRoomId The new courtroom ID to assign
     * @param courtRoom The courtroom details (already retrieved)
     */
    private void assignCourtroomToSession(final String courtScheduleId, 
                                          final String courtRoomId, 
                                          final CourtRoom courtRoom) {
        // Retrieve the persisted entity
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = 
                courtScheduleRepository.retrieveCourtScheduleWithListingById(courtScheduleId);
        
        if (isNull(persistedCourtSchedule)) {
            throw new RuntimeException("Session not found: " + courtScheduleId);
        }

        // Update only courtroomId and isDraft fields
        persistedCourtSchedule.setCourtRoomId(courtRoomId);
        persistedCourtSchedule.setIsDraft(false); // Assigned means isDraft set to false
        
        // Update courtroom-related fields from the courtRoom object
        persistedCourtSchedule.setOuCode(courtRoom.getOucode());
        persistedCourtSchedule.setCourtRoomName(courtRoom.getCourtroomName());
        persistedCourtSchedule.setCourtRoomNumber(courtRoom.getCppCourtRoomId());
        persistedCourtSchedule.setCourtHouseName(courtRoom.getOucodeL3Name());
        persistedCourtSchedule.setOperationalUnit(courtRoom.getOucodeL2Code());
        
        // Update timestamp
        persistedCourtSchedule.setUpdatedOn(Calendar.getInstance().getTime());
        
        // Save the changes directly via repository
        courtScheduleRepository.save(persistedCourtSchedule);
    }


    private uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleView convertToView(final CourtSchedule session) {
        return new uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleView()
                .courtScheduleId(session.getCourtScheduleId())
                .active(session.getActive())
                .totalBooked(session.getTotalBooked())
                .slotBased(session.getSlotBased())
                .availableDuration(session.getAvailableDuration())
                .availableSlots(session.getAvailableSlots())
                .businessType(session.getBusinessType())
                .businessDescription(session.getBusinessDescription())
                .courtHouseId(session.getCourtHouseId())
                .courtHouseName(session.getCourtHouseName())
                .courtRoomNumber(session.getCourtRoomNumber())
                .courtRoomId(session.getCourtRoomId())
                .courtRoomName(session.getCourtRoomName())
                .courtSession(session.getCourtSession())
                .listingProfileId(session.getListingProfileId())
                .maxDuration(session.getMaxDuration())
                .maxSlots(session.getMaxSlots())
                .operationalUnit(session.getOperationalUnit())
                .ouCode(session.getOuCode())
                .panel(session.getPanel())
                .sessionDate(session.getSessionDate())
                .allDaySplit(session.getAllDaySplit())
                .maxDurationForMorning(session.getMaxDurationForMorning())
                .maxDurationForAfternoon(session.getMaxDurationForAfternoon())
                .totalBookedForMorning(session.getTotalBookedForMorning())
                .totalBookedForAfternoon(session.getTotalBookedForAfternoon())
                .availableDurationForMorning(session.getAvailableDurationForMorning())
                .availableDurationForAfternoon(session.getAvailableDurationForAfternoon())
                .minHearingTime(session.getMinHearingTime())
                .maxHearingTime(session.getMaxHearingTime())
                .sessionStartTime(session.getSessionStartTime() != null
                        ? sessionTimeFormatter(Date.from(session.getSessionStartTime().toInstant())) : null)
                .sessionEndTime(session.getSessionEndTime() != null
                        ? sessionTimeFormatter(Date.from(session.getSessionEndTime().toInstant())) : null)
                .isOverbookingAllowed(session.getOverbookingAllowed())
                .isDraft(session.getDraft());
    }

    /**
     * Determines which session types to check for duplicates based on the current session type.
     * - AM session: check for AM or AD
     * - PM session: check for PM or AD
     * - AD session: check for AD, AM, or PM
     *
     * @param sessionType The current session type (AM, PM, or AD)
     * @return List of session types to check for duplicates
     */
    private List<String> getDuplicateSessionTypes(final String sessionType) {
        if (isNull(sessionType)) {
            return emptyList();
        }
        
        if (AM_SESSION.equalsIgnoreCase(sessionType)) {
            return List.of(AM_SESSION, ALL_DAY);
        } else if (PM_SESSION.equalsIgnoreCase(sessionType)) {
            return List.of(PM_SESSION, ALL_DAY);
        } else if (ALL_DAY.equalsIgnoreCase(sessionType)) {
            return List.of(ALL_DAY, AM_SESSION, PM_SESSION);
        }
        
        return emptyList();
    }
}