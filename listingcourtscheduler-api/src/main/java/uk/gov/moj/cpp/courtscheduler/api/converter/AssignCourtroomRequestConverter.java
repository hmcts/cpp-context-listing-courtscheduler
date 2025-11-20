package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.json.JsonArray;
import javax.json.JsonObject;

public class AssignCourtroomRequestConverter implements Converter<JsonObject, AssignCourtroomRequest> {
    @Override
    public AssignCourtroomRequest convert(final JsonObject jsonObject) {
        List<String> courtScheduleIds = new ArrayList<>();
        
        if (jsonObject.containsKey(RequestParameterConstant.COURT_SCHEDULE_IDS.getLabel()) && jsonObject.get(RequestParameterConstant.COURT_SCHEDULE_IDS.getLabel()) != null) {
            JsonArray idsArray = jsonObject.getJsonArray(RequestParameterConstant.COURT_SCHEDULE_IDS.getLabel());
            if (idsArray != null) {
                courtScheduleIds = idsArray.stream()
                        .map(value -> value.toString().replace("\"", ""))
                        .toList();
//                        .collect(Collectors.toList());
            }
        }
        
        String courtRoomId = jsonObject.containsKey(RequestParameterConstant.COURT_ROOM.getLabel())
                ? jsonObject.getString(RequestParameterConstant.COURT_ROOM.getLabel(), null)
                : null;

        return AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(courtScheduleIds)
                .withCourtRoomId(courtRoomId)
                .build();
    }
}


