package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

@Service
public class AssignJudiciariesRequestConverter {

    private static final String JUDICIARIES = "judiciaries";
    private static final String JUDICIARY_ID = "judiciaryId";
    private static final String SESSION_IDS = "sessionIds";
    private static final String SKIP_VALIDATIONS = "skipValidations";
    private static final String IS_DEPUTY = "isDeputy";
    private static final String IS_BENCH_CHAIRMAN = "isBenchChairman";
    private static final String POSITION = "position";

    public AssignJudiciariesRequest convert(final JsonObject payload) {
        if (payload == null || !payload.containsKey(JUDICIARIES)) {
            return AssignJudiciariesRequest.builder().build();
        }
        final JsonArray judiciariesArray = payload.getJsonArray(JUDICIARIES);
        final List<JudiciaryAssignment> assignments = judiciariesArray == null
                ? List.of()
                : judiciariesArray.stream()
                .filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast)
                .map(this::toAssignment)
                .collect(Collectors.toList());

        final boolean skipValidations = payload.containsKey(SKIP_VALIDATIONS) && payload.getBoolean(SKIP_VALIDATIONS);

        return AssignJudiciariesRequest.builder()
                .withJudiciaries(assignments)
                .withSkipValidations(skipValidations)
                .build();
    }

    private JudiciaryAssignment toAssignment(final JsonObject jsonObject) {
        final String judiciaryId = jsonObject.getString(JUDICIARY_ID, null);
        final List<String> sessionIds = extractSessionIds(jsonObject.getJsonArray(SESSION_IDS));
        final Boolean isDeputy = jsonObject.containsKey(IS_DEPUTY) && !jsonObject.isNull(IS_DEPUTY) 
                ? jsonObject.getBoolean(IS_DEPUTY) : null;
        final Boolean isBenchChairman = jsonObject.containsKey(IS_BENCH_CHAIRMAN) && !jsonObject.isNull(IS_BENCH_CHAIRMAN) 
                ? jsonObject.getBoolean(IS_BENCH_CHAIRMAN) : null;
        final String position = jsonObject.containsKey(POSITION) && !jsonObject.isNull(POSITION) 
                ? jsonObject.getString(POSITION, null) : null;
        return JudiciaryAssignment.builder()
                .withJudiciaryId(judiciaryId)
                .withSessionIds(sessionIds)
                .withIsDeputy(isDeputy)
                .withIsBenchChairman(isBenchChairman)
                .withPosition(position)
                .build();
    }

    private List<String> extractSessionIds(final JsonArray sessionIdsArray) {
        if (sessionIdsArray == null) {
            return List.of();
        }
        return sessionIdsArray.stream()
                .filter(jsonValue -> jsonValue.getValueType() == JsonValue.ValueType.STRING)
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}

