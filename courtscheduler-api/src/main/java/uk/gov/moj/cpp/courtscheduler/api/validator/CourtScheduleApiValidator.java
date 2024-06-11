package uk.gov.moj.cpp.courtscheduler.api.validator;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.services.common.converter.LocalDates;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import javax.json.JsonObject;
import java.time.format.DateTimeParseException;

import static java.lang.String.format;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getGlobal;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.*;

public class CourtScheduleApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CourtScheduleApiValidator.class.getName());

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public JsonObject getCourtSchedulesValidation(final CourtScheduleRequestParam courtScheduleRequestParam) {

        LOGGER.info("Validating GET Court Schedule input : {}", courtScheduleRequestParam);

        if (StringUtils.isBlank(courtScheduleRequestParam.courtCentreId())) {
            return getMessage(RequestParameterConstant.COURT_CENTRE.getLabel());
        }

        if (StringUtils.isBlank(courtScheduleRequestParam.courtRoomId())) {
            return getMessage(RequestParameterConstant.COURT_ROOM.getLabel());
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
            LocalDates.from(date);
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
