package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.lang.String.format;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getGlobal;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.END_DATE_IS_IN_BAD_FORMAT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_INVALID;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_IN_BAD_FORMAT;

import uk.gov.justice.services.common.converter.LocalDates;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.format.DateTimeParseException;

import javax.json.JsonObject;

import jdk.jfr.Frequency;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionsApiValidator {
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
