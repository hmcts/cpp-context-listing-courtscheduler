package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.AssignCourtroomRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.json.JsonArray;
import javax.json.JsonObject;

public class AssignCourtroomRequestConverter implements Converter<JsonObject, AssignCourtroomRequest> {
    @Override
    public AssignCourtroomRequest convert(final JsonObject jsonObject) {
        List<String> courtScheduleIds = new ArrayList<>();
        
        if (jsonObject.containsKey("courtScheduleIds") && jsonObject.get("courtScheduleIds") != null) {
            JsonArray idsArray = jsonObject.getJsonArray("courtScheduleIds");
            if (idsArray != null) {
                courtScheduleIds = idsArray.stream()
                        .map(value -> value.toString().replace("\"", ""))
                        .collect(Collectors.toList());
            }
        }
        
        String courtRoomId = jsonObject.containsKey("courtRoomId") 
                ? jsonObject.getString("courtRoomId", null) 
                : null;

        return AssignCourtroomRequest.AssignCourtroomRequestBuilder
                .assignCourtroomRequestBuilder()
                .withCourtScheduleIds(courtScheduleIds)
                .withCourtRoomId(courtRoomId)
                .build();
    }
}


