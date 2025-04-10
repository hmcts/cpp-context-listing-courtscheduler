package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getGlobal;
import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.*;

import uk.gov.justice.services.common.converter.LocalDates;
import uk.gov.moj.cpp.courtscheduler.domain.*;

import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;
import javax.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

public class HearingSlotsApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(HearingSlotsApiValidator.class.getName());
    @Inject
    private CourtScheduleRepository courtScheduleRepository;

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

        LOGGER.info("Validating list Hearing Slots input : {}", hearingSlots);


        for (HearingSlot hearingSlot : hearingSlots) {
            List<CourtScheduleId> schedules = hearingSlot.getCourtScheduleIds();

            for (CourtScheduleId schedule : schedules) {
                uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs = courtScheduleRepository.findBy(schedule.getCourtScheduleId());

                if (isNull(cs)) {
                    return buildErrorResponse("CourSchedule not found id: " + schedule.getCourtScheduleId());
                }

                validateSessionStartTime(schedule, cs);

                if (notValidDuration(schedule, cs))
                    return buildErrorResponse("No duration supplied for CourtSchedule: " + cs.getCourtScheduleId());
            }
        }

        return EMPTY_JSON_OBJECT;
    }

    private static void validateSessionStartTime(CourtScheduleId schedule, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs) {
        if (isNull(schedule.getSessionStartTime())) {
            schedule.setSessionStartTime(cs.getSessionStartTime().toString());
        } else {
            Date hearingStartTime = DateUtils.getDate(schedule.getSessionStartTime());
            if ((hearingStartTime.before(cs.getSessionStartTime()) || hearingStartTime.after(cs.getSessionEndTime()))) {
                schedule.setSessionStartTime(cs.getSessionStartTime().toString());
            }
        }
    }

    private boolean notValidDuration(CourtScheduleId schedule, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs) {
        if (isNull(schedule.getDurationInMinutes())) {
            if (cs.isSlotBased()) {
                schedule.setDurationInMinutes(SLOT_DURATION_DEFAULT);
            } else {
                return true;
            }
        }
        return false;
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
