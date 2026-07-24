package uk.gov.moj.cpp.courtscheduler.api.validator;

import org.springframework.stereotype.Service;

import static java.lang.String.format;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getGlobal;
import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.END_DATE_IS_IN_BAD_FORMAT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_AFTER_END_DATE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_IN_BAD_FORMAT;

// (removed) use java.time.LocalDate directly
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import java.time.format.DateTimeParseException;

import jakarta.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CourtScheduleApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleApiValidator.class.getName());

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public JsonObject getCourtSchedulesValidation(final CourtScheduleRequestParam courtScheduleRequestParam) {

        LOGGER.info("Validating GET Court Schedule input : {}", courtScheduleRequestParam);

        if (StringUtils.isBlank(courtScheduleRequestParam.courtCentreId())) {
            return getMessage(RequestParameterConstant.COURT_CENTRE.getLabel());
        }

        if (StringUtils.isBlank(courtScheduleRequestParam.sessionStartDate())) {
            return getMessage(RequestParameterConstant.START_DATE.getLabel());
        } else if (isInvalidDateFormat(courtScheduleRequestParam.sessionStartDate())) {
            return getMessage(format(START_DATE_IS_IN_BAD_FORMAT, courtScheduleRequestParam.sessionStartDate()));
        }

        if (StringUtils.isBlank(courtScheduleRequestParam.sessionEndDate())) {
            return getMessage(RequestParameterConstant.END_DATE.getLabel());
        } else if (isInvalidDateFormat(courtScheduleRequestParam.sessionEndDate())) {
            return getMessage(format(END_DATE_IS_IN_BAD_FORMAT, courtScheduleRequestParam.sessionEndDate()));
        }

        // Validate startDate <= endDate
        try {
            final var start = java.time.LocalDate.parse(courtScheduleRequestParam.sessionStartDate());
            final var end = java.time.LocalDate.parse(courtScheduleRequestParam.sessionEndDate());
            if (end.isBefore(start)) {
                return buildErrorResponse(START_DATE_AFTER_END_DATE);
            }
        } catch (DateTimeParseException e) {
            return buildErrorResponse(format(START_DATE_IS_IN_BAD_FORMAT, courtScheduleRequestParam.sessionStartDate()));
        }


        if (StringUtils.isBlank(courtScheduleRequestParam.pageSize())) {
            return getMessage(RequestParameterConstant.PAGE_SIZE.getLabel());
        }

        if (StringUtils.isBlank(courtScheduleRequestParam.pageNumber())) {
            return getMessage(RequestParameterConstant.PAGE_NUMBER.getLabel());
        }
        return EMPTY_JSON_OBJECT;
    }

    private boolean isInvalidDateFormat(final String date) {
        try {
            java.time.LocalDate.parse(date);
        } catch (final DateTimeParseException e) {
            getGlobal().log(WARNING, format("Invalid Date supplied: %s and exception", date), e);
            return true;
        }
        return false;
    }

    private JsonObject getMessage(final String value) {
        return buildErrorResponse(MANDATORY_SEARCH_CRITERIA + value + CANNOT_BE_NULL);
    }

    private JsonObject buildErrorResponse(String errorMessage) {
        return createObjectBuilder()
                .add(ERROR_MESSAGE, errorMessage)
                .build();
    }
}
