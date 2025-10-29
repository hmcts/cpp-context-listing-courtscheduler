package uk.gov.moj.cpp.courtscheduler.api.converter;

import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_SCHEDULE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_SCHEDULE_ID_LIST;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.DURATION;

import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.ValidateSessionAvailabilityRequestParam.ValidateSessionAvailabilityRequestParamBuilder;

import java.util.ArrayList;
import java.util.List;

import javax.json.JsonObject;
import javax.json.JsonValue;

public class ValidateSessionAvailabilityRequestParamConverter implements Converter<JsonObject, ValidateSessionAvailabilityRequestParam> {

    @Override
    public ValidateSessionAvailabilityRequestParam convert(final JsonObject jsonObject) {
        List<String> courtSessionIds = new ArrayList<>();
        final String courtScheduleIdListLabel = COURT_SCHEDULE_ID_LIST.getLabel();
        if (jsonObject.containsKey(courtScheduleIdListLabel) && jsonObject.getJsonArray(courtScheduleIdListLabel) != null) {
            for (JsonValue jsonValue : jsonObject.getJsonArray(courtScheduleIdListLabel)) {
                JsonObject jsonObj = (JsonObject) jsonValue;
                courtSessionIds.add(jsonObj.getString(COURT_SCHEDULE_ID.getLabel()));
            }
        }
        final ValidateSessionAvailabilityRequestParamBuilder paramBuilder = ValidateSessionAvailabilityRequestParamBuilder.validateSessionAvailabilityRequestParam();
        paramBuilder.withCourtScheduleIds(courtSessionIds);
        final String durationLabel = DURATION.getLabel();
        if (jsonObject.containsKey(durationLabel) && jsonObject.get(durationLabel) != null) {
            paramBuilder.withSlotsOrDuration(
                    jsonObject.getInt(durationLabel)
            );
        }
        return paramBuilder.build();
    }
}