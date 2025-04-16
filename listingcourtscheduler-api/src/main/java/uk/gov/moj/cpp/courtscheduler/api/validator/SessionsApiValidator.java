package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.AM_SESSION_END_TIME_CANNOT_EXCEED;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.BUSINESS_TYPE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.PM_SESSION_START_TIME_CANNOT_BE_EARLIER;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_START_TIME_CANNOT_BE_LATER_THAN_END_TIME;
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
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

        if (patternStartDate.isBefore(ChronoLocalDate.from(LocalDateTime.now()))) {
            LOGGER.debug("getSessionsCreateValidation patternStartDate isBefore");
            return getMessageForInvalidDate(patternStartDate.toString());
        }

        if(repeatFrequency == RepeatFrequency.EVERY_WEEK && patternEndDate == null) {
            LOGGER.debug("getSessionsCreateValidation repeatFrequency EVERY_WEEK and patternEndDate null");
            return getMessageForInvalidParameterCombination(RepeatFrequency.EVERY_WEEK);
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

    private JsonObject validateSessionStartEndTime(final List<Session> sessionList) {
        return sessionList.stream()
                .filter(session -> session.getSessionStartTime() != null && session.getSessionEndTime() != null)
                .map(session -> {
                    try {
                        LocalTime sessionStartTime = LocalTime.parse(session.getSessionStartTime(), TIME_FORMATTER);
                        LocalTime sessionEndTime = LocalTime.parse(session.getSessionEndTime(), TIME_FORMATTER);

                        if (sessionStartTime.isAfter(sessionEndTime)) {
                            return buildErrorResponse(SESSION_START_TIME_CANNOT_BE_LATER_THAN_END_TIME);
                        }

                        if (session.getSessionType().equals(PM_SESSION) && sessionStartTime.isBefore(LocalTime.of(14, 0))) {
                            return buildErrorResponse(PM_SESSION_START_TIME_CANNOT_BE_EARLIER);
                        }
                        if (session.getSessionType().equals(AM_SESSION) && sessionEndTime.isAfter(LocalTime.of(13, 0))) {
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
                null
        );
        return validateSession(params, true, requester);
    }

    JsonObject validateSession(final SessionValidationParams params, final boolean sessionToBeAdded, final Requester requester) {
        if (ALL_DAY.equals(params.getSessionType()) && isNull(params.isAllDaySplit())) {
            return buildErrorResponse(ErrorMessages.ALL_DAY_SPLIT_MANDATORY_FOR_AD_SESSION);
        }
        if (Boolean.TRUE.equals(params.isAllDaySplit())) {
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

    private JsonObject validateAllDaySplit(final SessionValidationParams params, final boolean sessionToBeAdded, final Requester requester) {
        if (!ALL_DAY.equals(params.getSessionType())) {
            return buildErrorResponse(ErrorMessages.SPLIT_ONLY_APPLIES_AD_SESSIONS);
        }
        if (ObjectUtils.isEmpty(params.getMaxDurationForMorning()) || params.getMaxDurationForMorning() < 0
                || params.getMaxDurationForAfternoon() < 0 || ObjectUtils.isEmpty(params.getMaxDurationForAfternoon())) {
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

    private JsonObject validateAllDaySplitForUpdate(final SessionValidationParams params) {
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

    private void calculateTotalBooked(List<AllocatedListingEachBooked> allocatedListingEachBookedForThisSchedule, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule, AtomicInteger totalBookedForMorning, AtomicInteger totalBookedForAfternoon) {
        allocatedListingEachBookedForThisSchedule.forEach(eachBooked -> {
            if (isMorningSession(eachBooked, persistedCourtSchedule)) {
                updateTotalBooked(eachBooked.getDuration(), totalBookedForMorning, totalBookedForAfternoon, DEFAULT_DURATION);
            } else {
                totalBookedForAfternoon.set(totalBookedForAfternoon.get() + eachBooked.getDuration());
            }
        });
    }

    private boolean isMorningSession(AllocatedListingEachBooked eachBooked, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        return (eachBooked.getHearingStartTime().after(persistedCourtSchedule.getSessionStartTime()) || eachBooked.getHearingStartTime().equals(persistedCourtSchedule.getSessionStartTime())) &&
                eachBooked.getHearingStartTime().before(combineDateAndTime(persistedCourtSchedule.getSessionDate(), DEFAULT_AFTERNOON_START_TIME));
    }

    //this should be called after we have a day match. This is to check if the session type is duplicate or not valid for all day
    private boolean isSessionTypeDuplicateOrNotValidForAllDay(final Session sessionInList, final Session sessionToBeAdded) {
        return sessionInList.getSessionType().equals(sessionToBeAdded.getSessionType()) || sessionInList.getSessionType().equals("AD") || sessionToBeAdded.getSessionType().equals("AD");
    }

    private JsonObject getMessageForInvalidDate(final String value) {
        return buildErrorResponse(START_DATE_IS_INVALID + value);
    }

    private JsonObject getMessageForInvalidParameterCombination(final RepeatFrequency repeatFrequency) {
        String errorMessage = "Invalid combination of parameters: ";
        if (repeatFrequency == RepeatFrequency.EVERY_WEEK) {
            errorMessage += "For More Than once, you should supply a repeat-for and end date ";
        } else if (repeatFrequency == RepeatFrequency.ONCE) {
            errorMessage += "For Once, you should not supply a repeat-for and end date ";
        }
        return buildErrorResponse(errorMessage);
    }

    private JsonObject buildErrorResponse(String errorMessage) {
        return createObjectBuilder()
                .add(ERROR_MESSAGE, errorMessage)
                .build();
    }

    public JsonObject getSessionsUpdateValidation(UpdateCourtSchedule updateCourtSchedule, Requester requester) {
        Integer slotsOrDuration = updateCourtSchedule.getMaxDuration() != null && updateCourtSchedule.getMaxDuration() > 0
                ? updateCourtSchedule.getMaxDuration()
                : updateCourtSchedule.getMaxSlots();

        SessionValidationParams params = new SessionValidationParams(
                updateCourtSchedule.getMaxDurationForMorning(),
                updateCourtSchedule.getMaxDurationForAfternoon(),
                updateCourtSchedule.isAllDaySplit(),
                updateCourtSchedule.getSessionType(),
                updateCourtSchedule.getBusinessType(),
                slotsOrDuration,
                updateCourtSchedule.getCourtScheduleId());
        return validateSession(params, false, requester);
    }
}
