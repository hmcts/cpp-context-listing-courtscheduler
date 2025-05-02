package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import javax.json.JsonObject;

public class HearingSlotSearchRequestConverter implements Converter<JsonObject, HearingSlotSearchRequest> {
    @Override
    public HearingSlotSearchRequest convert(final JsonObject jsonObject) {
        final String hearingId = jsonObject.containsKey(RequestParameterConstant.HEARING_ID.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.HEARING_ID.getLabel()) : null;
        final String ouCode = jsonObject.containsKey(RequestParameterConstant.OU_CODE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.OU_CODE.getLabel()) : null;
        final String courtRoomId = jsonObject.containsKey(RequestParameterConstant.COURT_ROOM.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.COURT_ROOM.getLabel()) : null;
        final String hearingSessionDate = jsonObject.containsKey(RequestParameterConstant.HEARING_SESSION_DATE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.HEARING_SESSION_DATE.getLabel()) : null;
        final String hearingSessionDateCutOff = jsonObject.containsKey(RequestParameterConstant.HEARING_SESSION_DATE_CUT_OFF.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.HEARING_SESSION_DATE_CUT_OFF.getLabel()) : null;
        final String sessionStartTime = jsonObject.containsKey(RequestParameterConstant.SESSION_START_TIME.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.SESSION_START_TIME.getLabel()) : null;
        final Integer durationMinutes = jsonObject.containsKey(RequestParameterConstant.DURATION_MINUTES.getLabel()) ?
                jsonObject.getInt(RequestParameterConstant.DURATION_MINUTES.getLabel()) : null;

        return new HearingSlotSearchRequest(hearingId, ouCode, hearingSessionDate, courtRoomId, hearingSessionDateCutOff, sessionStartTime, durationMinutes);
    }
}
