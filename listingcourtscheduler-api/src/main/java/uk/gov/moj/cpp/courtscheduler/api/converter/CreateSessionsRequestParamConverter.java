package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Session;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

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
            if (jsonObject.getJsonArray(RequestParameterConstant.REPEAT_DAYS.getLabel()).isEmpty()) {
                throw new IllegalArgumentException("Repeat days cannot be empty");
            }
            final Session.SessionBuilder sessionBuilder = Session.SessionBuilder.session()
                    .withCourtCentreId(jsonObject.getString(RequestParameterConstant.COURT_CENTRE_ID.getLabel()))
                    .withCourtRoomId(jsonObject.getString(RequestParameterConstant.COURT_ROOM.getLabel()))
                    .withSessionType(jsonObject.getString(RequestParameterConstant.SESSION_TYPE.getLabel()))
                    .withBusinessType(jsonObject.getString(RequestParameterConstant.BUSINESS_TYPE.getLabel()))
                    .withSlotsOrDuration(jsonObject.getInt(RequestParameterConstant.DURATION.getLabel(), 0))
                    .withPanelType(jsonObject.getString(RequestParameterConstant.PANEL.getLabel()))
                    .withRepeatDays(DayOfWeekConverter.convert(jsonObject.getJsonArray(RequestParameterConstant.REPEAT_DAYS.getLabel())))
                    .withAllDaySplit(jsonObject.getBoolean(RequestParameterConstant.ALL_DAY_SPLIT.getLabel(), false))
                    .withMaxDurationForMorning(jsonObject.getInt(RequestParameterConstant.MAX_DURATION_FOR_MORNING.getLabel(), 0))
                    .withMaxDurationForAfternoon(jsonObject.getInt(RequestParameterConstant.MAX_DURATION_FOR_AFTERNOON.getLabel(), 0))
                    .withIsDraft(jsonObject.getBoolean(RequestParameterConstant.IS_DRAFT.getLabel(), false));

            if (!isNull(jsonObject.get(RequestParameterConstant.IS_OVERBOOKING_ALLOWED.getLabel()))) {
                sessionBuilder.withIsOverbookingAllowed(jsonObject.getBoolean(RequestParameterConstant.IS_OVERBOOKING_ALLOWED.getLabel()));
            }

            if (!isNull(jsonObject.get(RequestParameterConstant.SESSION_START_TIME.getLabel())) && !isNull(jsonObject.get(RequestParameterConstant.SESSION_END_TIME.getLabel()))) {
                sessionBuilder
                        .withSessionStartTime(jsonObject.getString(RequestParameterConstant.SESSION_START_TIME.getLabel()))
                        .withSessionEndTime(jsonObject.getString(RequestParameterConstant.SESSION_END_TIME.getLabel()));
            }
            sessions.add(sessionBuilder.build());
        }
        return sessions;
    }

    private Session convertSession(JsonObject jsonObject) {
        final Session.SessionBuilder sessionBuilder = Session.SessionBuilder.session()
                .withCourtCentreId(jsonObject.getString(RequestParameterConstant.COURT_CENTRE_ID.getLabel()))
                .withCourtRoomId(jsonObject.getString(RequestParameterConstant.COURT_ROOM.getLabel()))
                .withSessionType(jsonObject.getString(RequestParameterConstant.SESSION_TYPE.getLabel()))
                .withBusinessType(jsonObject.getString(RequestParameterConstant.BUSINESS_TYPE.getLabel()))
                .withSlotsOrDuration(jsonObject.getInt(RequestParameterConstant.DURATION.getLabel(), 0))
                .withPanelType(jsonObject.getString(RequestParameterConstant.PANEL.getLabel()))
                .withRepeatDays(DayOfWeekConverter.convert(jsonObject.getJsonArray(RequestParameterConstant.REPEAT_DAYS.getLabel())))
                .withMaxDurationForMorning(jsonObject.getInt(RequestParameterConstant.MAX_DURATION_FOR_MORNING.getLabel(), -1))
                .withMaxDurationForAfternoon(jsonObject.getInt(RequestParameterConstant.MAX_DURATION_FOR_AFTERNOON.getLabel(), -1))
                .withIsDraft(jsonObject.getBoolean(RequestParameterConstant.IS_DRAFT.getLabel(), false));

        if (!isNull(jsonObject.get(RequestParameterConstant.ALL_DAY_SPLIT.getLabel()))) {
            sessionBuilder.withAllDaySplit(jsonObject.getBoolean(RequestParameterConstant.ALL_DAY_SPLIT.getLabel()));
        }

        if (!isNull(jsonObject.get(RequestParameterConstant.SESSION_START_TIME.getLabel())) && !isNull(jsonObject.get(RequestParameterConstant.SESSION_END_TIME.getLabel()))) {
            sessionBuilder
                    .withSessionStartTime(jsonObject.getString(RequestParameterConstant.SESSION_START_TIME.getLabel()))
                    .withSessionEndTime(jsonObject.getString(RequestParameterConstant.SESSION_END_TIME.getLabel()));
        }

        if (!isNull(jsonObject.get(RequestParameterConstant.IS_OVERBOOKING_ALLOWED.getLabel()))) {
            sessionBuilder.withIsOverbookingAllowed(jsonObject.getBoolean(RequestParameterConstant.IS_OVERBOOKING_ALLOWED.getLabel()));
        }

        return sessionBuilder.build();
    }

    private RepeatPattern convertRepeatPattern(JsonObject jsonObject) {
        return RepeatPattern.RepeatPatternBuilder.repeatPattern()
                .withFrequency(RepeatFrequency.valueOf(jsonObject.getString(RequestParameterConstant.REPEAT_FREQUENCY.getLabel()).trim().toUpperCase()))
                .withRepeatFor(jsonObject.getInt(RequestParameterConstant.REPEAT_FOR.getLabel()))
                .withStartDate(LocalDate.parse(jsonObject.getString(RequestParameterConstant.START_DATE.getLabel()), DateTimeFormatter.ISO_DATE))
                .withEndDate(LocalDate.parse(jsonObject.getString(RequestParameterConstant.END_DATE.getLabel()), DateTimeFormatter.ISO_DATE))
                .build();
    }
}