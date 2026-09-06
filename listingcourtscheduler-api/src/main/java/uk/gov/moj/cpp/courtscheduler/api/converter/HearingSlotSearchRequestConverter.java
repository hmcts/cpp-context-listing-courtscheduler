package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import jakarta.json.JsonObject;

@Service
public class HearingSlotSearchRequestConverter implements Converter<JsonObject, HearingSlotSearchRequest> {
    @Override
    public HearingSlotSearchRequest convert(final JsonObject jsonObject) {
        final String hearingId = jsonObject.containsKey(RequestParameterConstant.HEARING_ID.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.HEARING_ID.getLabel()) : null;
        final String courtCentreId = jsonObject.containsKey(RequestParameterConstant.COURT_CENTRE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.COURT_CENTRE.getLabel()) : null;
        final String courtRoomId = jsonObject.containsKey(RequestParameterConstant.COURT_ROOM.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.COURT_ROOM.getLabel()) : null;
        final String hearingSessionDate = jsonObject.containsKey(RequestParameterConstant.HEARING_DATE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.HEARING_DATE.getLabel()) : null;
        final String hearingSessionDateCutOff = jsonObject.containsKey(RequestParameterConstant.HEARING_SESSION_DATE_CUT_OFF.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.HEARING_SESSION_DATE_CUT_OFF.getLabel()) : null;
        final String sessionStartTime = jsonObject.containsKey(RequestParameterConstant.HEARING_START_TIME.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.HEARING_START_TIME.getLabel()) : null;
        final Integer durationMinutes = jsonObject.containsKey(RequestParameterConstant.DURATION_MINUTES.getLabel()) ?
                jsonObject.getInt(RequestParameterConstant.DURATION_MINUTES.getLabel()) : null;
        final Boolean isPolice = jsonObject.containsKey(RequestParameterConstant.IS_POLICE.getLabel()) ?
                jsonObject.getBoolean(RequestParameterConstant.IS_POLICE.getLabel()) : false;
        final String businessType = jsonObject.containsKey(RequestParameterConstant.BUSINESS_TYPE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.BUSINESS_TYPE.getLabel()) : null;

        return new HearingSlotSearchRequest(hearingId, courtCentreId, hearingSessionDate, courtRoomId, hearingSessionDateCutOff, sessionStartTime, durationMinutes, isPolice, businessType);
    }
}
