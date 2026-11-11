package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.Boolean.TRUE;
import static uk.gov.moj.cpp.courtscheduler.common.CommonUtils.buildErrorResponse;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.CROWN;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_COURTROOM;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_NO_MONDAY;
import static uk.gov.moj.cpp.courtscheduler.common.exception.ErrorMessages.WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED;
import static uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency.EVERY_WEEK;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.PanelTypes.ADULT;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;

import uk.gov.moj.cpp.courtscheduler.api.validator.SessionsApiValidator;
import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataCache;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.openapi.model.BusinessType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekCommencingCreateRequest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import jakarta.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.owasp.encoder.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * SPRDT-1550: creates a Crown week commencing session, a draft capacity bucket for a court centre
 * and business type stored as one all-day {@code court_schedule} row on each Monday of the date
 * range. The request is converted into an ordinary {@code courtscheduler.create} payload and goes
 * through the existing create validation and persistence; the only rules specific to week
 * commencing are the default courtroom, duration-based business types only, and one draft week
 * commencing session per Monday for a court centre and business type.
 */
@Service
public class WeekCommencingSessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WeekCommencingSessionService.class);

    private static final String MONDAY = StringUtils.capitalize(DayOfWeek.MONDAY.name().toLowerCase(Locale.ROOT));
    private static final int EVERY_WEEK_REPEAT = 1;

    private final ReferenceDataCache referenceDataCache;
    private final SessionsApiValidator sessionsApiValidator;
    private final SessionsService sessionsService;

    public WeekCommencingSessionService(final ReferenceDataCache referenceDataCache,
                                        final SessionsApiValidator sessionsApiValidator,
                                        final SessionsService sessionsService) {
        this.referenceDataCache = referenceDataCache;
        this.sessionsApiValidator = sessionsApiValidator;
        this.sessionsService = sessionsService;
    }

    /**
     * Validates and persists the week commencing session.
     *
     * @throws ValidationException (400) when the court centre has no courtroom, the converted
     *                             session fails the standard create validation, the business type
     *                             is slot-based, or a Monday in the range already has one
     */
    public void create(final WeekCommencingCreateRequest request) {
        final String courtRoomId = defaultCourtRoomId(request.getCourtCentreId());
        final CreateSessionRequestParam createRequest = toCreateSessionRequest(request, courtRoomId);

        failIfNotEmpty(sessionsApiValidator.getSessionsCreateValidation(createRequest));
        failIfSlotBased(request.getBusinessType());
        // the standard validation has already rejected unparseable dates and an end before the start
        final LocalDate startDate = DateUtils.parseRepeatPatternDate(request.getStartDate());
        final LocalDate endDate = DateUtils.parseRepeatPatternDate(request.getEndDate());
        failIfNoMonday(startDate, endDate);
        failIfNotEmpty(sessionsService.createWeekCommencing(
                createRequest, request.getCourtCentreId(), request.getBusinessType(), startDate, endDate));

        LOGGER.info("Week commencing session created for courtCentreId {} businessType {} from {} to {}",
                request.getCourtCentreId(), Encode.forJava(request.getBusinessType()),
                Encode.forJava(request.getStartDate()), Encode.forJava(request.getEndDate()));
    }

    /**
     * The first courtroom the reference data lists for the court centre. Reference data returns a
     * centre's currently valid courtrooms sorted by venue name, then courtroom name
     * (CourtRoom#compareTo in cpp-context-reference-data), so this is normally "Courtroom 01" of the
     * first venue. The ou-courtrooms {@code courtroomId} is a platform-wide id, not a room number
     * within the centre, so it cannot be used to find "courtroom 1".
     */
    /* package */ String defaultCourtRoomId(final String courtCentreId) {
        return referenceDataCache.getCpCourtRoomsByCourtCentreId(courtCentreId).stream()
                .map(CourtRoom::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new ValidationException(buildErrorResponse(WEEK_COMMENCING_NO_COURTROOM + courtCentreId)));
    }

    private static CreateSessionRequestParam toCreateSessionRequest(final WeekCommencingCreateRequest request, final String courtRoomId) {
        final Session session = new Session()
                .courtCentreId(request.getCourtCentreId())
                .jurisdiction(CROWN.getJurisdiction())
                .courtRoomId(courtRoomId)
                .sessionType(ALL_DAY)
                .businessType(request.getBusinessType())
                .duration(request.getCapacity())
                .allDaySplit(false)
                .maxDurationForMorning(0)
                .maxDurationForAfternoon(0)
                .panel(ADULT.name())
                .repeatDays(List.of(MONDAY))
                .isOverbookingAllowed(TRUE.equals(request.getIsOverbookingAllowed()))
                .isDraft(true)
                .isWeekCommencing(true);

        final RepeatPattern repeatPattern = new RepeatPattern()
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .repeatFor(EVERY_WEEK_REPEAT)
                .frequency(EVERY_WEEK.getRepeatFrequency());

        return new CreateSessionRequestParam()
                .sessions(List.of(session))
                .repeatPattern(repeatPattern);
    }

    private static void failIfNoMonday(final LocalDate startDate, final LocalDate endDate) {
        if (startDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).isAfter(endDate)) {
            throw new ValidationException(buildErrorResponse(WEEK_COMMENCING_NO_MONDAY));
        }
    }

    private void failIfSlotBased(final String businessTypeCode) {
        // The standard create validation has already rejected an unknown business type.
        final boolean slotBased = referenceDataCache.getRotaBusinessTypeByCode(businessTypeCode)
                .map(BusinessType::getSlot)
                .map(TRUE::equals)
                .orElse(false);
        if (slotBased) {
            throw new ValidationException(buildErrorResponse(WEEK_COMMENCING_SLOT_BASED_NOT_ALLOWED));
        }
    }

    private static void failIfNotEmpty(final JsonObject validationResult) {
        if (!validationResult.isEmpty()) {
            throw new ValidationException(validationResult);
        }
    }
}
