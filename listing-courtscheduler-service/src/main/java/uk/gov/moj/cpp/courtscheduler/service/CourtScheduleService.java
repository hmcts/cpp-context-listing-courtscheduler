package uk.gov.moj.cpp.courtscheduler.service;

import uk.gov.moj.cpp.courtscheduler.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.*;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

@ApplicationScoped
public class CourtScheduleService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public List<CourtSchedule> getCourtSchedules(CourtScheduleRequestParam courtScheduleRequestParam) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtScheduleRequestParam);
        return courtSchedules;
    }

    public Result update(UpdateCourtSchedule updateCourtSchedule) {
        return courtScheduleRepository.update(updateCourtSchedule);
    }

    public JsonObject deleteCourtScheduleSessions(final SessionsParam sessionsParam) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.deleteCourtSchedule(sessionsParam.getSessions());

        final ListToJsonArrayConverter<CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();
        JsonArray jsonArray = courtSchedules.isEmpty() ? JsonValue.EMPTY_JSON_ARRAY : listToJsonArrayConverter.convert(courtSchedules);
        return Json.createObjectBuilder()
                .add(RequestParameterConstant.SESSIONS.getLabel(), jsonArray)
                .build();
    }
}
