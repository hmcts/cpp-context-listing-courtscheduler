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
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_IN_BAD_FORMAT;

import uk.gov.justice.services.common.converter.LocalDates;
import uk.gov.moj.cpp.courtscheduler.domain.*;

import java.time.format.DateTimeParseException;
import java.util.List;

import javax.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HearingSlotsApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(HearingSlotsApiValidator.class.getName());

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public JsonObject getHearingSlotsValidation(final HearingSlotRequestParam hearingSlotRequestParam) {

        LOGGER.info("Validating GET Hearing Slot input : {}", hearingSlotRequestParam);

        if (StringUtils.isBlank(hearingSlotRequestParam.panel())) {
            return getMessage(RequestParameterConstant.PANEL.getLabel());
        }

        if (StringUtils.isBlank(hearingSlotRequestParam.sessionStartDate())) {
            return getMessage(RequestParameterConstant.SESSION_START_DATE.getLabel());
        } else if (isInvalidDateFormat(hearingSlotRequestParam.sessionStartDate())) {
            return getMessage(format(START_DATE_IS_IN_BAD_FORMAT, hearingSlotRequestParam.sessionStartDate()));
        }

        if (StringUtils.isBlank(hearingSlotRequestParam.sessionEndDate())) {
            return getMessage(RequestParameterConstant.SESSION_END_DATE.getLabel());
        } else if (isInvalidDateFormat(hearingSlotRequestParam.sessionEndDate())) {
            return getMessage(format(END_DATE_IS_IN_BAD_FORMAT, hearingSlotRequestParam.sessionEndDate()));
        }

        if (StringUtils.isBlank(hearingSlotRequestParam.oucodeL2Code()) && StringUtils.isBlank(hearingSlotRequestParam.ouCode())) {
            return getMessage("Either " + RequestParameterConstant.OU_LEVEL2.getLabel() + " or " +
                    RequestParameterConstant.OU_CODE.getLabel() + " should be entered");
        }

        if (StringUtils.isBlank(hearingSlotRequestParam.pageSize())) {
            return getMessage(RequestParameterConstant.PAGE_SIZE.getLabel());
        }

        if (StringUtils.isBlank(hearingSlotRequestParam.pageNumber())) {
            return getMessage(RequestParameterConstant.PAGE_NUMBER.getLabel());
        }
        return EMPTY_JSON_OBJECT;
    }

    public JsonObject listHearingSlotsValidation(final List<HearingSlot> hearingSlots) {

        LOGGER.info("Validating PUT Hearing Slots input : {}", hearingSlots);

        for (int i = 0; i < hearingSlots.size(); i++) {
            HearingSlot hearingSlot = hearingSlots.get(i);

            if (hearingSlot.getHearingId() == null || hearingSlot.getHearingId().isEmpty()) {
                return getMessage(RequestParameterConstant.HEARING_ID.getLabel() +" is missing at index " + i);
            }

            List<CourtScheduleId> schedules = hearingSlot.getCourtScheduleIds();
            if (schedules == null || schedules.isEmpty()) {
                return getMessage(RequestParameterConstant.COURT_SCHEDULES.getLabel()+ " missing for hearing at index " + i);
            }

            for (int j = 0; j < schedules.size(); j++) {
                CourtScheduleId schedule = schedules.get(j);
                if (schedule.getCourtScheduleId() == null || schedule.getCourtScheduleId().isEmpty()) {
                    return getMessage(RequestParameterConstant.COURT_SCHEDULE_ID.getLabel()+" is missing at hearing[" + i + "], courtSchedule[" + j + "]");
                }
            }
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
