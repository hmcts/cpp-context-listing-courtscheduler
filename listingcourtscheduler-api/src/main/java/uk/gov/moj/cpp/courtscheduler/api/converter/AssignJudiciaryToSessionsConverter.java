package uk.gov.moj.cpp.courtscheduler.api.converter;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciaryToSessionsRequest;
import uk.gov.moj.cpp.courtscheduler.domain.SessionJudiciary;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

@Service
public class AssignJudiciaryToSessionsConverter {

    private static final String COURT_SCHEDULE_IDS = "courtScheduleIds";
    private static final String JUDICIARY = "judiciary";
    private static final String JUDICIAL_ID = "judicialId";
    private static final String JUDICIAL_ROLE_TYPE = "judicialRoleType";
    private static final String JUDICIARY_TYPE = "judiciaryType";
    private static final String IS_DEPUTY = "isDeputy";
    private static final String IS_BENCH_CHAIRMAN = "isBenchChairman";

    public AssignJudiciaryToSessionsRequest convert(final JsonObject payload) {
        final AssignJudiciaryToSessionsRequest.Builder builder = AssignJudiciaryToSessionsRequest.builder();
        if (payload == null) {
            return builder.build();
        }
        builder.withCourtScheduleIds(extractCourtScheduleIds(payload));
        builder.withJudiciary(extractJudiciary(payload));
        return builder.build();
    }

    private List<String> extractCourtScheduleIds(final JsonObject payload) {
        if (!payload.containsKey(COURT_SCHEDULE_IDS) || payload.isNull(COURT_SCHEDULE_IDS)) {
            return List.of();
        }
        final JsonArray arr = payload.getJsonArray(COURT_SCHEDULE_IDS);
        if (arr == null) {
            return List.of();
        }
        return arr.stream()
                .filter(v -> v.getValueType() == JsonValue.ValueType.STRING)
                .map(JsonString.class::cast)
                .map(JsonString::getString)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .filter(this::isUuid)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<SessionJudiciary> extractJudiciary(final JsonObject payload) {
        if (!payload.containsKey(JUDICIARY) || payload.isNull(JUDICIARY)) {
            return List.of();
        }
        final JsonArray arr = payload.getJsonArray(JUDICIARY);
        if (arr == null) {
            return List.of();
        }
        final List<SessionJudiciary> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            final JsonValue v = arr.get(i);
            if (v.getValueType() != JsonValue.ValueType.OBJECT) {
                continue;
            }
            out.add(toSessionJudiciary(v.asJsonObject()));
        }
        return out;
    }

    private SessionJudiciary toSessionJudiciary(final JsonObject o) {
        final String judicialId = o.containsKey(JUDICIAL_ID) && !o.isNull(JUDICIAL_ID)
                ? o.getString(JUDICIAL_ID, null)
                : null;
        final String judiciaryType = extractJudiciaryType(o);
        final Boolean isDeputy = o.containsKey(IS_DEPUTY) && !o.isNull(IS_DEPUTY)
                ? o.getBoolean(IS_DEPUTY)
                : null;
        final Boolean isBenchChairman = o.containsKey(IS_BENCH_CHAIRMAN) && !o.isNull(IS_BENCH_CHAIRMAN)
                ? o.getBoolean(IS_BENCH_CHAIRMAN)
                : null;
        return SessionJudiciary.builder()
                .withJudicialId(judicialId)
                .withJudiciaryType(judiciaryType)
                .withIsDeputy(isDeputy)
                .withIsBenchChairman(isBenchChairman)
                .build();
    }

    private String extractJudiciaryType(final JsonObject judiciaryObject) {
        if (!judiciaryObject.containsKey(JUDICIAL_ROLE_TYPE) || judiciaryObject.isNull(JUDICIAL_ROLE_TYPE)) {
            return null;
        }
        final JsonObject roleType = judiciaryObject.getJsonObject(JUDICIAL_ROLE_TYPE);
        if (roleType == null) {
            return null;
        }
        if (!roleType.containsKey(JUDICIARY_TYPE) || roleType.isNull(JUDICIARY_TYPE)) {
            return null;
        }
        return roleType.getString(JUDICIARY_TYPE, null);
    }

    private boolean isUuid(final String s) {
        if (s == null) {
            return false;
        }
        try {
            UUID.fromString(s);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
