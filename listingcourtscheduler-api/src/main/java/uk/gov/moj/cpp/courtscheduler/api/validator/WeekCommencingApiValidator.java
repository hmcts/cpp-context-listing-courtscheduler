package uk.gov.moj.cpp.courtscheduler.api.validator;

import static java.lang.Boolean.TRUE;
import static java.util.Objects.isNull;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_COURTROOM;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_MONDAY;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.openapi.model.BusinessType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

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
     * Validates a week commencing request and the create payload it was converted into. Returns the
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

        return sessionsService.validateWeekCommencingIntegrity(
                request.getCourtCentreId(), request.getBusinessType(), startDate, endDate);
    }

    private boolean isSlotBased(final String businessTypeCode) {
        // the standard create validation has already rejected an unknown business type
        return referenceDataCache.getRotaBusinessTypeByCode(businessTypeCode)
                .map(BusinessType::getSlot)
                .map(TRUE::equals)
                .orElse(false);
    }
}
