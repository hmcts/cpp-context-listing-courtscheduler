package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Record containing court schedule data for a judiciary assignment.
 *
 * @param courtScheduleIds the list of court schedule UUIDs
 * @param rotaJudiciaryId the rota judiciary ID
 * @param position the position of the judiciary
 * @param isBenchChairman whether the judiciary is a bench chairman
 * @param isDeputy whether the judiciary is a deputy
 */
public record JudiciaryCourtScheduleData(
        List<UUID> courtScheduleIds,
        String rotaJudiciaryId,
        String position,
        Boolean isBenchChairman,
        Boolean isDeputy
) {
}

