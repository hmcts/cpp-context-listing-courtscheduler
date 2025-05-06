package uk.gov.moj.cpp.courtscheduler.api.converter;

import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_SCHEDULE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.COURT_SCHEDULE_ID_LIST;

import uk.gov.moj.cpp.courtscheduler.domain.SearchCourtSchedulesByIdRequestParam;

import java.util.ArrayList;
import java.util.List;

import javax.json.JsonObject;
import javax.json.JsonValue;

public class SearchCourtSchedulesByIdRequestParamConverter implements Converter<JsonObject, SearchCourtSchedulesByIdRequestParam> {

    @Override
    public SearchCourtSchedulesByIdRequestParam convert(final JsonObject jsonObject) {
        List<String> courtSessionIds = new ArrayList<>();
        final String courtScheduleIdListLabel = COURT_SCHEDULE_ID_LIST.getLabel();
        if (jsonObject.containsKey(courtScheduleIdListLabel) && jsonObject.getJsonArray(courtScheduleIdListLabel) != null) {
            for (JsonValue jsonValue : jsonObject.getJsonArray(courtScheduleIdListLabel)) {
                JsonObject jsonObj = (JsonObject) jsonValue;
                courtSessionIds.add(jsonObj.getString(COURT_SCHEDULE_ID.getLabel()));
            }
        }
        final SearchCourtSchedulesByIdRequestParam.SearchCourtSchedulesByIdRequestParamBuilder paramBuilder = SearchCourtSchedulesByIdRequestParam.SearchCourtSchedulesByIdRequestParamBuilder.searchCourtSchedulesByIdRequestParamBuilder();
        paramBuilder.withCourtScheduleIds(courtSessionIds);
        return paramBuilder.build();
    }
}