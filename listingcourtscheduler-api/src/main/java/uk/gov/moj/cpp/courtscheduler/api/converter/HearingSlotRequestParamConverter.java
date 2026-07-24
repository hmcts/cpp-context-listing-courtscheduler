package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import static java.lang.Boolean.valueOf;

import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import jakarta.json.JsonObject;

@Service
public class HearingSlotRequestParamConverter implements Converter<JsonObject, HearingSlotRequestParam> {
    @Override
    public HearingSlotRequestParam convert(final JsonObject jsonObject) {
        final String panel = jsonObject.containsKey(RequestParameterConstant.PANEL.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.PANEL.getLabel()) : null;
        final String startDate = jsonObject.containsKey(RequestParameterConstant.SESSION_START_DATE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.SESSION_START_DATE.getLabel()) : null;
        final String endDate = jsonObject.containsKey(RequestParameterConstant.SESSION_END_DATE.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.SESSION_END_DATE.getLabel()) : null;
        final String exactHearingStartDateTime = jsonObject.containsKey(RequestParameterConstant.EXACT_HEARING_START_DATETIME.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.EXACT_HEARING_START_DATETIME.getLabel()) : null;
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
        final Boolean isSlotBased = jsonObject.containsKey(RequestParameterConstant.IS_SLOT_BASED.getLabel()) ?
                valueOf(jsonObject.getString(RequestParameterConstant.IS_SLOT_BASED.getLabel())) : null;
        final String hearingStartTime = jsonObject.containsKey(RequestParameterConstant.HEARING_START_TIME.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.HEARING_START_TIME.getLabel()) : null;
        // HearingSlotsApi.getHearingSlots writes Boolean fields into the qp map via
        // .toString(), so they arrive here as JsonString("true"/"false") — not JsonValue.TRUE/FALSE —
        // while direct JSON payloads (and the ccsph2-era fixtures) carry a real JSON boolean.
        // Accept both shapes; getBoolean()/getString() alone each ClassCast on the other → HTTP 500.
        final boolean showOverbookingSlots = readFlexibleBoolean(jsonObject, RequestParameterConstant.SHOW_OVERBOOKING_SLOTS.getLabel());
        final String duration = jsonObject.containsKey(RequestParameterConstant.AVAILABLE_DURATION_MINS.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.AVAILABLE_DURATION_MINS.getLabel()) : null;
        final String status = jsonObject.containsKey(RequestParameterConstant.STATUS.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.STATUS.getLabel()) : null;
        final String jurisdiction = jsonObject.containsKey(RequestParameterConstant.JURISDICTION.getLabel()) ?
                jsonObject.getString(RequestParameterConstant.JURISDICTION.getLabel()) : null;

        return new HearingSlotRequestParam(panel, startDate, endDate, exactHearingStartDateTime, ouLevel, ouCode, pageSize,
                pageNumber, courtRoomId, courtRoomNumber, businessType, courtSession, isSlotBased, hearingStartTime, showOverbookingSlots, duration, status, jurisdiction);
    }

    private static boolean readFlexibleBoolean(final JsonObject jsonObject, final String key) {
        if (!jsonObject.containsKey(key) || jsonObject.isNull(key)) {
            return false;
        }
        final jakarta.json.JsonValue value = jsonObject.get(key);
        if (value.getValueType() == jakarta.json.JsonValue.ValueType.TRUE) {
            return true;
        }
        if (value.getValueType() == jakarta.json.JsonValue.ValueType.FALSE) {
            return false;
        }
        return valueOf(jsonObject.getString(key));
    }
}
