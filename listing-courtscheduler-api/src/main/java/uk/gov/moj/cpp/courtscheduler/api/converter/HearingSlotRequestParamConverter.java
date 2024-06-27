package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import javax.json.JsonObject;

public class HearingSlotRequestParamConverter implements Converter<JsonObject, HearingSlotRequestParam> {
    @Override
    public HearingSlotRequestParam convert(final JsonObject jsonObject) {
        final String panel = jsonObject.containsKey(RequestParameterConstant.PANEL.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.PANEL.getLabel()) : null;
        final String startDate = jsonObject.containsKey(RequestParameterConstant.SESSION_START_DATE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.SESSION_START_DATE.getLabel()) : null;
        final String endDate = jsonObject.containsKey(RequestParameterConstant.SESSION_END_DATE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.SESSION_END_DATE.getLabel()) : null;
        final String ouLevel = jsonObject.containsKey(RequestParameterConstant.OU_LEVEL2.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.OU_LEVEL2.getLabel()) : null;
        final String ouCode = jsonObject.containsKey(RequestParameterConstant.OU_CODE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.OU_CODE.getLabel()) : null;
        final String pageSize = jsonObject.containsKey(RequestParameterConstant.PAGE_SIZE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.PAGE_SIZE.getLabel()) : null;
        final String pageNumber = jsonObject.containsKey(RequestParameterConstant.PAGE_NUMBER.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.PAGE_NUMBER.getLabel()) : null;
        final String courtRoomId = jsonObject.containsKey(RequestParameterConstant.COURT_ROOM.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.COURT_ROOM.getLabel()) : null;
        final String courtRoomNumber = jsonObject.containsKey(RequestParameterConstant.COURT_ROOM_NUMBER.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.COURT_ROOM_NUMBER.getLabel()) : null;
        final String businessType = jsonObject.containsKey(RequestParameterConstant.BUSINESS_TYPE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.BUSINESS_TYPE.getLabel()) : null;
        final String courtSession = jsonObject.containsKey(RequestParameterConstant.COURT_SESSION.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.COURT_SESSION.getLabel()) : null;

        return new HearingSlotRequestParam(panel, startDate, endDate, ouLevel, ouCode, pageSize,
                pageNumber, courtRoomId, courtRoomNumber, businessType, courtSession);
    }
}
