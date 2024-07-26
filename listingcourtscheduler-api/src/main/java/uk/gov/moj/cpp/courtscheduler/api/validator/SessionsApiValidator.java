package uk.gov.moj.cpp.courtscheduler.api.validator;

import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.api.CommonUtils.getValidationResult;

import uk.gov.moj.cpp.courtscheduler.api.CommonUtils;
import uk.gov.moj.cpp.courtscheduler.api.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.Session;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionsApiValidator {
    @Inject
    private SessionsService sessionsService;
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionsApiValidator.class.getName());

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public JsonObject getSessionsCreateValidation(final CreateSessionRequestParam createSessionRequestParam) {

        final LocalDate patternStartDate = createSessionRequestParam.getRepeatPattern().getStartDate();
        final LocalDate patternEndDate =  createSessionRequestParam.getRepeatPattern().getEndDate();
        final RepeatFrequency repeatFrequency = createSessionRequestParam.getRepeatPattern().getFrequency();

        LOGGER.info("Validating CREATE Sessions  input : {}", createSessionRequestParam);

        if (patternStartDate.isBefore(ChronoLocalDate.from(LocalDateTime.now()))) {
            return getMessageForInvalidDate(patternStartDate.toString());
        }

        if(repeatFrequency == RepeatFrequency.EVERY_WEEK && patternEndDate == null) {
            return getMessageForInvalidParameterCombination(RepeatFrequency.EVERY_WEEK);
        }
        //if the request is coming from validate endpoint, this object should be populated
        if(Objects.nonNull(createSessionRequestParam.getSessionToBeAdded())){
            final JsonObject addSessionValidationResult = validateAddedSessionPayload(createSessionRequestParam);
            if(addSessionValidationResult != EMPTY_JSON_OBJECT){
                return addSessionValidationResult;
            }
            return sessionsService.validateSessionIntegrity(createSessionRequestParam.getSessionToBeAdded(),patternStartDate,patternEndDate);
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateAddedSessionPayload(final CreateSessionRequestParam createSessionRequestParam) {
        final Set<DayOfWeek> repeatDaysToBeAdded = new HashSet<>(createSessionRequestParam.getSessionToBeAdded().getRepeatDays());
        for(Session session : createSessionRequestParam.getSessionList()) {
            boolean match = session.getCourtCentreId().equals(createSessionRequestParam.getSessionToBeAdded().getCourtCentreId()) &&
                    session.getCourtRoomId().equals(createSessionRequestParam.getSessionToBeAdded().getCourtRoomId()) &&
                    session.getSessionType().equals(createSessionRequestParam.getSessionToBeAdded().getSessionType()) &&
                    session.getBusinessType().equals(createSessionRequestParam.getSessionToBeAdded().getBusinessType());
            if(match){
                Set<DayOfWeek> repeatDays = new HashSet<>(session.getRepeatDays());
                if(repeatDaysToBeAdded.stream().anyMatch(repeatDays::contains)){
                    return getValidationResult("SessionsToBe Added has a duplicate entry within SessionList: CourtCentreId,courtroomId,businessType,SessionType,RepeatDays");
                }
            }
        }

        return EMPTY_JSON_OBJECT;
    }



    private JsonObject getMessageForInvalidDate(final String value) {
        return buildErrorResponse(START_DATE_IS_INVALID + value);
    }

    private JsonObject getMessageForInvalidParameterCombination(final RepeatFrequency repeatFrequency) {
        String errorMessage = "Invalid combination of parameters: ";
        if(repeatFrequency == RepeatFrequency.EVERY_WEEK) {
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
}
