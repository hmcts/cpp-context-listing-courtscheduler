package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.END_DATE_IS_IN_BAD_FORMAT;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_AFTER_END_DATE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.START_DATE_IS_IN_BAD_FORMAT;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.ALL_DAY_SPLIT;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_CENTRE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_ROOM;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.DURATION;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.END_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.INDEX;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.IS_DRAFT;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.IS_OVERBOOKING_ALLOWED;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.JURISDICTION;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.MAX_DURATION_FOR_AFTERNOON;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.MAX_DURATION_FOR_MORNING;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.PANEL;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.REPEAT_DAYS;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.SESSION_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.START_DATE;

import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Session;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

@Service
public class CreateSessionsRequestParamConverter implements Converter<JsonObject, CreateSessionRequestParam> {
    @Override
    public CreateSessionRequestParam convert(final JsonObject jsonObject) {
        final List<Session> sessions = convertSessions(jsonObject.getJsonArray(RequestParameterConstant.SESSIONS.getLabel()));
        final RepeatPattern repeatPattern = convertRepeatPattern(jsonObject.getJsonObject(RequestParameterConstant.REPEAT_PATTERN.getLabel()));
        Session sessionToBeAdded = null;
        if (nonNull(jsonObject.getJsonObject(RequestParameterConstant.SESSION_TO_BE_ADDED.getLabel()))) {
            sessionToBeAdded = convertSession(jsonObject.getJsonObject(RequestParameterConstant.SESSION_TO_BE_ADDED.getLabel()));
        }

        CreateSessionRequestParam.CreateSessionRequestParamBuilder createSessionRequestParamBuilder = CreateSessionRequestParam.CreateSessionRequestParamBuilder.createSessionRequestParam();
        createSessionRequestParamBuilder.withSessionList(sessions);
        createSessionRequestParamBuilder.withRepeatPattern(repeatPattern);
        if (nonNull(sessionToBeAdded)) {
            createSessionRequestParamBuilder.withSessionToBeAdded(sessionToBeAdded);
        }

        return createSessionRequestParamBuilder.build();
    }

    private List<Session> convertSessions(JsonArray jsonArray) {
        List<Session> sessions = new ArrayList<>();
        for (JsonValue jsonValue : jsonArray) {
            JsonObject jsonObject = (JsonObject) jsonValue;
            if (jsonObject.getJsonArray(REPEAT_DAYS.getLabel()).isEmpty()) {
                throw new IllegalArgumentException("Repeat days cannot be empty");
            }
            final Session.SessionBuilder sessionBuilder = Session.SessionBuilder.session()
                    .withCourtCentreId(jsonObject.getString(COURT_CENTRE_ID.getLabel()))
                    .withCourtRoomId(jsonObject.getString(COURT_ROOM.getLabel()))
                    .withSessionType(jsonObject.getString(SESSION_TYPE.getLabel()))
                    .withBusinessType(jsonObject.getString(BUSINESS_TYPE.getLabel()))
                    .withSlotsOrDuration(jsonObject.getInt(DURATION.getLabel(), 0))
                    .withPanelType(jsonObject.getString(PANEL.getLabel()))
                    .withRepeatDays(DayOfWeekConverter.convert(jsonObject.getJsonArray(REPEAT_DAYS.getLabel())))
                    .withAllDaySplit(jsonObject.getBoolean(ALL_DAY_SPLIT.getLabel(), false))
                    .withMaxDurationForMorning(jsonObject.getInt(MAX_DURATION_FOR_MORNING.getLabel(), 0))
                    .withMaxDurationForAfternoon(jsonObject.getInt(MAX_DURATION_FOR_AFTERNOON.getLabel(), 0))
                    .withJurisdiction(jsonObject.getString(JURISDICTION.getLabel()))
                    .withIndex(jsonObject.containsKey(INDEX.getLabel()) ? jsonObject.getInt(INDEX.getLabel()) : null)
                    .withJurisdiction(jsonObject.containsKey(JURISDICTION.getLabel()) ? jsonObject.getString(JURISDICTION.getLabel()) : MAGISTRATES.getJurisdiction());

            if (!isNull(jsonObject.get(IS_DRAFT.getLabel()))) {
                sessionBuilder.withIsDraft(jsonObject.getBoolean(IS_DRAFT.getLabel()));
            }

            if (!isNull(jsonObject.get(IS_OVERBOOKING_ALLOWED.getLabel()))) {
                sessionBuilder.withIsOverbookingAllowed(jsonObject.getBoolean(IS_OVERBOOKING_ALLOWED.getLabel()));
            }

            if (!isNull(jsonObject.get(SESSION_START_TIME.getLabel())) && !isNull(jsonObject.get(SESSION_END_TIME.getLabel()))) {
                sessionBuilder
                        .withSessionStartTime(jsonObject.getString(SESSION_START_TIME.getLabel()))
                        .withSessionEndTime(jsonObject.getString(SESSION_END_TIME.getLabel()));
            }
            sessions.add(sessionBuilder.build());
        }
        return sessions;
    }

    private Session convertSession(JsonObject jsonObject) {
        final Session.SessionBuilder sessionBuilder = Session.SessionBuilder.session()
                .withCourtCentreId(jsonObject.getString(COURT_CENTRE_ID.getLabel()))
                .withCourtRoomId(jsonObject.getString(COURT_ROOM.getLabel()))
                .withSessionType(jsonObject.getString(SESSION_TYPE.getLabel()))
                .withBusinessType(jsonObject.getString(BUSINESS_TYPE.getLabel()))
                .withSlotsOrDuration(jsonObject.getInt(DURATION.getLabel(), 0))
                .withPanelType(jsonObject.getString(PANEL.getLabel()))
                .withRepeatDays(DayOfWeekConverter.convert(jsonObject.getJsonArray(REPEAT_DAYS.getLabel())))
                .withMaxDurationForMorning(jsonObject.getInt(MAX_DURATION_FOR_MORNING.getLabel(), -1))
                .withMaxDurationForAfternoon(jsonObject.getInt(MAX_DURATION_FOR_AFTERNOON.getLabel(), -1))
                .withIndex(jsonObject.containsKey(INDEX.getLabel()) ? jsonObject.getInt(INDEX.getLabel()) : null)
                .withJurisdiction(jsonObject.containsKey(JURISDICTION.getLabel()) ? jsonObject.getString(JURISDICTION.getLabel()) : MAGISTRATES.getJurisdiction());

        if (!isNull(jsonObject.get(IS_DRAFT.getLabel()))) {
            sessionBuilder.withIsDraft(jsonObject.getBoolean(IS_DRAFT.getLabel()));
        }

        if (!isNull(jsonObject.get(ALL_DAY_SPLIT.getLabel()))) {
            sessionBuilder.withAllDaySplit(jsonObject.getBoolean(ALL_DAY_SPLIT.getLabel()));
        }

        if (!isNull(jsonObject.get(SESSION_START_TIME.getLabel())) && !isNull(jsonObject.get(SESSION_END_TIME.getLabel()))) {
            sessionBuilder
                    .withSessionStartTime(jsonObject.getString(SESSION_START_TIME.getLabel()))
                    .withSessionEndTime(jsonObject.getString(SESSION_END_TIME.getLabel()));
        }

        if (!isNull(jsonObject.get(IS_OVERBOOKING_ALLOWED.getLabel()))) {
            sessionBuilder.withIsOverbookingAllowed(jsonObject.getBoolean(IS_OVERBOOKING_ALLOWED.getLabel()));
        }

        return sessionBuilder.build();
    }

    private RepeatPattern convertRepeatPattern(JsonObject jsonObject) {
        final String startDateStr = jsonObject.getString(START_DATE.getLabel());
        String endDateStr = jsonObject.getString(END_DATE.getLabel(), null);
        
        // Treat placeholder values as null
        if (endDateStr != null && (endDateStr.equals("END_DATE") || endDateStr.trim().isEmpty())) {
            endDateStr = null;
        }

        final LocalDate startDate = parseDateOrThrow(startDateStr, true);
        final LocalDate endDate = endDateStr != null ? parseDateOrThrow(endDateStr, false) : null;

        if (endDate != null && endDate.isBefore(startDate)) {
            throw new ValidationException(Json.createObjectBuilder()
                    .add(ERROR_MESSAGE, START_DATE_AFTER_END_DATE)
                    .build());
        }

        return RepeatPattern.RepeatPatternBuilder.repeatPattern()
                .withFrequency(RepeatFrequency.valueOf(jsonObject.getString(RequestParameterConstant.REPEAT_FREQUENCY.getLabel()).trim().toUpperCase()))
                .withRepeatFor(jsonObject.getInt(RequestParameterConstant.REPEAT_FOR.getLabel()))
                .withStartDate(startDate)
                .withEndDate(endDate)
                .build();
    }

    private LocalDate parseDateOrThrow(final String date, final boolean isStart) {
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_DATE);
        } catch (DateTimeParseException ex) {
            final String message = isStart ? START_DATE_IS_IN_BAD_FORMAT : END_DATE_IS_IN_BAD_FORMAT;
            throw new ValidationException(Json.createObjectBuilder()
                    .add(ERROR_MESSAGE, String.format(message, date))
                    .build());
        }
    }
}