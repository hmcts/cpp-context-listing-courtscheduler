package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import jakarta.json.JsonObject;

@Service
public class CourtScheduleRequestParamConverter implements Converter<JsonObject, CourtScheduleRequestParam> {
    @Override
    public CourtScheduleRequestParam convert(final JsonObject jsonObject) {
        final String courtCentreId = jsonObject.getString(RequestParameterConstant.COURT_CENTRE.getLabel());
        final String startDate = jsonObject.getString(RequestParameterConstant.SESSION_START_DATE.getLabel());
        final String endDate = jsonObject.getString(RequestParameterConstant.SESSION_END_DATE.getLabel());
        final String pageSize = jsonObject.getString(RequestParameterConstant.PAGE_SIZE.getLabel());
        final String pageNumber = jsonObject.getString(RequestParameterConstant.PAGE_NUMBER.getLabel());
        final String courtRoomId = jsonObject.containsKey(RequestParameterConstant.COURT_ROOM.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.COURT_ROOM.getLabel()) : null;
        final String businessType =  jsonObject.containsKey(RequestParameterConstant.BUSINESS_TYPE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.BUSINESS_TYPE.getLabel()) : null;
        final Boolean isDraft = jsonObject.containsKey(RequestParameterConstant.IS_DRAFT.getLabel()) ?
                jsonObject.getBoolean(RequestParameterConstant.IS_DRAFT.getLabel()) : null;
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId, businessType, startDate, endDate, isDraft, pageSize, pageNumber);
    }
}
