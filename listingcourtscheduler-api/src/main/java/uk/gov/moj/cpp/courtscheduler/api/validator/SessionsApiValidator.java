package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.LocalTime.of;
import static java.time.LocalTime.parse;
import static java.time.ZoneId.of;
import static java.time.ZoneId.systemDefault;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Comparator.comparing;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toSet;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.AM_SESSION_END_TIME_CANNOT_EXCEED;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.BUSINESS_TYPE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.PM_SESSION_START_TIME_CANNOT_BE_EARLIER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_END_TIME_CANNOT_BE_LATER;
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

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionValidationParams;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionsApiValidator {

    private static final DateTimeFormatter TIME_FORMATTER = ofPattern("HH:mm");
    public static final int DEFAULT_DURATION = 180;
    public static final String CROWN = "CROWN";
    public static final String MAGISTRATES = "MAGISTRATES";

    @Inject
    private SessionsService sessionsService;
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionsApiValidator.class.getName());

    @Inject
    private ReferenceDataCache referenceDataCache;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private AllocatedListingService allocatedListingService;

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public JsonObject getSessionsCreateValidation(final CreateSessionRequestParam createSessionRequestParam, final Requester requester) {

        final LocalDate patternStartDate = createSessionRequestParam.getRepeatPattern().getStartDate();
        final LocalDate patternEndDate = createSessionRequestParam.getRepeatPattern().getEndDate();
        final RepeatFrequency repeatFrequency = createSessionRequestParam.getRepeatPattern().getFrequency();

        LOGGER.info("Validating CREATE Sessions input : {}", createSessionRequestParam);

        if (repeatFrequency == EVERY_MONTH) {
            JsonObject err = validateMonthlyCrownIndexForRequest(createSessionRequestParam);
            if (err != EMPTY_JSON_OBJECT) return err;
        }

        if (patternStartDate.isBefore(ChronoLocalDate.from(LocalDateTime.now()))) {
            LOGGER.debug("getSessionsCreateValidation patternStartDate isBefore");
            return getMessageForInvalidDate(patternStartDate.toString());
        }

        if(repeatFrequency == EVERY_WEEK && patternEndDate == null) {
            LOGGER.debug("getSessionsCreateValidation repeatFrequency EVERY_WEEK and patternEndDate null");
            return getMessageForInvalidParameterCombination(EVERY_WEEK);
        }

        final JsonObject result = validateSessionStartEndTime(createSessionRequestParam.getSessionList());
        if (result != null) {
            return result;
        }

        //if the request is coming from validate endpoint, this object should be populated
        if(Objects.nonNull(createSessionRequestParam.getSessionToBeAdded())){
            LOGGER.debug("getSessionsCreateValidation getSessionToBeAdded not null");
            final JsonObject addSessionValidationResult = validateAddedSessionPayload(createSessionRequestParam,requester);
            if(addSessionValidationResult != EMPTY_JSON_OBJECT){
                return addSessionValidationResult;
            }
            LOGGER.debug("getSessionsCreateValidation addSessionValidationResult is empty");
            return sessionsService.validateSessionIntegrity(createSessionRequestParam.getSessionToBeAdded(),patternStartDate,patternEndDate, createSessionRequestParam.getRepeatPattern().getRepeatFor());
        }
        return EMPTY_JSON_OBJECT;
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

    public JsonObject getSessionsAvailabilityValidation(final ValidateSessionAvailabilityRequestParam validateSessionAvailabilityRequestParam) {

        final List<String> courtScheduleIds = validateSessionAvailabilityRequestParam.getCourtScheduleIds();
        final Integer requestedDuration = validateSessionAvailabilityRequestParam.getSlotsOrDuration();

        if (courtScheduleIds.isEmpty()) {
            return buildErrorResponse("Court Schedule Ids cannot be empty");
        }

        List<CourtSchedule> courtSchedules = courtScheduleRepository.findByCourtScheduleIds(courtScheduleIds);
        boolean firstIsSlotBased = courtSchedules.get(0).isSlotBased();

        JsonObject missingIdError = validateMissingCourtScheduleIds(courtScheduleIds, courtSchedules);
        if (missingIdError != EMPTY_JSON_OBJECT) return missingIdError;

        JsonObject typeConsistencyError = validateScheduleTypeConsistency(courtSchedules, firstIsSlotBased);
        if (typeConsistencyError != EMPTY_JSON_OBJECT) return typeConsistencyError;

        if (firstIsSlotBased) {
            return validateSlotBasedAvailability(courtSchedules, courtScheduleIds);
        } else {
            return validateDurationBasedAvailability(courtSchedules, requestedDuration);
        }
    }

    private JsonObject validateMissingCourtScheduleIds(List<String> courtScheduleIds, List<CourtSchedule> courtSchedules) {
        Set<String> retrievedIds = courtSchedules.stream()
                .map(CourtSchedule::getCourtScheduleId)
                .collect(toSet());
        List<String> missingIds = courtScheduleIds.stream()
                .filter(id -> !retrievedIds.contains(id))
                .toList();
        return missingIds.isEmpty() ? EMPTY_JSON_OBJECT : buildErrorResponse("Court Schedule Ids not found: " + missingIds);
    }

    private JsonObject validateScheduleTypeConsistency(List<CourtSchedule> courtSchedules, boolean firstIsSlotBased) {
        boolean allSameType = courtSchedules.stream()
                .allMatch(schedule -> schedule.isSlotBased() == firstIsSlotBased);
        return allSameType ? EMPTY_JSON_OBJECT : buildErrorResponse("All court schedules should be either slot-based or duration-based");
    }

    private JsonObject validateSlotBasedAvailability(List<CourtSchedule> courtSchedules, List<String> courtScheduleIds) {
        Map<String, Integer> allocatedListingsMapByCourtScheduleId = allocatedListingService.getAllocatedListingsByCourtScheduleId(courtScheduleIds);
        for (Map.Entry<String, Integer> entry : allocatedListingsMapByCourtScheduleId.entrySet()) {
            String courtScheduleId = entry.getKey();
            int totalBooked = entry.getValue();
            int maxSlots = courtSchedules.stream()
                    .filter(schedule -> schedule.getCourtScheduleId().equals(courtScheduleId))
                    .findFirst()
                    .map(CourtSchedule::getMaxSlots)
                    .orElse(0);
            if (totalBooked >= maxSlots) {
                return buildErrorResponse("Court Schedule Id: " + courtScheduleId + " is fully booked");
            }
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateDurationBasedAvailability(List<CourtSchedule> courtSchedules, Integer requestedDuration) {
        if (requestedDuration == null || requestedDuration < 1) {
            return buildErrorResponse("Duration is mandatory and should be greater than 0");
        }
        boolean allHaveSufficientDuration = courtSchedules.stream()
                .allMatch(schedule -> schedule.getAvailableDuration() >= requestedDuration);
        if (!allHaveSufficientDuration) {
            return buildErrorResponse("Not enough available durations for all court schedules");
        }

        for (CourtSchedule cs : courtSchedules) {
            if (TRUE.equals(cs.getSupportAdSplit())) {
                final AtomicInteger totalBookedForMorning = new AtomicInteger(0);
                final AtomicInteger totalBookedForAfternoon = new AtomicInteger(0);
                final List<AllocatedListingEachBooked> allocatedListingEachBookedForThisSchedule = allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(cs.getCourtScheduleId());
                calculateTotalBooked(allocatedListingEachBookedForThisSchedule, cs, totalBookedForMorning, totalBookedForAfternoon);

                int morningAvailability = cs.getMaxAdMorningDuration() - totalBookedForMorning.get();
                int afternoonAvailability = cs.getMaxAdAfternoonDuration() - totalBookedForAfternoon.get();
                if (morningAvailability <= 0 || afternoonAvailability <= 0) {
                    return buildErrorResponse("Requested duration must fit within either the morning or afternoon session for all-day split schedules.");
                }
            }
        }
        return EMPTY_JSON_OBJECT;
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

    private JsonObject validateAddedSessionPayload(final CreateSessionRequestParam createSessionRequestParam, final Requester requester) {
        final Session sessionToBeAdded = createSessionRequestParam.getSessionToBeAdded();
        final Set<DayOfWeek> repeatDaysToBeAdded = new HashSet<>(sessionToBeAdded.getRepeatDays());
        for(Session session : createSessionRequestParam.getSessionList()) {
            LOGGER.info("getSessionsCreateValidation getSessionList not null");
            boolean match = session.getCourtCentreId().equals(sessionToBeAdded.getCourtCentreId()) &&
                    session.getCourtRoomId().equals(sessionToBeAdded.getCourtRoomId()) &&
                    session.getBusinessType().equals(sessionToBeAdded.getBusinessType());
            LOGGER.info("getSessionsCreateValidation match value : {}", match);
            if(match){
                Set<DayOfWeek> repeatDays = new HashSet<>(session.getRepeatDays());
                if(repeatDaysToBeAdded.stream().anyMatch(repeatDays::contains) && isSessionTypeDuplicateOrNotValidForAllDay(session,sessionToBeAdded)) {
                    LOGGER.info("getSessionsCreateValidation DUPLICATE_SESSIONS");
                    return buildErrorResponse(ErrorMessages.DUPLICATE_SESSIONS);
                }
            }
        }
        return validateSessionToBeAdded(sessionToBeAdded, requester);
    }

    private JsonObject validateSessionToBeAdded(Session sessionToBeAdded, Requester requester) {
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
        return validateSession(params, true, requester);
    }

    JsonObject validateSession(final SessionValidationParams params, final boolean sessionToBeAdded, final Requester requester) {
        if (ALL_DAY.equals(params.getSessionType()) && isNull(params.isAllDaySplit())) {
            return buildErrorResponse(ErrorMessages.ALL_DAY_SPLIT_MANDATORY_FOR_AD_SESSION);
        }
        JsonObject scheduleValidationResult = validateExistingSchedule(params);
        if (!scheduleValidationResult.equals(EMPTY_JSON_OBJECT)) {
            return scheduleValidationResult;
        }
        if (TRUE.equals(params.isAllDaySplit())) {
            return validateAllDaySplit(params, sessionToBeAdded, requester);
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
        LocalTime sessionStartTime = parse(params.getSessionStartTime(), TIME_FORMATTER);
        LocalTime sessionEndTime = parse(params.getSessionEndTime(), TIME_FORMATTER);
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
                .map(date -> date.toInstant().atZone(of("Europe/London")).toLocalTime())
                .orElse(null);
    }

    private LocalTime getMaxHearingTime(List<AllocatedListingEachBooked> allocatedListings) {
        return allocatedListings.stream()
                .map(AllocatedListingEachBooked::getHearingStartTime)
                .max(comparing(Date::getTime))
                .map(date -> date.toInstant().atZone(systemDefault()).toLocalTime())
                .orElse(null);
    }

    private boolean isInvalidMaxDuration(SessionValidationParams params) {
        return ObjectUtils.isEmpty(params.getMaxDurationForMorning()) || params.getMaxDurationForMorning() < 0
                || params.getMaxDurationForAfternoon() < 0 || ObjectUtils.isEmpty(params.getMaxDurationForAfternoon());
    }

    private JsonObject validateAllDaySplit(final SessionValidationParams params, final boolean sessionToBeAdded, final Requester requester) {
        if (!ALL_DAY.equals(params.getSessionType())) {
            return buildErrorResponse(ErrorMessages.SPLIT_ONLY_APPLIES_AD_SESSIONS);
        }

        if (isInvalidMaxDuration(params)) {
            return buildErrorResponse(ErrorMessages.MAX_DURATION_AM_PM_PROVIDED_FOR_ALL_DAY_SPLIT_SESSION);
        }
        final Optional<BusinessType> businessTypeOptional = referenceDataCache.getRotaBusinessTypeByCode(params.getBusinessType(), requester);
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
                                                           updateCourtSchedule, Requester requester){
        // Validate jurisdiction
        String jurisdiction = updateCourtSchedule.getJurisdiction();
        if (isNull(jurisdiction) || jurisdiction.isEmpty()) {
            return buildErrorResponse("Jurisdiction is mandatory and must be either MAGISTRATES or CROWN");
        }
        if (!MAGISTRATES.equalsIgnoreCase(jurisdiction) && !CROWN.equalsIgnoreCase(jurisdiction)) {
            return buildErrorResponse("Jurisdiction must be either MAGISTRATES or CROWN");
        }

        // Validate is_draft can only be supplied when jurisdiction is CROWN
        Boolean isDraft = updateCourtSchedule.getIsDraft();
        if (nonNull(isDraft) && MAGISTRATES.equalsIgnoreCase(jurisdiction)) {
            return buildErrorResponse("is_draft can only be supplied when jurisdiction is CROWN");
        }

        // Validate that if CROWN and database is_draft = false, it can't be changed to is_draft = true
        if (CROWN.equalsIgnoreCase(jurisdiction) && nonNull(isDraft) && TRUE.equals(isDraft)) {
            uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule =
                    courtScheduleRepository.retrieveCourtScheduleWithListingById(updateCourtSchedule.getCourtScheduleId());
            if (nonNull(persistedCourtSchedule) && FALSE.equals(persistedCourtSchedule.getIsDraft())) {
                return buildErrorResponse("Cannot change is_draft from false to true for CROWN jurisdiction sessions");
            }
        }

        SessionValidationParams params = getSessionValidationParams(updateCourtSchedule);
        return validateSession(params, false, requester);
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
}
