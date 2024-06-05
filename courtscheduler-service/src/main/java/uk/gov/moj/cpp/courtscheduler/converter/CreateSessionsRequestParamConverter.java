package uk.gov.moj.cpp.courtscheduler.converter;

import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Session;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class CreateSessionsRequestParamConverter implements Converter<JsonObject, CreateSessionRequestParam> {
    @Override
    public CreateSessionRequestParam convert(final JsonObject jsonObject) {
        final List<Session> sessions = convertSessions(jsonObject.getJsonArray(RequestParameterConstant.SESSIONS.getLabel()));
        final RepeatPattern  repeatPattern = convertRepeatPattern(jsonObject.getJsonObject(RequestParameterConstant.REPEAT_PATTERN.getLabel()));

        return CreateSessionRequestParam.CreateSessionRequestParamBuilder.createSessionRequestParam()
                .withSessionList(sessions)
                .withRepeatPattern(repeatPattern)
                .build();
    }

    private List<Session> convertSessions(JsonArray jsonArray) {
        List<Session> sessions = new ArrayList<>();
        for (JsonValue jsonValue : jsonArray) {
            JsonObject jsonObject = (JsonObject) jsonValue;
            if(jsonObject.getJsonArray(RequestParameterConstant.REPEAT_DAYS.getLabel()).isEmpty()){
                throw new IllegalArgumentException("Repeat days cannot be empty");
            }
            sessions.add(Session.SessionBuilder.session()
                    .withCourtCentreId(jsonObject.getString(RequestParameterConstant.COURT_CENTRE_ID.getLabel()))
                            .withCourtRoomId( jsonObject.getString(RequestParameterConstant.COURT_ROOM.getLabel()))
                            .withSessionType(jsonObject.getString(RequestParameterConstant.SESSION_TYPE.getLabel()))
                            .withBusinessType(jsonObject.getString(RequestParameterConstant.BUSINESS_TYPE.getLabel()))
                            .withSlotsOrDuration(jsonObject.getInt(RequestParameterConstant.DURATION.getLabel(), 0))
                            .withPanelType(jsonObject.getString(RequestParameterConstant.PANEL.getLabel()))
                            .withRepeatDays(DayOfWeekConverter.convert(jsonObject.getJsonArray(RequestParameterConstant.REPEAT_DAYS.getLabel())))
                    .build());

        }
        return sessions;
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