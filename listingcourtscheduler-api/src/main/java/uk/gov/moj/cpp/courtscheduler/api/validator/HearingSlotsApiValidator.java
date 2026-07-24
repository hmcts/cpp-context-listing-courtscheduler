package uk.gov.moj.cpp.courtscheduler.api.validator;

import org.springframework.stereotype.Service;

import static java.lang.String.format;
import static java.util.Objects.isNull;
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
import uk.gov.moj.cpp.courtscheduler.common.Jurisdiction;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateRequest;
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

    /**
     * Minimum duration (minutes) accepted by {@code courtscheduler.multiday.searchandbook.hearing.slots}.
     * Anything below this is not a true multi-day booking — a single full working day is 360 minutes, so
     * 720 minutes is the smallest span that actually needs two distinct sessions. Requests below this
     * are rejected with HTTP 400 so the single-day endpoint can be used instead.
     */
    static final int MULTIDAY_MIN_DURATION_MINUTES = 720;

    static final String SHOULD_BE_ENTERED = " should be entered";

    static final String MULTIDAY_DURATION_BELOW_MINIMUM =
            "Multi-day search and book requires durationInMinutes >= %d (one full day is %d mins; less than two days is not multi-day). Received: %d";

    static final String COURT_SCHEDULE_ID_CANNOT_BE_NULL_OR_EMPTY =
            "Multi-day search and book: courtScheduleId cannot be null or empty";

    static final String HEARING_ID_CANNOT_BE_NULL_OR_EMPTY =
            "Multi-day search and book: hearingId cannot be null or empty";

    static final String MAGS_COURT_SCHEDULE_ID_NOT_ALLOWED =
            "courtScheduleId is not permitted on mags.search.and.book — Magistrates bookings never anchor on a courtScheduleId";

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

        validateHearingStartTime(hearingSlotRequestParam.hearingStartTime());

        if (!isValidInstant(hearingSlotRequestParam.exactHearingStartDateTime())) {
            return getMessage(format(EXACT_HEARING_START_DATETIME_IS_IN_BAD_FORMAT, hearingSlotRequestParam.exactHearingStartDateTime()));
        }

        if (isBlank(hearingSlotRequestParam.oucodeL2Code()) && isBlank(hearingSlotRequestParam.ouCode())) {
            return getMessage("Either " + RequestParameterConstant.OU_LEVEL2.getLabel() + " or " +
                    RequestParameterConstant.OU_CODE.getLabel() + SHOULD_BE_ENTERED);
        }

        if (isBlank(hearingSlotRequestParam.pageSize())) {
            return getMessage(RequestParameterConstant.PAGE_SIZE.getLabel());
        }

        if (isBlank(hearingSlotRequestParam.pageNumber())) {
            return getMessage(RequestParameterConstant.PAGE_NUMBER.getLabel());
        }

        final JsonObject jurisdictionError = validateJurisdiction(hearingSlotRequestParam.jurisdiction());
        if (jurisdictionError != EMPTY_JSON_OBJECT) {
            return jurisdictionError;
        }

        return EMPTY_JSON_OBJECT;
    }

    private JsonObject validateJurisdiction(final String jurisdiction) {
        if (isNotBlank(jurisdiction)) {
            try {
                Jurisdiction.valueOf(jurisdiction);
            } catch (final IllegalArgumentException e) {
                return buildErrorResponse(format("Invalid jurisdiction value: %s. Must be CROWN or MAGISTRATES", jurisdiction));
            }
        }
        return EMPTY_JSON_OBJECT;
    }

    public JsonObject searchAndBookRequestValidation(final HearingSlotSearchRequest hearingSlotSearchRequest) {

        LOGGER.info("Validating Search and Book Hearing Slot request : {}", hearingSlotSearchRequest);

        if (StringUtils.isBlank(hearingSlotSearchRequest.hearingId())) {
            return getMessage(RequestParameterConstant.HEARING_ID.getLabel());
        }

        if (StringUtils.isBlank(hearingSlotSearchRequest.courtCentreId())) {
            return getMessage(RequestParameterConstant.COURT_CENTRE.getLabel() + SHOULD_BE_ENTERED);
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

    private void validateHearingStartTime(final String hearingStartTime) {
        if (isNotBlank(hearingStartTime)) {
            try {
                ZonedDateTime.parse(hearingStartTime);
            } catch (final DateTimeParseException e) {
                throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, format("Invalid hearingStartTime: %s and exception %s ", hearingStartTime, e.getMessage()));
            }
        }
    }

    private boolean isInvalidDateFormat(final String date) {
        try {
            java.time.LocalDate.parse(date);
        } catch (final DateTimeParseException ignored) {
            LOGGER.debug("Invalid date string for hearing-slots validation (expected for bad input): {}", date);
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
        } catch (final DateTimeParseException ignored) {
            LOGGER.debug("Invalid instant string for hearing-slots validation (expected for bad input): {}", date);
            return false;
        }
        return true;
    }

    private JsonObject getMessage(final String value) {
        return buildErrorResponse(MANDATORY_SEARCH_CRITERIA + value + CANNOT_BE_NULL);
    }

    /**
     * Validates a {@code courtscheduler.multiday.searchandbook.hearing.slots} request. Returns an empty
     * object when valid; returns a {@code {errorMessage: ...}} object otherwise which the handler then
     * wraps in {@link uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException} to surface as
     * HTTP 400 Bad Request.
     *
     * <p>Rules:
     * <ul>
     *   <li>{@code courtScheduleId} must be non-null and non-empty (anchor for the consecutive-day search)</li>
     *   <li>{@code hearingId} must be non-null and non-empty (bookings are keyed on this)</li>
     *   <li>{@code durationInMinutes} must be {@code >= MULTIDAY_MIN_DURATION_MINUTES} (720) — below that
     *       is not a multi-day booking and should use the single-day endpoint instead</li>
     * </ul>
     */
    public JsonObject getMultiDaySearchAndBookValidation(
            final String courtScheduleId,
            final String hearingId,
            final int durationInMinutes) {
        LOGGER.info("Validating multiDaySearchAndBook: courtScheduleId={}, hearingId={}, durationInMinutes={}",
                courtScheduleId, hearingId, durationInMinutes);

        if (isBlank(courtScheduleId)) {
            return buildErrorResponse(COURT_SCHEDULE_ID_CANNOT_BE_NULL_OR_EMPTY);
        }
        if (isBlank(hearingId)) {
            return buildErrorResponse(HEARING_ID_CANNOT_BE_NULL_OR_EMPTY);
        }
        if (durationInMinutes < MULTIDAY_MIN_DURATION_MINUTES) {
            return buildErrorResponse(format(MULTIDAY_DURATION_BELOW_MINIMUM,
                    MULTIDAY_MIN_DURATION_MINUTES,
                    MULTIDAY_MIN_DURATION_MINUTES / 2,
                    durationInMinutes));
        }
        return EMPTY_JSON_OBJECT;
    }

    // ─── SPRDT-1089: validations for the resource-based booking engine (Phase 1) ──
    // Stubs only — Stage 5 implements. HearingSlotsApiValidatorTest defines the contract.

    /**
     * Validates a {@code courtscheduler.crown.search.and.book} request (SPRDT-1089, AC1/AC2/AC3/AC6).
     *
     * <p>{@code hearingId} and {@code hearingDate} are mandatory. {@code courtScheduleId} is an OPTIONAL
     * anchor — its presence (single- or multi-day) is always valid. Returns {@code EMPTY_JSON_OBJECT}
     * when valid; otherwise a {@code {errorMessage: ...}} object the handler surfaces as HTTP 400.</p>
     */
    public JsonObject crownSearchAndBookValidation(final CrownSearchAndBookRequest request) {
        LOGGER.info("Validating crownSearchAndBook: hearingId={}, courtCentreId={}, hearingDate={}, durationInMinutes={}, courtScheduleId={}",
                request.getHearingId(), request.getCourtCentreId(), request.getHearingDate(),
                request.getDurationInMinutes(), request.getCourtScheduleId());

        if (isBlank(request.getHearingId())) {
            return getMessage(RequestParameterConstant.HEARING_ID.getLabel());
        }
        if (request.getHearingDate() == null) {
            return getMessage("hearingDate");
        }
        if (isBlank(request.getCourtCentreId())) {
            return getMessage(RequestParameterConstant.COURT_CENTRE.getLabel() + SHOULD_BE_ENTERED);
        }
        return EMPTY_JSON_OBJECT;
    }

    /**
     * Validates a {@code courtscheduler.mags.search.and.book} request (SPRDT-1089, AC4/AC5).
     *
     * <p>{@code hearingId} and {@code hearingDate} are mandatory. MAGS NEVER anchors, so a supplied
     * {@code courtScheduleId} is rejected. Returns {@code EMPTY_JSON_OBJECT} when valid.</p>
     */
    public JsonObject magsSearchAndBookValidation(final MagsSearchAndBookRequest request) {
        LOGGER.info("Validating magsSearchAndBook: hearingId={}, courtCentreId={}, hearingDate={}, durationInMinutes={}, isPolice={}",
                request.getHearingId(), request.getCourtCentreId(), request.getHearingDate(),
                request.getDurationInMinutes(), request.isPolice());

        if (isBlank(request.getHearingId())) {
            return getMessage(RequestParameterConstant.HEARING_ID.getLabel());
        }
        if (request.getHearingDate() == null) {
            return getMessage("hearingDate");
        }
        if (isBlank(request.getCourtCentreId())) {
            return getMessage(RequestParameterConstant.COURT_CENTRE.getLabel() + SHOULD_BE_ENTERED);
        }
        if (request.hasCourtScheduleId()) {
            return buildErrorResponse(MAGS_COURT_SCHEDULE_ID_NOT_ALLOWED);
        }
        return EMPTY_JSON_OBJECT;
    }

    /**
     * Validates a {@code courtscheduler.move-hearing-to-past-date} request (SPRDT-1089, AC7).
     *
     * <p>{@code hearingId}, {@code jurisdiction} and {@code startDate} are mandatory.
     * {@code courtScheduleId} is an OPTIONAL CROWN anchor. Returns {@code EMPTY_JSON_OBJECT} when valid.
     * The past-only rule is owned by the caller (listing); it is not enforced here.</p>
     */
    public JsonObject moveHearingToPastDateValidation(final MoveHearingToPastDateRequest request) {
        LOGGER.info("Validating moveHearingToPastDate: hearingId={}, courtCentreId={}, jurisdiction={}, startDate={}",
                request.getHearingId(), request.getCourtCentreId(), request.getJurisdiction(), request.getStartDate());

        if (isBlank(request.getHearingId())) {
            return getMessage(RequestParameterConstant.HEARING_ID.getLabel());
        }
        if (isBlank(request.getJurisdiction())) {
            return getMessage("jurisdiction");
        }
        if (request.getStartDate() == null) {
            return getMessage("startDate");
        }
        return EMPTY_JSON_OBJECT;
    }

    private JsonObject buildErrorResponse(String errorMessage) {
        return createObjectBuilder()
                .add(ERROR_MESSAGE, errorMessage)
                .build();
    }
}
