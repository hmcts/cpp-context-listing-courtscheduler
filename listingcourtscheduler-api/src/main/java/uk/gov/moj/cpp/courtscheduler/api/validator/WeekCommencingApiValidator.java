package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_IN_PAST_CANNOT_BE_EDITED;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.SESSION_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_COURTROOM;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_MONDAY;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_SESSION_NOT_WC;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.openapi.model.BusinessType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingUpdateRequest;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;
import java.util.Optional;

import jakarta.json.JsonObject;

import org.springframework.stereotype.Service;

@Service
public class WeekCommencingApiValidator {

    private final SessionsApiValidator sessionsApiValidator;
    private final ReferenceDataCache referenceDataCache;
    private final SessionsService sessionsService;

    public WeekCommencingApiValidator(final SessionsApiValidator sessionsApiValidator,
                                      final ReferenceDataCache referenceDataCache,
                                      final SessionsService sessionsService) {
        this.sessionsApiValidator = sessionsApiValidator;
        this.referenceDataCache = referenceDataCache;
        this.sessionsService = sessionsService;
    }

    /**
     * Runs before the request is enriched: rejects a range in which a Monday already has a week
     * commencing session for the same court centre and business type.
     */
    public JsonObject getWeekCommencingDuplicateValidation(final WeekCommencingCreateRequest request) {
        final Optional<LocalDate> startDate = parseDate(request.getStartDate());
        final Optional<LocalDate> endDate = parseDate(request.getEndDate());
        if (startDate.isEmpty() || endDate.isEmpty()) {
            // reported by the standard create validation
            return EMPTY_JSON_OBJECT;
        }
        return sessionsService.validateWeekCommencingIntegrity(
                request.getCourtCentreId(), request.getBusinessType(), startDate.get(), endDate.get());
    }

    /**
     * Validates a week commencing request and the create payload it was enriched into. Returns the
     * first error found, or an empty object when the request is valid.
     */
    public JsonObject getWeekCommencingCreateValidation(final WeekCommencingCreateRequest request,
                                                        final CreateSessionRequestParam createRequest) {
        if (isNull(createRequest.getSessions().getFirst().getCourtRoomId())) {
            return buildErrorResponse(WEEK_COMMENCING_NO_COURTROOM + request.getCourtCentreId());
        }

        final JsonObject createValidation = sessionsApiValidator.getSessionsCreateValidation(createRequest);
        if (!createValidation.isEmpty()) {
            return createValidation;
        }

        if (isSlotBased(request.getBusinessType())) {
            return buildErrorResponse(WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED);
        }

        // the standard create validation has already rejected unparseable dates and an end before the start
        final LocalDate startDate = DateUtils.parseRepeatPatternDate(request.getStartDate());
        final LocalDate endDate = DateUtils.parseRepeatPatternDate(request.getEndDate());
        if (startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).isAfter(endDate)) {
            return buildErrorResponse(WEEK_COMMENCING_NO_MONDAY);
        }

        return EMPTY_JSON_OBJECT;
    }

    /**
     * Validates that the session exists, is a week commencing session, uses a duration-based
     * business type, and — when the business type is changing — that no other week commencing
     * session already exists for the same court centre and new business type on the same Monday.
     * The caller is responsible for loading {@code persisted} via
     * {@link uk.gov.moj.cpp.courtscheduler.api.service.WeekCommencingSessionService#loadPersistedSession}
     * so the row is fetched only once per request.
     */
    public JsonObject getWeekCommencingUpdateValidation(final WeekCommencingUpdateRequest request,
                                                        final CourtSchedule persisted) {
        if (isNull(persisted)) {
            return buildErrorResponse(SESSION_NOT_FOUND);
        }
        if (!TRUE.equals(persisted.isWeekCommencing())) {
            return buildErrorResponse(WEEK_COMMENCING_SESSION_NOT_WC);
        }
        final LocalDate weekStart = LocalDate.now(TimezoneUtils.LONDON_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (persisted.getSessionDate() != null && persisted.getSessionDate().isBefore(weekStart)) {
            return buildErrorResponse(SESSION_IN_PAST_CANNOT_BE_EDITED);
        }
        if (isSlotBased(request.getBusinessType())) {
            return buildErrorResponse(WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED);
        }
        if (!Objects.equals(persisted.getBusinessType(), request.getBusinessType())) {
            final JsonObject duplicate = sessionsService.validateWeekCommencingIntegrity(
                    persisted.getCourtHouseId(), request.getBusinessType(),
                    persisted.getSessionDate(), persisted.getSessionDate());
            if (!duplicate.isEmpty()) {
                return duplicate;
            }
        }
        return EMPTY_JSON_OBJECT;
    }

    private static Optional<LocalDate> parseDate(final String date) {
        if (isNull(date)) {
            return Optional.empty();
        }
        try {
            return Optional.of(DateUtils.parseRepeatPatternDate(date));
        } catch (final DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private boolean isSlotBased(final String businessTypeCode) {
        // the standard create validation has already rejected an unknown business type
        return referenceDataCache.getRotaBusinessTypeByCode(businessTypeCode)
                .map(BusinessType::getSlot)
                .map(TRUE::equals)
                .orElse(false);
    }
}
