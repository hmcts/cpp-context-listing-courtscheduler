package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import uk.gov.moj.cpp.courtscheduler.domain.AssignJudiciariesRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAssignment;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for building judiciary assignment and unassignment requests.
 */
@ApplicationScoped
public class JudiciaryAssignmentRequestHelper {

    private static final Logger logger = LoggerFactory.getLogger(JudiciaryAssignmentRequestHelper.class);

    /**
     * Converts a map of judiciary IDs to lists of court schedule UUIDs into an AssignJudiciariesRequest.
     *
     * @param judiciaryAssignmentMap map where key is judiciaryId (String) and value is list of court schedule UUIDs
     * @return AssignJudiciariesRequest containing the judiciary assignments
     */
    public AssignJudiciariesRequest buildAssignJudiciariesRequest(final Map<String, List<UUID>> judiciaryAssignmentMap) {
        logger.debug("Building AssignJudiciariesRequest from map with {} entries", judiciaryAssignmentMap.size());

        final List<JudiciaryAssignment> assignments = judiciaryAssignmentMap.entrySet().stream()
                .map(entry -> {
                    final String judiciaryId = entry.getKey();
                    final List<String> sessionIds = entry.getValue().stream()
                            .map(UUID::toString)
                            .toList();
                    return JudiciaryAssignment.builder()
                            .withJudiciaryId(judiciaryId)
                            .withSessionIds(sessionIds)
                            .build();
                })
                .toList();

        return AssignJudiciariesRequest.builder()
                .withJudiciaries(assignments)
                .build();
    }

    /**
     * Converts a map of judiciary IDs to lists of court schedule UUIDs into a map with String session IDs.
     *
     * @param judiciaryUnAssignmentMap map where key is judiciaryId (String) and value is list of court schedule UUIDs
     * @return Map where key is judiciaryId (String) and value is list of session IDs (String)
     */
    public Map<String, List<String>> convertToUnassignmentMap(final Map<String, List<UUID>> judiciaryUnAssignmentMap) {
        logger.debug("Converting unassignment map with {} entries", judiciaryUnAssignmentMap.size());

        return judiciaryUnAssignmentMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(UUID::toString)
                                .toList()
                ));
    }
}

