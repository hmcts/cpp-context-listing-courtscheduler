package uk.gov.moj.cpp.courtscheduler.api.validator;

import org.springframework.stereotype.Service;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getGlobal;
import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.END_DATE_IS_IN_BAD_FORMAT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.EXACT_HEARING_START_DATETIME_IS_IN_BAD_FORMAT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_AFTER_END_DATE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_IN_BAD_FORMAT;

import org.springframework.web.server.ResponseStatusException;
// (removed) use java.time.LocalDate directly
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class HearingSlotsApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(HearingSlotsApiValidator.class.getName());
    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @SuppressWarnings("squid:MethodCyclomaticComplexity")
    public JsonObject getHearingSlotsValidation(final HearingSlotRequestParam hearingSlotRequestParam) {

        LOGGER.info("Validating GET Hearing Slot input : {}", hearingSlotRequestParam);

        if (isBlank(hearingSlotRequestParam.panel())) {
            return getMessage(RequestParameterConstant.PANEL.getLabel());
        }

        if (isBlank(hearingSlotRequestParam.sessionStartDate())) {
            return getMessage(RequestParameterConstant.SESSION_START_DATE.getLabel());
        } else if (isInvalidDateFormat(hearingSlotRequestParam.sessionStartDate())) {
            return getMessage(format(START_DATE_IS_IN_BAD_FORMAT, hearingSlotRequestParam.sessionStartDate()));
        }

        if (isBlank(hearingSlotRequestParam.sessionEndDate())) {
            return getMessage(RequestParameterConstant.SESSION_END_DATE.getLabel());
        } else if (isInvalidDateFormat(hearingSlotRequestParam.sessionEndDate())) {
            return getMessage(format(END_DATE_IS_IN_BAD_FORMAT, hearingSlotRequestParam.sessionEndDate()));
        }

        // Validate startDate <= endDate
        final var start = java.time.LocalDate.parse(hearingSlotRequestParam.sessionStartDate());
        final var end = java.time.LocalDate.parse(hearingSlotRequestParam.sessionEndDate());
        if (end.isBefore(start)) {
            return buildErrorResponse(START_DATE_AFTER_END_DATE);
        }

        if (isNotBlank(hearingSlotRequestParam.hearingStartTime())) {
            try {
                ZonedDateTime.parse(hearingSlotRequestParam.hearingStartTime());
            } catch (final DateTimeParseException e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, format("Invalid hearingStartTime: %s and exception %s ", hearingSlotRequestParam.hearingStartTime(),e.getMessage()));
            }
        }

        if (!isValidInstant(hearingSlotRequestParam.exactHearingStartDateTime())) {
            return getMessage(format(EXACT_HEARING_START_DATETIME_IS_IN_BAD_FORMAT, hearingSlotRequestParam.exactHearingStartDateTime()));
        }

        if (isBlank(hearingSlotRequestParam.oucodeL2Code()) && isBlank(hearingSlotRequestParam.ouCode())) {
            return getMessage("Either " + RequestParameterConstant.OU_LEVEL2.getLabel() + " or " +
                    RequestParameterConstant.OU_CODE.getLabel() + " should be entered");
        }

        if (isBlank(hearingSlotRequestParam.pageSize())) {
            return getMessage(RequestParameterConstant.PAGE_SIZE.getLabel());
        }

        if (isBlank(hearingSlotRequestParam.pageNumber())) {
            return getMessage(RequestParameterConstant.PAGE_NUMBER.getLabel());
        }
        return EMPTY_JSON_OBJECT;
    }

    public JsonObject searchAndBookRequestValidation(final HearingSlotSearchRequest hearingSlotSearchRequest) {

        LOGGER.info("Validating Search and Book Hearing Slot request : {}", hearingSlotSearchRequest);

        if (StringUtils.isBlank(hearingSlotSearchRequest.hearingId())) {
            return getMessage(RequestParameterConstant.HEARING_ID.getLabel());
        }

        if (StringUtils.isBlank(hearingSlotSearchRequest.courtCentreId())) {
            return getMessage(RequestParameterConstant.COURT_CENTRE.getLabel() + " should be entered");
        }

        if (StringUtils.isBlank(hearingSlotSearchRequest.hearingSessionDate())) {
            return getMessage(RequestParameterConstant.HEARING_SESSION_DATE.getLabel());
        } else if (isInvalidDateFormat(hearingSlotSearchRequest.hearingSessionDate())) {
            return getMessage(format(START_DATE_IS_IN_BAD_FORMAT, hearingSlotSearchRequest.hearingSessionDate()));
        }

        return EMPTY_JSON_OBJECT;
    }

    public JsonObject listHearingSlotsValidation(final List<HearingSlot> hearingSlots) {

        LOGGER.info("Validating list Hearing Slots input : {}", hearingSlots);

        for (HearingSlot hearingSlot : hearingSlots) {
            List<RequestedCourtSchedule> schedules = hearingSlot.getCourtScheduleIds();

            for (RequestedCourtSchedule requestedCourtSchedule : schedules) {
                uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule cs = courtScheduleRepository.findBy(requestedCourtSchedule.getCourtScheduleId());

                if (isNull(cs)) {
                    return buildErrorResponse("Requested CourSchedule not found. Id: " + requestedCourtSchedule.getCourtScheduleId());
                }

                if (invalidDuration(requestedCourtSchedule, cs))
                    return buildErrorResponse("No duration supplied for requested CourtSchedule: " + requestedCourtSchedule.getCourtScheduleId());
            }
        }

        return EMPTY_JSON_OBJECT;
    }


    private boolean invalidDuration(RequestedCourtSchedule schedule, CourtSchedule cs) {
        return !cs.isSlotBased() && isNull(schedule.getDurationInMinutes());
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

    private boolean isValidInstant(final String date) {
        if(date == null){
            return true;
        }
        try {
            Instant.parse(date);
        } catch (final DateTimeParseException e) {
            getGlobal().log(WARNING, format("Invalid Date supplied: %s and exception", date), e);
            return false;
        }
        return true;
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
