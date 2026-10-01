package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

/**
 * Represents a judiciary assignment with its associated court schedule data.
 *
 * @param judiciaryId the judiciary ID
 * @param scheduleData the court schedule data for this assignment
 */
public record JudiciaryScheduleAssignment(
        String judiciaryId,
        JudiciaryCourtScheduleData scheduleData
) {
}

