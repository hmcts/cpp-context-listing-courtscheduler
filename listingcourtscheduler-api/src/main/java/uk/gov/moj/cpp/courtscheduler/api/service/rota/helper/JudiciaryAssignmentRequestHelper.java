package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for building judiciary assignment requests.
 */
@ApplicationScoped
public class JudiciaryAssignmentRequestHelper {

    private static final Logger logger = LoggerFactory.getLogger(JudiciaryAssignmentRequestHelper.class);

    /**
     * Converts a map of judiciary IDs to JudiciaryCourtScheduleData into an AssignJudiciariesRequest.
     *
     * @param judiciaryAssignmentDataMap map where key is judiciaryId (String) and value is JudiciaryCourtScheduleData
     * @return AssignJudiciariesRequest containing the judiciary assignments
     */
    public AssignJudiciariesRequest buildAssignJudiciariesRequest(
            final Map<String, JudiciaryCourtScheduleData> judiciaryAssignmentDataMap) {
        logger.info("Building AssignJudiciariesRequest from map with {} entries", judiciaryAssignmentDataMap.size());

        final List<JudiciaryAssignment> assignments = judiciaryAssignmentDataMap.entrySet().stream()
                .map(entry -> buildJudiciaryAssignment(entry.getKey(), entry.getValue()))
                .toList();

        final int totalSessionIds = assignments.stream()
                .mapToInt(a -> a.getSessionIds().size())
                .sum();
        logger.info("Built AssignJudiciariesRequest with {} judiciary assignments and {} total session IDs", 
                assignments.size(), totalSessionIds);

        return AssignJudiciariesRequest.builder()
                .withJudiciaries(assignments)
                .withSkipValidations(true)
                .build();
    }

    /**
     * Builds a JudiciaryAssignment from judiciary ID and court schedule data.
     *
     * @param judiciaryId the judiciary ID
     * @param data the court schedule data
     * @return JudiciaryAssignment object
     */
    private JudiciaryAssignment buildJudiciaryAssignment(final String judiciaryId, final JudiciaryCourtScheduleData data) {
        final List<String> sessionIds = data.courtScheduleIds().stream()
                .map(UUID::toString)
                .toList();
        return JudiciaryAssignment.builder()
                .withJudiciaryId(judiciaryId)
                .withSessionIds(sessionIds)
                .withPosition(data.position())
                .withIsBenchChairman(data.isBenchChairman())
                .withIsDeputy(data.isDeputy())
                .build();
    }
}

