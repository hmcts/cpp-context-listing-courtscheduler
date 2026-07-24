package uk.gov.moj.cpp.courtscheduler.api.validator;

import org.springframework.stereotype.Service;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.LocalTime.of;
import static java.time.LocalTime.parse;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toSet;
import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_AFTER_END_DATE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.CROWN;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.AM_SESSION_END_TIME_CANNOT_EXCEED;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.BUSINESS_TYPE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.CREATE_SESSIONS_COURTROOM_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.PM_SESSION_START_TIME_CANNOT_BE_EARLIER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_END_TIME_CANNOT_BE_LATER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_START_TIME_CANNOT_BE_EARLIER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_START_TIME_CANNOT_BE_LATER_THAN_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency.EVERY_MONTH;
import static uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency.EVERY_WEEK;
import static uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency.ONCE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.BookingUtils.updateTotalBooked;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_AFTERNOON_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.combineDateAndTime;

// (removed) replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionValidationParams;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SessionsApiValidator {

    private static final DateTimeFormatter TIME_FORMATTER = ofPattern("HH:mm");
    /** Court session wall-clock times are compared with hearing times in Europe/London — must not use JVM default (CI is often UTC). */
    private static final String EUROPE_LONDON = "Europe/London";
    public static final int DEFAULT_DURATION = 180;
    private static final String PERF_VALIDATE_ADDED_SESSION_PAYLOAD_DUPLICATE_CHECK_MS =
            "[PERF] validateAddedSessionPayload duplicate check took {} ms";
    private static final String PERF_VALIDATE_ADDED_SESSION_PAYLOAD_TOTAL_MS =
            "[PERF] validateAddedSessionPayload total took {} ms";

    @Inject
    private SessionsService sessionsService;
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionsApiValidator.class.getName());

    @Inject
    private ReferenceDataCache referenceDataCache;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private AllocatedListingService allocatedListingService;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    public JsonObject getSessionsCreateValidation(final CreateSessionRequestParam createSessionRequestParam) {
        final long totalStartTime = System.currentTimeMillis();

        final LocalDate patternStartDate = createSessionRequestParam.getRepeatPattern().getStartDate();
        final LocalDate patternEndDate = createSessionRequestParam.getRepeatPattern().getEndDate();
        final RepeatFrequency repeatFrequency = createSessionRequestParam.getRepeatPattern().getFrequency();

        LOGGER.info("Validating CREATE Sessions input : {}", createSessionRequestParam);

        long stepStart = System.currentTimeMillis();
        JsonObject repeatPatternValidation = validateRepeatPattern(createSessionRequestParam, patternStartDate, patternEndDate, repeatFrequency);
        LOGGER.info("[PERF] validateRepeatPattern took {} ms", System.currentTimeMillis() - stepStart);
        if (repeatPatternValidation != EMPTY_JSON_OBJECT) {
            LOGGER.info("[PERF] getSessionsCreateValidation total took {} ms (early return: repeatPatternValidation)", System.currentTimeMillis() - totalStartTime);
            return repeatPatternValidation;
        }

        stepStart = System.currentTimeMillis();
        JsonObject sessionValidation = validateSessionsBasicRules(createSessionRequestParam);
        LOGGER.info("[PERF] validateSessionsBasicRules took {} ms", System.currentTimeMillis() - stepStart);
        if (sessionValidation != EMPTY_JSON_OBJECT) {
            LOGGER.info("[PERF] getSessionsCreateValidation total took {} ms (early return: sessionValidation)", System.currentTimeMillis() - totalStartTime);
            return sessionValidation;
        }

        if(Objects.nonNull(createSessionRequestParam.getSessionToBeAdded())){
            stepStart = System.currentTimeMillis();
            JsonObject result = validateSessionToBeAddedPath(createSessionRequestParam, patternStartDate, patternEndDate, repeatFrequency);
            LOGGER.info("[PERF] validateSessionToBeAddedPath took {} ms", System.currentTimeMillis() - stepStart);
            LOGGER.info("[PERF] getSessionsCreateValidation total took {} ms", System.currentTimeMillis() - totalStartTime);
            return result;
        }

        stepStart = System.currentTimeMillis();
        final JsonObject businessTypeAndCourtRoomValidationResult = validateBusinessTypesAndCourtRooms(createSessionRequestParam);
        LOGGER.info("[PERF] validateBusinessTypesAndCourtRooms took {} ms", System.currentTimeMillis() - stepStart);
        if (businessTypeAndCourtRoomValidationResult != EMPTY_JSON_OBJECT) {
            LOGGER.info("[PERF] getSessionsCreateValidation total took {} ms (early return: businessTypeAndCourtRoomValidation)", System.currentTimeMillis() - totalStartTime);
            return businessTypeAndCourtRoomValidationResult;
        }

        LOGGER.info("[PERF] getSessionsCreateValidation total took {} ms", System.currentTimeMillis() - totalStartTime);
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateRepeatPattern(CreateSessionRequestParam createSessionRequestParam, LocalDate patternStartDate, 
                                             LocalDate patternEndDate, RepeatFrequency repeatFrequency) {
        if (repeatFrequency == EVERY_MONTH) {
            JsonObject err = validateMonthlyCrownIndexForRequest(createSessionRequestParam);
            if (err != EMPTY_JSON_OBJECT) return err;
        }

        if (patternEndDate != null && patternEndDate.isBefore(patternStartDate)) {
            return buildErrorResponse(START_DATE_AFTER_END_DATE);
        }

        if (patternStartDate.isBefore(ChronoLocalDate.from(LocalDateTime.now()))) {
            LOGGER.debug("getSessionsCreateValidation patternStartDate isBefore");
            return getMessageForInvalidDate(patternStartDate.toString());
        }

        if (isRepeatFrequencyRequiringEndDate(repeatFrequency) && patternEndDate == null) {
            LOGGER.debug("getSessionsCreateValidation repeatFrequency {} and patternEndDate null", repeatFrequency);
            return getMessageForInvalidParameterCombination(repeatFrequency);
        }

        return EMPTY_JSON_OBJECT;
    }

    private boolean isRepeatFrequencyRequiringEndDate(RepeatFrequency repeatFrequency) {
        return repeatFrequency == EVERY_WEEK || repeatFrequency == EVERY_MONTH;
    }

    private JsonObject validateSessionsBasicRules(CreateSessionRequestParam createSessionRequestParam) {
        final JsonObject result = validateSessionStartEndTime(createSessionRequestParam.getSessionList());
        if (result != null) {
            return result;
        }

        final JsonObject isDraftValidationResult = validateIsDraftForJurisdiction(createSessionRequestParam);
        if (isDraftValidationResult != EMPTY_JSON_OBJECT) {
            return isDraftValidationResult;
        }

        final JsonObject panelValidationResult = validatePanelForJurisdiction(createSessionRequestParam);
        if (panelValidationResult != EMPTY_JSON_OBJECT) {
            return panelValidationResult;
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateSessionToBeAddedPath(CreateSessionRequestParam createSessionRequestParam, 
                                                    LocalDate patternStartDate, LocalDate patternEndDate, 
                                                    RepeatFrequency repeatFrequency) {
        LOGGER.debug("getSessionsCreateValidation getSessionToBeAdded not null");
        
        long stepStart = System.currentTimeMillis();
        final JsonObject addSessionValidationResult = validateAddedSessionPayload(createSessionRequestParam, repeatFrequency);
        LOGGER.info("[PERF] validateAddedSessionPayload took {} ms", System.currentTimeMillis() - stepStart);
        if(addSessionValidationResult != EMPTY_JSON_OBJECT){
            return addSessionValidationResult;
        }
        LOGGER.debug("getSessionsCreateValidation addSessionValidationResult is empty");
        LOGGER.debug("getSessionsCreateValidation repeatFrequency: {}", repeatFrequency);
        
        stepStart = System.currentTimeMillis();
        JsonObject result = sessionsService.validateSessionIntegrity(createSessionRequestParam.getSessionToBeAdded(), 
                patternStartDate, patternEndDate, createSessionRequestParam.getRepeatPattern().getRepeatFor(), repeatFrequency);
        LOGGER.info("[PERF] validateSessionIntegrity took {} ms", System.currentTimeMillis() - stepStart);
        return result;
    }

    private JsonObject validateBusinessTypesAndCourtRooms(CreateSessionRequestParam requestParam) {
        for (Session session : requestParam.getSessionList()) {
            JsonObject error = validateSessionBusinessTypeAndCourtRoom(session);
            if (error != EMPTY_JSON_OBJECT) return error;
        }
        if (nonNull(requestParam.getSessionToBeAdded())) {
            JsonObject error = validateSessionBusinessTypeAndCourtRoom(requestParam.getSessionToBeAdded());
            if (error != EMPTY_JSON_OBJECT) return error;
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateSessionBusinessTypeAndCourtRoom(Session session) {
        final long totalStartTime = System.currentTimeMillis();
        
        JsonObject allDaySplitError = validateAllDaySplitForSession(session);
        if (allDaySplitError != EMPTY_JSON_OBJECT) {
            return allDaySplitError;
        }

        long stepStart = System.currentTimeMillis();
        Optional<BusinessType> businessTypeOpt = referenceDataCache.getRotaBusinessTypeByCode(session.getBusinessType());
        LOGGER.info("[PERF] getRotaBusinessTypeByCode took {} ms", System.currentTimeMillis() - stepStart);
        if (businessTypeOpt.isEmpty()) {
            return buildErrorResponse(BUSINESS_TYPE_NOT_FOUND + session.getBusinessType());
        }
        BusinessType businessType = businessTypeOpt.get();

        String sessionJurisdiction = getSessionJurisdiction(session);
        JsonObject businessTypeJurisdictionError = validateBusinessTypeJurisdiction(sessionJurisdiction, businessType);
        if (businessTypeJurisdictionError != EMPTY_JSON_OBJECT) {
            return businessTypeJurisdictionError;
        }

        if (!isDurationBasedWithValidDuration(session, businessType)) {
            return buildErrorResponse("Duration should be supplied for duration-based business type " + session.getBusinessType());
        }

        stepStart = System.currentTimeMillis();
        JsonObject result = validateCourtRoomForSession(session, sessionJurisdiction);
        LOGGER.info("[PERF] validateCourtRoomForSession took {} ms", System.currentTimeMillis() - stepStart);
        LOGGER.info("[PERF] validateSessionBusinessTypeAndCourtRoom total took {} ms", System.currentTimeMillis() - totalStartTime);
        return result;
    }

    private JsonObject validateAllDaySplitForSession(Session session) {
        if (ALL_DAY.equals(session.getSessionType()) && isNull(session.isAllDaySplit())) {
            return buildErrorResponse(ErrorMessages.ALL_DAY_SPLIT_MANDATORY_FOR_AD_SESSION);
        }
        return EMPTY_JSON_OBJECT;
    }

    private String getSessionJurisdiction(Session session) {
        return nonNull(session.getJurisdiction()) ? session.getJurisdiction() : MAGISTRATES.getJurisdiction();
    }

    private JsonObject validateBusinessTypeJurisdiction(String sessionJurisdiction, BusinessType businessType) {
        String businessTypeJurisdiction = businessType.getJurisdiction();
        if (isNull(businessTypeJurisdiction)) {
            return EMPTY_JSON_OBJECT; // Null jurisdiction is allowed for both CROWN and MAGISTRATES
        }

        if (MAGISTRATES.equalsIgnoreCase(sessionJurisdiction) && !MAGISTRATES.equalsIgnoreCase(businessTypeJurisdiction)) {
            return buildErrorResponse("Business Type jurisdiction " + businessTypeJurisdiction + " does not match session jurisdiction " + sessionJurisdiction);
        }
        if (CROWN.equalsIgnoreCase(sessionJurisdiction) && !CROWN.equalsIgnoreCase(businessTypeJurisdiction)) {
            return buildErrorResponse("Business Type jurisdiction " + businessTypeJurisdiction + " does not match session jurisdiction " + sessionJurisdiction);
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateCourtRoomForSession(Session session, String sessionJurisdiction) {
        String courtRoomId = session.getCourtRoomId();

        // 1. Courtroom must exist in CP reference data (regardless of session jurisdiction)
        long stepStart = System.currentTimeMillis();
        Optional<CourtRoom> cpCourtRoomOpt = referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId);
        LOGGER.info("[PERF] getCpCourtRoomByCourtRoomId took {} ms", System.currentTimeMillis() - stepStart);
        if (cpCourtRoomOpt.isEmpty()) {
            return buildErrorResponse("Courtroom does not exist");
        }
        CourtRoom courtRoom = cpCourtRoomOpt.get();

        // 2. Jurisdiction validation: oucode must match session jurisdiction (B = Magistrates, C = Crown)
        JsonObject jurisdictionError = validateCourtRoomOucodeJurisdiction(courtRoom, sessionJurisdiction);
        if (jurisdictionError != EMPTY_JSON_OBJECT) {
            return jurisdictionError;
        }

        // 3. Courtroom must belong to the same court centre as supplied in the payload
        JsonObject courtCentreError = validateCourtRoomBelongsToCourtCentre(session.getCourtCentreId(), courtRoom);
        if (courtCentreError != EMPTY_JSON_OBJECT) {
            return courtCentreError;
        }

        // 4. For MAGISTRATES only: courtroom must exist in Rota mapping
        if (MAGISTRATES.equalsIgnoreCase(sessionJurisdiction)) {
            Optional<CourtRoom> rotaCourtRoomOpt = referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId);
            if (rotaCourtRoomOpt.isEmpty()) {
                rotaProcessLogService.saveRotaProcessLog(
                        uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog()
                                .withErrorCode(CREATE_SESSIONS_COURTROOM_NOT_FOUND.code())
                                .withErrorText(CREATE_SESSIONS_COURTROOM_NOT_FOUND.format(courtRoomId))
                                .build()
                );
                return buildErrorResponse("Courtroom selected does not exist in Rota");
            }
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateCourtRoomBelongsToCourtCentre(String sessionCourtCentreId, CourtRoom courtRoom) {
        String courtRoomCourtCentreId = courtRoom.getOucodeUUID();
        if (isNull(sessionCourtCentreId) || isNull(courtRoomCourtCentreId)
                || !sessionCourtCentreId.equals(courtRoomCourtCentreId)) {
            return buildErrorResponse("This courtroom belongs to a different court centre");
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateCourtRoomOucodeJurisdiction(CourtRoom courtRoom, String sessionJurisdiction) {
        String oucode = courtRoom.getOucode();
        if (nonNull(oucode) && ((oucode.startsWith("B") && CROWN.equalsIgnoreCase(sessionJurisdiction))
                || (oucode.startsWith("C") && MAGISTRATES.equalsIgnoreCase(sessionJurisdiction)))) {
            return buildErrorResponse("Courtroom doesn't belong to this jurisdiction");
        }
        return EMPTY_JSON_OBJECT;
    }

    private static boolean isDurationBasedWithValidDuration(final Session session, final BusinessType businessType) {
        //if slot based based, then its ok. otherwise if its all day split, morning/afternoon duration should be supplied,for regular allday duraton should be supplied
        return businessType.isSlot() || (allDaySplitWithValidDuration(session) || hasValidDuration(session));
    }

    private static boolean hasValidDuration(final Session session) {
        return nonNull(session.getSlotsOrDuration()) && (session.getSlotsOrDuration() >= 0);
    }

    private static boolean allDaySplitWithValidDuration(final Session session) {
        return ALL_DAY.equals(session.getSessionType()) && session.isAllDaySplit() && (nonNull(session.getMaxDurationForMorning()) && nonNull(session.getMaxDurationForAfternoon()));
    }

    private JsonObject validateMonthlyCrownIndexForRequest(CreateSessionRequestParam requestParam) {
        for (Session s : requestParam.getSessionList()) {
            JsonObject err = validateMonthlyCrownIndex(s);
            if (!err.isEmpty()) return err;
        }
        Session sessionToBeAdded = requestParam.getSessionToBeAdded();
        if (sessionToBeAdded != null) {
            JsonObject err = validateMonthlyCrownIndex(sessionToBeAdded);
            if (!err.isEmpty()) return err;
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateMonthlyCrownIndex(Session session) {
        if (session == null) return EMPTY_JSON_OBJECT;
        if (!CROWN.equalsIgnoreCase(session.getJurisdiction())) {
            return EMPTY_JSON_OBJECT;
        }
        Integer index = session.getIndex();
        if (index == null) {
            return buildErrorResponse("For CROWN jurisdiction with EVERY_MONTH frequency, 'index' is required and must be between 1 and 5.");
        }
        if (index < 1 || index > 5) {
            return buildErrorResponse("For CROWN jurisdiction with EVERY_MONTH frequency, 'index' must be between 1 and 5.");
        }
        return EMPTY_JSON_OBJECT;
    }

    public JsonObject getSessionsAvailabilityValidation(final ValidateSessionAvailabilityRequestParam param) {
        if (param.courtScheduleIds() == null || param.courtScheduleIds().isEmpty()) {
            return buildErrorResponse("Court Schedule Ids cannot be empty");
        }
        Optional<String> error = sessionsService.validateSessionAvailabilityListMode(
                param.courtScheduleIds(), param.slotsOrDuration());
        return error.map(this::buildErrorResponse).orElse(EMPTY_JSON_OBJECT);
    }

    private JsonObject validateSessionStartEndTime(final List<Session> sessionList) {
        return sessionList.stream()
                .filter(session -> session.getSessionStartTime() != null && session.getSessionEndTime() != null)
                .map(session -> {
                    try {
                        LocalTime sessionStartTime = parse(session.getSessionStartTime(), TIME_FORMATTER);
                        LocalTime sessionEndTime = parse(session.getSessionEndTime(), TIME_FORMATTER);

                        final String sessionType = session.getSessionType();
                        if (sessionStartTime.isAfter(sessionEndTime)) {
                            return buildErrorResponse(SESSION_START_TIME_CANNOT_BE_LATER_THAN_END_TIME);
                        }
                        if (sessionStartTime.isBefore(of(1, 0))) {
                            return buildErrorResponse(SESSION_START_TIME_CANNOT_BE_EARLIER.formatted(sessionType));
                        }
                        if (sessionEndTime.isAfter(of(23, 0))) {
                            return buildErrorResponse(SESSION_END_TIME_CANNOT_BE_LATER.formatted(sessionType));
                        }
                        if (sessionType.equals(PM_SESSION) && sessionStartTime.isBefore(of(14, 0))) {
                            return buildErrorResponse(PM_SESSION_START_TIME_CANNOT_BE_EARLIER);
                        }
                        if (sessionType.equals(AM_SESSION) && sessionEndTime.isAfter(of(13, 0))) {
                            return buildErrorResponse(AM_SESSION_END_TIME_CANNOT_EXCEED);
                        }
                    } catch (DateTimeParseException e) {
                        return buildErrorResponse("Invalid time format. Please use HH:mm format.");
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private JsonObject validateAddedSessionPayload(final CreateSessionRequestParam createSessionRequestParam,
                                                   final RepeatFrequency repeatFrequency
    ) {
        final long totalStartTime = System.currentTimeMillis();
        final Session sessionToBeAdded = createSessionRequestParam.getSessionToBeAdded();
        final long duplicateCheckStart = System.currentTimeMillis();

        Optional<JsonObject> duplicateError = repeatFrequency == EVERY_MONTH
                ? findDuplicateErrorForMonthlyPayload(createSessionRequestParam, sessionToBeAdded)
                : findDuplicateErrorForWeeklyPayload(createSessionRequestParam, sessionToBeAdded);

        LOGGER.info(PERF_VALIDATE_ADDED_SESSION_PAYLOAD_DUPLICATE_CHECK_MS, System.currentTimeMillis() - duplicateCheckStart);
        if (duplicateError.isPresent()) {
            LOGGER.info(PERF_VALIDATE_ADDED_SESSION_PAYLOAD_TOTAL_MS, System.currentTimeMillis() - totalStartTime);
            return duplicateError.get();
        }

        long stepStart = System.currentTimeMillis();
        JsonObject businessTypeAndCourtRoomValidationResult = validateSessionBusinessTypeAndCourtRoom(sessionToBeAdded);
        LOGGER.info("[PERF] validateSessionBusinessTypeAndCourtRoom took {} ms", System.currentTimeMillis() - stepStart);
        if (businessTypeAndCourtRoomValidationResult != EMPTY_JSON_OBJECT) {
            LOGGER.info(PERF_VALIDATE_ADDED_SESSION_PAYLOAD_TOTAL_MS, System.currentTimeMillis() - totalStartTime);
            return businessTypeAndCourtRoomValidationResult;
        }

        stepStart = System.currentTimeMillis();
        JsonObject result = validateSessionToBeAdded(sessionToBeAdded);
        LOGGER.info("[PERF] validateSessionToBeAdded took {} ms", System.currentTimeMillis() - stepStart);
        LOGGER.info(PERF_VALIDATE_ADDED_SESSION_PAYLOAD_TOTAL_MS, System.currentTimeMillis() - totalStartTime);
        return result;
    }

    private Optional<JsonObject> findDuplicateErrorForMonthlyPayload(final CreateSessionRequestParam createSessionRequestParam,
                                                                    final Session sessionToBeAdded) {
        List<Session> matchingSessions = collectSessionsMatchingCourtCentreRoomAndBusinessType(
                sessionToBeAdded, createSessionRequestParam.getSessionList());
        matchingSessions.add(sessionToBeAdded);
        Map<String, Session> dayIndexToSession = new HashMap<>();
        for (Session session : matchingSessions) {
            Optional<JsonObject> error = checkMonthlySessionForDuplicate(dayIndexToSession, session);
            if (error.isPresent()) {
                return error;
            }
        }
        return Optional.empty();
    }

    private List<Session> collectSessionsMatchingCourtCentreRoomAndBusinessType(final Session sessionToBeAdded,
                                                                                final List<Session> sessionList) {
        List<Session> matching = new ArrayList<>();
        for (Session session : sessionList) {
            if (isSameCourtCentreRoomAndBusinessType(session, sessionToBeAdded)) {
                matching.add(session);
            }
        }
        return matching;
    }

    private static boolean isSameCourtCentreRoomAndBusinessType(final Session a, final Session b) {
        return a.getCourtCentreId().equals(b.getCourtCentreId())
                && a.getCourtRoomId().equals(b.getCourtRoomId())
                && a.getBusinessType().equals(b.getBusinessType())
                && Objects.equals(a.isDraft(), b.isDraft());
    }

    private Optional<JsonObject> checkMonthlySessionForDuplicate(final Map<String, Session> dayIndexToSession,
                                                               final Session session) {
        for (DayOfWeek day : session.getRepeatDays()) {
            Integer index = session.getIndex();
            if (index == null) {
                continue;
            }
            String key = day.name() + "_" + index;
            if (dayIndexToSession.containsKey(key)) {
                if (isSessionTypeDuplicateOrNotValidForAllDay(dayIndexToSession.get(key), session)) {
                    LOGGER.info("getSessionsCreateValidation DUPLICATE_SESSIONS (monthly: same day and index)");
                    return Optional.of(buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS));
                }
            } else {
                dayIndexToSession.put(key, session);
            }
        }
        return Optional.empty();
    }

    private Optional<JsonObject> findDuplicateErrorForWeeklyPayload(final CreateSessionRequestParam createSessionRequestParam,
                                                                    final Session sessionToBeAdded) {
        Set<DayOfWeek> repeatDaysToBeAdded = new HashSet<>(sessionToBeAdded.getRepeatDays());
        for (Session session : createSessionRequestParam.getSessionList()) {
            LOGGER.info("getSessionsCreateValidation getSessionList not null");
            boolean match = isSameCourtCentreRoomAndBusinessType(session, sessionToBeAdded);
            LOGGER.info("getSessionsCreateValidation match value : {}", match);
            if (match && hasOverlappingDayWithDuplicateSessionType(repeatDaysToBeAdded, session, sessionToBeAdded)) {
                LOGGER.info("getSessionsCreateValidation DUPLICATE_SESSIONS");
                return Optional.of(buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS));
            }
        }
        return Optional.empty();
    }

    private boolean hasOverlappingDayWithDuplicateSessionType(final Set<DayOfWeek> repeatDaysToBeAdded,
                                                              final Session existingSession,
                                                              final Session sessionToBeAdded) {
        Set<DayOfWeek> existingRepeatDays = new HashSet<>(existingSession.getRepeatDays());
        return repeatDaysToBeAdded.stream().anyMatch(existingRepeatDays::contains)
                && isSessionTypeDuplicateOrNotValidForAllDay(existingSession, sessionToBeAdded);
    }

    private JsonObject validateSessionToBeAdded(Session sessionToBeAdded) {
        SessionValidationParams params = new SessionValidationParams(
                sessionToBeAdded.getMaxDurationForMorning(),
                sessionToBeAdded.getMaxDurationForAfternoon(),
                sessionToBeAdded.isAllDaySplit(),
                sessionToBeAdded.getSessionType(),
                sessionToBeAdded.getBusinessType(),
                sessionToBeAdded.getSlotsOrDuration(),
                null,
                null,
                null
        );
        return validateSession(params, true);
    }

    JsonObject validateSession(final SessionValidationParams params, final boolean sessionToBeAdded) {
        if (ALL_DAY.equals(params.getSessionType()) && isNull(params.isAllDaySplit())) {
            return buildErrorResponse(ErrorMessages.ALL_DAY_SPLIT_MANDATORY_FOR_AD_SESSION);
        }
        JsonObject scheduleValidationResult = validateExistingSchedule(params);
        if (!scheduleValidationResult.equals(EMPTY_JSON_OBJECT)) {
            return scheduleValidationResult;
        }
        if (TRUE.equals(params.isAllDaySplit())) {
            return validateAllDaySplit(params, sessionToBeAdded);
        } else if (sessionToBeAdded && ObjectUtils.isEmpty(params.getSlotsOrDuration())) {
            return buildErrorResponse(ErrorMessages.DURATION_NOT_FOUND_FOR_REGULAR_SESSION);
        } else if (!sessionToBeAdded) {
            final Map<String, Integer> totalBookedMap = allocatedListingService.getTotalBookedPerCourtScheduleIds(List.of(params.getCourtScheduleId()));
            final int totalBooked = totalBookedMap.getOrDefault(params.getCourtScheduleId(), 0);
            if (params.getSlotsOrDuration() < totalBooked) {
                return buildErrorResponse(ErrorMessages.MAX_DURATION_LESS_THAN_TOTAL_BOOKED);
            }
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateExistingSchedule(SessionValidationParams params) {
        if (isNull(params.getCourtScheduleId())) {
            return EMPTY_JSON_OBJECT;
        }
        List<AllocatedListingEachBooked> allocatedListings = allocatedListingService
                .getAllocatedListingEachBookedByCourtScheduleId(params.getCourtScheduleId());
        if (allocatedListings.isEmpty()) {
            return EMPTY_JSON_OBJECT;
        }

        String[] sessionTimes = retrieveSessionTimes(params);
        if (sessionTimes.length == 0) {
            return EMPTY_JSON_OBJECT;
        }

        return validateHearingTimesAgainstSessionTimes(sessionTimes[0], sessionTimes[1], allocatedListings);
    }

    private String[] retrieveSessionTimes(SessionValidationParams params) {
        String sessionStartTimeStr = params.getSessionStartTime();
        String sessionEndTimeStr = params.getSessionEndTime();

        if (isNull(sessionStartTimeStr) || isNull(sessionEndTimeStr)) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule =
                    courtScheduleRepository.retrieveCourtScheduleWithListingById(params.getCourtScheduleId());
            if (isNull(persistedCourtSchedule)) {
                return new String[0];
            }
            sessionStartTimeStr = retrieveSessionStartTime(sessionStartTimeStr, persistedCourtSchedule);
            sessionEndTimeStr = retrieveSessionEndTime(sessionEndTimeStr, persistedCourtSchedule);
        }

        if (isNull(sessionStartTimeStr) || isNull(sessionEndTimeStr)) {
            return new String[0];
        }

        return new String[]{sessionStartTimeStr, sessionEndTimeStr};
    }

    private String retrieveSessionStartTime(String sessionStartTimeStr,
                                            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        if (isNull(sessionStartTimeStr) && nonNull(persistedCourtSchedule.getSessionStartTime())) {
            return formatSessionTimeForValidation(persistedCourtSchedule.getSessionStartTime());
        }
        return sessionStartTimeStr;
    }

    private String retrieveSessionEndTime(String sessionEndTimeStr,
                                          uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        if (isNull(sessionEndTimeStr) && nonNull(persistedCourtSchedule.getSessionEndTime())) {
            return formatSessionTimeForValidation(persistedCourtSchedule.getSessionEndTime());
        }
        return sessionEndTimeStr;
    }

    private JsonObject validateHearingTimesAgainstSessionTimes(String sessionStartTimeStr, String sessionEndTimeStr,
                                                               List<AllocatedListingEachBooked> allocatedListings) {
        LocalTime sessionStartTime = parse(sessionStartTimeStr, TIME_FORMATTER);
        LocalTime sessionEndTime = parse(sessionEndTimeStr, TIME_FORMATTER);
        LocalTime minHearingTime = getMinHearingTime(allocatedListings);
        if (minHearingTime != null && sessionStartTime.isAfter(minHearingTime)) {
            return buildErrorResponse(ErrorMessages.MIN_HEARING_TIME_AFTER_SESSION_START_TIME);
        }
        LocalTime maxHearingTime = getMaxHearingTime(allocatedListings);
        if (maxHearingTime != null && sessionEndTime.isBefore(maxHearingTime)) {
            return buildErrorResponse(ErrorMessages.MAX_HEARING_TIME_BEFORE_SESSION_END_TIME);
        }
        return EMPTY_JSON_OBJECT;
    }

    private LocalTime getMinHearingTime(List<AllocatedListingEachBooked> allocatedListings) {
        return allocatedListings.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .min(Date::compareTo)
                .map(date -> date.toInstant().atZone(ZoneId.of(EUROPE_LONDON)).toLocalTime())
                .orElse(null);
    }

    private LocalTime getMaxHearingTime(List<AllocatedListingEachBooked> allocatedListings) {
        return allocatedListings.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .max(comparing(Date::getTime))
                .map(date -> date.toInstant().atZone(ZoneId.of(EUROPE_LONDON)).toLocalTime())
                .orElse(null);
    }

    private static String formatSessionTimeForValidation(final Date date) {
        final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone(EUROPE_LONDON));
        return sdf.format(date);
    }

    private boolean isInvalidMaxDuration(SessionValidationParams params) {
        return ObjectUtils.isEmpty(params.getMaxDurationForMorning()) || params.getMaxDurationForMorning() < 0
                || params.getMaxDurationForAfternoon() < 0 || ObjectUtils.isEmpty(params.getMaxDurationForAfternoon());
    }

    private JsonObject validateAllDaySplit(final SessionValidationParams params, final boolean sessionToBeAdded) {
        if (!ALL_DAY.equals(params.getSessionType())) {
            return buildErrorResponse(ErrorMessages.SPLIT_ONLY_APPLIES_AD_SESSIONS);
        }

        if (isInvalidMaxDuration(params)) {
            return buildErrorResponse(ErrorMessages.MAX_DURATION_AM_PM_PROVIDED_FOR_ALL_DAY_SPLIT_SESSION);
        }
        final Optional<BusinessType> businessTypeOptional = referenceDataCache.getRotaBusinessTypeByCode(params.getBusinessType());
        if (businessTypeOptional.isEmpty()) {
            return buildErrorResponse(BUSINESS_TYPE_NOT_FOUND + params.getBusinessType());
        }
        if (!businessTypeOptional.get().isDuration()) {
            return buildErrorResponse(ErrorMessages.SPLIT_ONLY_APPLIES_DURATION_BASED_SESSION);
        }

        if (!sessionToBeAdded) {
            return validateAllDaySplitForUpdate(params);
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateAllDaySplitForUpdate ( final SessionValidationParams params){
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = courtScheduleRepository.retrieveCourtScheduleWithListingById(params.getCourtScheduleId());
        if (nonNull(persistedCourtSchedule)) {
            final List<AllocatedListingEachBooked> allocatedListingEachBookedForThisSchedule = allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(params.getCourtScheduleId());
            final AtomicInteger totalBookedForMorning = new AtomicInteger(0);
            final AtomicInteger totalBookedForAfternoon = new AtomicInteger(0);

            calculateTotalBooked(allocatedListingEachBookedForThisSchedule, persistedCourtSchedule, totalBookedForMorning, totalBookedForAfternoon);

            if (params.getMaxDurationForMorning() < totalBookedForMorning.get()) {
                return buildErrorResponse(ErrorMessages.MAX_DURATION_FOR_MORNING_LESS_THAN_TOTAL_BOOKED_FOR_MORNING);
            }
            if (params.getMaxDurationForAfternoon() < totalBookedForAfternoon.get()) {
                return buildErrorResponse(ErrorMessages.MAX_DURATION_FOR_AFTERNOON_LESS_THAN_TOTAL_BOOKED_FOR_AFTERNOON);
            }
        }
        return EMPTY_JSON_OBJECT;
    }

    private void calculateTotalBooked
            (List < AllocatedListingEachBooked > allocatedListingEachBookedForThisSchedule, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule
                    persistedCourtSchedule, AtomicInteger totalBookedForMorning, AtomicInteger
                     totalBookedForAfternoon){
        allocatedListingEachBookedForThisSchedule.forEach(eachBooked -> {
            if (isMorningSession(eachBooked, persistedCourtSchedule)) {
                updateTotalBooked(eachBooked.getDuration(), totalBookedForMorning, totalBookedForAfternoon, DEFAULT_DURATION);
            } else {
                totalBookedForAfternoon.set(totalBookedForAfternoon.get() + eachBooked.getDuration());
            }
        });
    }

    private boolean isMorningSession (AllocatedListingEachBooked
                                              eachBooked, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule
                                              persistedCourtSchedule){
        return (eachBooked.getHearingStartTime().after(persistedCourtSchedule.getSessionStartTime()) || eachBooked.getHearingStartTime().equals(persistedCourtSchedule.getSessionStartTime())) &&
                eachBooked.getHearingStartTime().before(combineDateAndTime(persistedCourtSchedule.getSessionDate(), DEFAULT_AFTERNOON_START_TIME));
    }

    //this should be called after we have a day match. This is to check if the session type is duplicate or not valid for all day
    private boolean isSessionTypeDuplicateOrNotValidForAllDay ( final Session sessionInList,
                                                                final Session sessionToBeAdded){
        return sessionInList.getSessionType().equals(sessionToBeAdded.getSessionType()) || sessionInList.getSessionType().equals("AD") || sessionToBeAdded.getSessionType().equals("AD");
    }

    private JsonObject getMessageForInvalidDate ( final String value){
        return buildErrorResponse(START_DATE_IS_INVALID + value);
    }

    private JsonObject getMessageForInvalidParameterCombination (
            final RepeatFrequency repeatFrequency){
        String errorMessage = "Invalid combination of parameters: ";
        if (repeatFrequency == EVERY_WEEK) {
            errorMessage += "For More Than once, you should supply a repeat-for and end date ";
        } else if (repeatFrequency == EVERY_MONTH) {
            errorMessage += "For More Than once, you should supply a repeat-for and end date ";
        } else if (repeatFrequency == ONCE) {
            errorMessage += "For Once, you should not supply a repeat-for and end date ";
        }
        return buildErrorResponse(errorMessage);
    }

    private JsonObject buildErrorResponse (String errorMessage){
        return createObjectBuilder()
                .add(ERROR_MESSAGE, errorMessage)
                .build();
    }

    public JsonObject getSessionsUpdateValidation (UpdateCourtSchedule
                                                           updateCourtSchedule){
        String jurisdiction = updateCourtSchedule.getJurisdiction();
        JsonObject jurisdictionValidation = validateUpdateJurisdiction(jurisdiction);
        if (jurisdictionValidation != EMPTY_JSON_OBJECT) {
            return jurisdictionValidation;
        }

        JsonObject courtRoomValidation = validateCourtRoomForJurisdiction(updateCourtSchedule);
        if (!courtRoomValidation.isEmpty()) {
            return courtRoomValidation;
        }

        JsonObject courtHouseValidation = validateCourtRoomBelongsToSameCourtHouse(updateCourtSchedule);
        if (!courtHouseValidation.isEmpty()) {
            return courtHouseValidation;
        }

        JsonObject pastSessionValidation = validateSessionNotInPast(updateCourtSchedule);
        if (!pastSessionValidation.isEmpty()) {
            return pastSessionValidation;
        }

        JsonObject isDraftValidation = validateUpdateIsDraft(updateCourtSchedule, jurisdiction);
        if (isDraftValidation != EMPTY_JSON_OBJECT) {
            return isDraftValidation;
        }

        JsonObject panelValidation = validateUpdatePanel(updateCourtSchedule, jurisdiction);
        if (panelValidation != EMPTY_JSON_OBJECT) {
            return panelValidation;
        }

        SessionValidationParams params = getSessionValidationParams(updateCourtSchedule);
        return validateSession(params, false);
    }

    private JsonObject validateUpdateJurisdiction(String jurisdiction) {
        if (isNull(jurisdiction) || jurisdiction.isEmpty()) {
            return buildErrorResponse("Jurisdiction is mandatory and must be either MAGISTRATES or CROWN");
        }
        if (!MAGISTRATES.equalsIgnoreCase(jurisdiction) && !CROWN.equalsIgnoreCase(jurisdiction)) {
            return buildErrorResponse("Jurisdiction must be either MAGISTRATES or CROWN");
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateUpdateIsDraft(UpdateCourtSchedule updateCourtSchedule, String jurisdiction) {
        Boolean isDraft = updateCourtSchedule.getIsDraft();
        
        JsonObject crownMandatoryCheck = validateCrownIsDraftMandatory(jurisdiction, isDraft);
        if (crownMandatoryCheck != EMPTY_JSON_OBJECT) {
            return crownMandatoryCheck;
        }
        
        JsonObject magistratesIsDraftCheck = validateMagistratesIsDraft(jurisdiction, isDraft);
        if (magistratesIsDraftCheck != EMPTY_JSON_OBJECT) {
            return magistratesIsDraftCheck;
        }

        JsonObject crownDraftChangeCheck = validateCrownDraftStateChange(updateCourtSchedule, jurisdiction, isDraft);
        if (crownDraftChangeCheck != EMPTY_JSON_OBJECT) {
            return crownDraftChangeCheck;
        }

        JsonObject crownDraftWithHearingsCheck = validateCrownDraftWithHearingsBooked(updateCourtSchedule, jurisdiction, isDraft);
        if (crownDraftWithHearingsCheck != EMPTY_JSON_OBJECT) {
            return crownDraftWithHearingsCheck;
        }
        
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateCrownIsDraftMandatory(String jurisdiction, Boolean isDraft) {
        if (CROWN.equalsIgnoreCase(jurisdiction) && isNull(isDraft)) {
            return buildErrorResponse("isDraft is mandatory for CROWN jurisdiction sessions");
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateMagistratesIsDraft(String jurisdiction, Boolean isDraft) {
        if (nonNull(isDraft) && TRUE.equals(isDraft) && MAGISTRATES.equalsIgnoreCase(jurisdiction)) {
            return buildErrorResponse("isDraft can only be true when jurisdiction is CROWN");
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateCrownDraftStateChange(UpdateCourtSchedule updateCourtSchedule, String jurisdiction, Boolean isDraft) {
        if (CROWN.equalsIgnoreCase(jurisdiction) && nonNull(isDraft) && TRUE.equals(isDraft)) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule =
                    courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId());
            if (nonNull(persistedCourtSchedule) && FALSE.equals(persistedCourtSchedule.getIsDraft())) {
                return buildErrorResponse("Cannot change isDraft from false to true for CROWN jurisdiction sessions");
            }
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateCrownDraftWithHearingsBooked(UpdateCourtSchedule updateCourtSchedule, String jurisdiction, Boolean isDraft) {
        if (CROWN.equalsIgnoreCase(jurisdiction) && nonNull(isDraft) && FALSE.equals(isDraft)) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule =
                    courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId());
            if (nonNull(persistedCourtSchedule) && TRUE.equals(persistedCourtSchedule.getIsDraft())) {
                List<AllocatedListingEachBooked> allocatedListings = allocatedListingService
                        .getAllocatedListingEachBookedByCourtScheduleId(updateCourtSchedule.getCourtScheduleId());
                if (!allocatedListings.isEmpty()) {
                    return buildErrorResponse("Cannot assign state to a CROWN draft session with hearings booked");
                }
            }
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateUpdatePanel(UpdateCourtSchedule updateCourtSchedule, String jurisdiction) {
        String panel = updateCourtSchedule.getPanel();
        
        if (MAGISTRATES.equalsIgnoreCase(jurisdiction) && (isNull(panel) || panel.trim().isEmpty())) {
            return buildErrorResponse("panel is mandatory for MAGISTRATES jurisdiction sessions");
        }
        
        if (nonNull(panel) && "YOUTH".equalsIgnoreCase(panel) && CROWN.equalsIgnoreCase(jurisdiction)) {
            return buildErrorResponse("YOUTH panel is not allowed for CROWN jurisdiction sessions. Only ADULT panel is allowed.");
        }
        
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateCourtRoomForJurisdiction(final UpdateCourtSchedule updateCourtSchedule) {
        String courtRoomId = updateCourtSchedule.getCourtRoomId();
        if (isNull(courtRoomId) || courtRoomId.trim().isEmpty()) {
            return buildErrorResponse("Courtroom ID must be provided");
        }

        Optional<CourtRoom> courtRoomOpt = CROWN.equalsIgnoreCase(updateCourtSchedule.getJurisdiction())
                ? referenceDataCache.getCpCourtRoomByCourtRoomId(courtRoomId)
                : referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId);

        if (courtRoomOpt.isEmpty()) {
            return buildErrorResponse("Courtroom selected does not exist in Rota");
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateCourtRoomBelongsToSameCourtHouse(final UpdateCourtSchedule updateCourtSchedule) {
        // Retrieve the persisted court schedule to get the original court house ID
        CourtSchedule persistedCourtSchedule = courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId());
        if (isNull(persistedCourtSchedule)) {
            // If persisted schedule doesn't exist, skip this validation (will be caught by other validations)
            return EMPTY_JSON_OBJECT;
        }

        String originalCourtHouseId = persistedCourtSchedule.getCourtHouseId();
        if (isNull(originalCourtHouseId) || originalCourtHouseId.trim().isEmpty()) {
            // If original court house ID is not available, skip this validation
            return EMPTY_JSON_OBJECT;
        }

        // Check if courtroom ID is being changed
        String newCourtRoomId = updateCourtSchedule.getCourtRoomId();
        String originalCourtRoomId = persistedCourtSchedule.getCourtRoomId();
        if (isNull(newCourtRoomId) || newCourtRoomId.equalsIgnoreCase(originalCourtRoomId)) {
            // If courtroom is not being changed, no need to validate
            return EMPTY_JSON_OBJECT;
        }

        // For CROWN draft sessions with hearings booked, prevent courtroom assignment
        String jurisdiction = updateCourtSchedule.getJurisdiction();
        if (CROWN.equalsIgnoreCase(jurisdiction) && TRUE.equals(persistedCourtSchedule.getIsDraft())) {
            List<AllocatedListingEachBooked> allocatedListings = allocatedListingService
                    .getAllocatedListingEachBookedByCourtScheduleId(updateCourtSchedule.getCourtScheduleId());
            if (!allocatedListings.isEmpty()) {
                return buildErrorResponse("Cannot assign courtroom to a CROWN draft session with hearings booked");
            }
        }

        // Retrieve the new courtroom from reference data
        Optional<CourtRoom> courtRoomOpt = CROWN.equalsIgnoreCase(jurisdiction)
                ? referenceDataCache.getCpCourtRoomByCourtRoomId(newCourtRoomId)
                : referenceDataCache.getRotaCourtRoomByCourtRoomId(newCourtRoomId);

        if (courtRoomOpt.isEmpty()) {
            // Courtroom not found - this will be caught by validateCourtRoomForJurisdiction
            return EMPTY_JSON_OBJECT;
        }

        CourtRoom newCourtRoom = courtRoomOpt.get();
        String newCourtHouseId = newCourtRoom.getOucodeUUID();

        // Validate that the new courtroom belongs to the same court house
        if (isNull(newCourtHouseId) || !newCourtHouseId.equals(originalCourtHouseId)) {
            return buildErrorResponse("Courtroom must belong to the same court house where the session was created. Original court house ID: " + originalCourtHouseId);
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateSessionNotInPast(final UpdateCourtSchedule updateCourtSchedule) {
        // Retrieve the persisted court schedule to get the session date
        CourtSchedule persistedCourtSchedule = courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId());
        if (isNull(persistedCourtSchedule)) {
            // If persisted schedule doesn't exist, skip this validation (will be caught by other validations)
            return EMPTY_JSON_OBJECT;
        }

        LocalDate sessionDate = persistedCourtSchedule.getSessionDate();
        if (isNull(sessionDate)) {
            // If session date is not available, skip this validation
            return EMPTY_JSON_OBJECT;
        }

        // Check if session date is before today
        LocalDate today = LocalDate.now();
        if (sessionDate.isBefore(today)) {
            return buildErrorResponse(SESSION_IN_PAST_CANNOT_BE_EDITED);
        }

        return EMPTY_JSON_OBJECT;
    }

    private static SessionValidationParams getSessionValidationParams(final UpdateCourtSchedule updateCourtSchedule) {
        Integer slotsOrDuration = updateCourtSchedule.getMaxDuration() != null && updateCourtSchedule.getMaxDuration() > 0
                ? updateCourtSchedule.getMaxDuration()
                : updateCourtSchedule.getMaxSlots();

        return new SessionValidationParams(
                updateCourtSchedule.getMaxDurationForMorning(),
                updateCourtSchedule.getMaxDurationForAfternoon(),
                updateCourtSchedule.isAllDaySplit(),
                updateCourtSchedule.getSessionType(),
                updateCourtSchedule.getBusinessType(),
                slotsOrDuration,
                updateCourtSchedule.getCourtScheduleId(),
                updateCourtSchedule.getSessionStartTime(),
                updateCourtSchedule.getSessionEndTime());
    }

    public JsonObject getAssignCourtroomValidation(final uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomRequest request) {
        if (isNull(request.getCourtScheduleIds()) || request.getCourtScheduleIds().isEmpty()) {
            return buildErrorResponse("At least one court schedule ID must be provided");
        }

        if (isNull(request.getCourtRoomId()) || request.getCourtRoomId().trim().isEmpty()) {
            return buildErrorResponse("Courtroom ID must be provided");
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateIsDraftForJurisdiction(final CreateSessionRequestParam createSessionRequestParam) {
        // Validate sessions in the list
        for (Session session : createSessionRequestParam.getSessionList()) {
            JsonObject error = validateSessionIsDraft(session);
            if (error != EMPTY_JSON_OBJECT) return error;
        }

        // Validate sessionToBeAdded if present
        if (nonNull(createSessionRequestParam.getSessionToBeAdded())) {
            JsonObject error = validateSessionIsDraft(createSessionRequestParam.getSessionToBeAdded());
            if (error != EMPTY_JSON_OBJECT) return error;
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateSessionIsDraft(final Session session) {
        if (session == null) {
            return EMPTY_JSON_OBJECT;
        }

        String jurisdiction = nonNull(session.getJurisdiction()) ? session.getJurisdiction() : MAGISTRATES.getJurisdiction();
        Boolean isDraft = session.isDraft();

        // For CROWN jurisdiction, isDraft must be explicitly supplied (true or false)
        if (CROWN.equalsIgnoreCase(jurisdiction) && isNull(isDraft)) {
            return buildErrorResponse("isDraft is mandatory for CROWN jurisdiction sessions");
        }

        // isDraft can only be true for CROWN jurisdiction - reject if true for MAGISTRATES, silently accept false
        if (nonNull(isDraft) && TRUE.equals(isDraft) && MAGISTRATES.equalsIgnoreCase(jurisdiction)) {
            return buildErrorResponse("isDraft can only be true for CROWN jurisdiction sessions");
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validatePanelForJurisdiction(final CreateSessionRequestParam createSessionRequestParam) {
        // Validate sessions in the list
        for (Session session : createSessionRequestParam.getSessionList()) {
            JsonObject error = validateSessionPanel(session);
            if (error != EMPTY_JSON_OBJECT) return error;
        }

        // Validate sessionToBeAdded if present
        if (nonNull(createSessionRequestParam.getSessionToBeAdded())) {
            JsonObject error = validateSessionPanel(createSessionRequestParam.getSessionToBeAdded());
            if (error != EMPTY_JSON_OBJECT) return error;
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateSessionPanel(final Session session) {
        if (session == null) {
            return EMPTY_JSON_OBJECT;
        }

        String jurisdiction = nonNull(session.getJurisdiction()) ? session.getJurisdiction() : MAGISTRATES.getJurisdiction();
        String panel = session.getPanelType();

        // For MAGISTRATES jurisdiction, panel is mandatory
        if (MAGISTRATES.equalsIgnoreCase(jurisdiction)
                && (isNull(panel) || panel.trim().isEmpty())) {
            return buildErrorResponse("panel is mandatory for MAGISTRATES jurisdiction sessions");
        }

        // YOUTH panel is not allowed for CROWN jurisdiction - only ADULT is allowed
        if (nonNull(panel) && "YOUTH".equalsIgnoreCase(panel) && CROWN.equalsIgnoreCase(jurisdiction)) {
            return buildErrorResponse("YOUTH panel is not allowed for CROWN jurisdiction sessions. Only ADULT panel is allowed.");
        }

        return EMPTY_JSON_OBJECT;
    }
}
