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
import uk.gov.moj.cpp.courtscheduler.openapi.model.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.openapi.model.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Session;
import uk.gov.moj.cpp.courtscheduler.openapi.model.WeekDay;

import java.time.DayOfWeek;
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

        final CreateSessionRequestParam createSessionRequestParam = new CreateSessionRequestParam()
                .sessionList(sessions)
                .repeatPattern(repeatPattern);
        if (nonNull(sessionToBeAdded)) {
            createSessionRequestParam.sessionToBeAdded(sessionToBeAdded);
        }

        return createSessionRequestParam;
    }

    private List<Session> convertSessions(JsonArray jsonArray) {
        List<Session> sessions = new ArrayList<>();
        for (JsonValue jsonValue : jsonArray) {
            JsonObject jsonObject = (JsonObject) jsonValue;
            if (jsonObject.getJsonArray(REPEAT_DAYS.getLabel()).isEmpty()) {
                throw new IllegalArgumentException("Repeat days cannot be empty");
            }
            final Session session = new Session()
                    .courtCentreId(jsonObject.getString(COURT_CENTRE_ID.getLabel()))
                    .courtRoomId(jsonObject.getString(COURT_ROOM.getLabel()))
                    .sessionType(jsonObject.getString(SESSION_TYPE.getLabel()))
                    .businessType(jsonObject.getString(BUSINESS_TYPE.getLabel()))
                    .slotsOrDuration(jsonObject.getInt(DURATION.getLabel(), 0))
                    .panel(jsonObject.getString(PANEL.getLabel()))
                    .repeatDays(toWeekDays(DayOfWeekConverter.convert(jsonObject.getJsonArray(REPEAT_DAYS.getLabel()))))
                    .allDaySplit(jsonObject.getBoolean(ALL_DAY_SPLIT.getLabel(), false))
                    .maxDurationForMorning(jsonObject.getInt(MAX_DURATION_FOR_MORNING.getLabel(), 0))
                    .maxDurationForAfternoon(jsonObject.getInt(MAX_DURATION_FOR_AFTERNOON.getLabel(), 0))
                    .index(jsonObject.containsKey(INDEX.getLabel()) ? jsonObject.getInt(INDEX.getLabel()) : null)
                    .jurisdiction(jsonObject.containsKey(JURISDICTION.getLabel()) ? jsonObject.getString(JURISDICTION.getLabel()) : MAGISTRATES.getJurisdiction());

            if (!isNull(jsonObject.get(IS_DRAFT.getLabel()))) {
                session.isDraft(jsonObject.getBoolean(IS_DRAFT.getLabel()));
            }

            if (!isNull(jsonObject.get(IS_OVERBOOKING_ALLOWED.getLabel()))) {
                session.isOverbookingAllowed(jsonObject.getBoolean(IS_OVERBOOKING_ALLOWED.getLabel()));
            }

            if (!isNull(jsonObject.get(SESSION_START_TIME.getLabel())) && !isNull(jsonObject.get(SESSION_END_TIME.getLabel()))) {
                session
                        .sessionStartTime(jsonObject.getString(SESSION_START_TIME.getLabel()))
                        .sessionEndTime(jsonObject.getString(SESSION_END_TIME.getLabel()));
            }
            sessions.add(session);
        }
        return sessions;
    }

    private Session convertSession(JsonObject jsonObject) {
        final Session session = new Session()
                .courtCentreId(jsonObject.getString(COURT_CENTRE_ID.getLabel()))
                .courtRoomId(jsonObject.getString(COURT_ROOM.getLabel()))
                .sessionType(jsonObject.getString(SESSION_TYPE.getLabel()))
                .businessType(jsonObject.getString(BUSINESS_TYPE.getLabel()))
                .slotsOrDuration(jsonObject.getInt(DURATION.getLabel(), 0))
                .panel(jsonObject.getString(PANEL.getLabel()))
                .repeatDays(toWeekDays(DayOfWeekConverter.convert(jsonObject.getJsonArray(REPEAT_DAYS.getLabel()))))
                .maxDurationForMorning(jsonObject.getInt(MAX_DURATION_FOR_MORNING.getLabel(), -1))
                .maxDurationForAfternoon(jsonObject.getInt(MAX_DURATION_FOR_AFTERNOON.getLabel(), -1))
                .index(jsonObject.containsKey(INDEX.getLabel()) ? jsonObject.getInt(INDEX.getLabel()) : null)
                .jurisdiction(jsonObject.containsKey(JURISDICTION.getLabel()) ? jsonObject.getString(JURISDICTION.getLabel()) : MAGISTRATES.getJurisdiction());

        if (!isNull(jsonObject.get(IS_DRAFT.getLabel()))) {
            session.isDraft(jsonObject.getBoolean(IS_DRAFT.getLabel()));
        }

        if (!isNull(jsonObject.get(ALL_DAY_SPLIT.getLabel()))) {
            session.allDaySplit(jsonObject.getBoolean(ALL_DAY_SPLIT.getLabel()));
        }

        if (!isNull(jsonObject.get(SESSION_START_TIME.getLabel())) && !isNull(jsonObject.get(SESSION_END_TIME.getLabel()))) {
            session
                    .sessionStartTime(jsonObject.getString(SESSION_START_TIME.getLabel()))
                    .sessionEndTime(jsonObject.getString(SESSION_END_TIME.getLabel()));
        }

        if (!isNull(jsonObject.get(IS_OVERBOOKING_ALLOWED.getLabel()))) {
            session.isOverbookingAllowed(jsonObject.getBoolean(IS_OVERBOOKING_ALLOWED.getLabel()));
        }

        return session;
    }

    private static List<WeekDay> toWeekDays(final java.util.Set<DayOfWeek> daysOfWeek) {
        final List<WeekDay> weekDays = new ArrayList<>();
        for (DayOfWeek dayOfWeek : daysOfWeek) {
            weekDays.add(WeekDay.valueOf(dayOfWeek.name()));
        }
        return weekDays;
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

        return new RepeatPattern()
                .frequency(RepeatFrequency.valueOf(jsonObject.getString(RequestParameterConstant.REPEAT_FREQUENCY.getLabel()).trim().toUpperCase()))
                .repeatFor(jsonObject.getInt(RequestParameterConstant.REPEAT_FOR.getLabel()))
                .startDate(startDate)
                .endDate(endDate);
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