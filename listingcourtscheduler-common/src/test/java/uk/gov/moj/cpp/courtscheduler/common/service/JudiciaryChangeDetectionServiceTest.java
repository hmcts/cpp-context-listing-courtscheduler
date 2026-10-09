package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryChangeDetectionServiceTest {

    private static final String JUDGE_1 = "judge-1";

    @InjectMocks
    private JudiciaryChangeDetectionService judiciaryChangeDetectionService;

    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Test
    void buildHashMap_shouldReturnEmptyMapAndSkipRepositoryCallWhenIdListIsEmpty() {
        final Map<String, List<Integer>> result =
                judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(List.of());

        assertTrue(result.isEmpty());
        verify(courtScheduleJudiciaryRepository, never()).findInCourtScheduleIds(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void buildHashMap_shouldGroupReturnedEntitiesByCourtScheduleId() {
        final String scheduleId = "schedule-1";
        final CourtScheduleJudiciary entity = buildEntity(scheduleId, JUDGE_1);

        when(courtScheduleJudiciaryRepository.findInCourtScheduleIds(List.of(scheduleId)))
                .thenReturn(List.of(entity));

        final Map<String, List<Integer>> result =
                judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(List.of(scheduleId));

        assertTrue(result.containsKey(scheduleId));
        assertEquals(1, result.get(scheduleId).size());
    }

    @Test
    void buildHashMap_shouldProduceSameHashForIdenticalEntities() {
        final String scheduleId = "schedule-1";

        when(courtScheduleJudiciaryRepository.findInCourtScheduleIds(List.of(scheduleId)))
                .thenReturn(List.of(buildEntity(scheduleId, JUDGE_1)))
                .thenReturn(List.of(buildEntity(scheduleId, JUDGE_1)));

        final Map<String, List<Integer>> result1 =
                judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(List.of(scheduleId));
        final Map<String, List<Integer>> result2 =
                judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(List.of(scheduleId));

        assertEquals(result1.get(scheduleId), result2.get(scheduleId));
    }

    @Test
    void buildHashMap_shouldProduceDifferentHashesForDifferentJudiciaryIds() {
        final String scheduleId = "schedule-1";

        when(courtScheduleJudiciaryRepository.findInCourtScheduleIds(List.of(scheduleId)))
                .thenReturn(List.of(buildEntity(scheduleId, JUDGE_1)))
                .thenReturn(List.of(buildEntity(scheduleId, "judge-2")));

        final Map<String, List<Integer>> result1 =
                judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(List.of(scheduleId));
        final Map<String, List<Integer>> result2 =
                judiciaryChangeDetectionService.buildCourtScheduleJudiciaryHashMap(List.of(scheduleId));

        assertFalse(result1.get(scheduleId).equals(result2.get(scheduleId)));
    }


    @Test
    void findChanged_shouldReturnEmptyWhenBothMapsAreEmpty() {
        final List<String> changed =
                judiciaryChangeDetectionService.findChangedCourtScheduleIds(Map.of(), Map.of());

        assertTrue(changed.isEmpty());
    }

    @Test
    void findChanged_shouldReturnIdWhenHashesDiffer() {
        final Map<String, List<Integer>> pre = Map.of("s1", List.of(1));
        final Map<String, List<Integer>> post = Map.of("s1", List.of(2));

        final List<String> changed = judiciaryChangeDetectionService.findChangedCourtScheduleIds(pre, post);

        assertEquals(1, changed.size());
        assertTrue(changed.contains("s1"));
    }

    @Test
    void findChanged_shouldNotReturnIdWhenHashesAreIdentical() {
        final Map<String, List<Integer>> pre = Map.of("s1", List.of(42));
        final Map<String, List<Integer>> post = Map.of("s1", List.of(42));

        final List<String> changed = judiciaryChangeDetectionService.findChangedCourtScheduleIds(pre, post);

        assertTrue(changed.isEmpty());
    }

    @Test
    void findChanged_shouldNotReturnIdWhenHashesSameButInDifferentOrder() {
        final Map<String, List<Integer>> pre = Map.of("s1", List.of(1, 2));
        final Map<String, List<Integer>> post = Map.of("s1", List.of(2, 1));

        final List<String> changed = judiciaryChangeDetectionService.findChangedCourtScheduleIds(pre, post);

        assertTrue(changed.isEmpty());
    }

    @Test
    void findChanged_shouldReturnIdWhenOnlyPresentInPreMap() {
        final Map<String, List<Integer>> pre = Map.of("s1", List.of(1));
        final Map<String, List<Integer>> post = Map.of();

        final List<String> changed = judiciaryChangeDetectionService.findChangedCourtScheduleIds(pre, post);

        assertTrue(changed.contains("s1"));
    }

    @Test
    void findChanged_shouldReturnIdWhenOnlyPresentInPostMap() {
        final Map<String, List<Integer>> pre = Map.of();
        final Map<String, List<Integer>> post = Map.of("s1", List.of(1));

        final List<String> changed = judiciaryChangeDetectionService.findChangedCourtScheduleIds(pre, post);

        assertTrue(changed.contains("s1"));
    }

    @Test
    void findChanged_shouldReturnIdWhenHashListSizesDiffer() {
        final Map<String, List<Integer>> pre = Map.of("s1", List.of(1, 2));
        final Map<String, List<Integer>> post = Map.of("s1", List.of(1));

        final List<String> changed = judiciaryChangeDetectionService.findChangedCourtScheduleIds(pre, post);

        assertTrue(changed.contains("s1"));
    }

    @Test
    void findChanged_shouldReturnOnlyIdsWhoseHashesDiffer() {
        final Map<String, List<Integer>> pre = Map.of(
                "s1", List.of(1),
                "s2", List.of(3));
        final Map<String, List<Integer>> post = Map.of(
                "s1", List.of(2),
                "s2", List.of(3));

        final List<String> changed = judiciaryChangeDetectionService.findChangedCourtScheduleIds(pre, post);

        assertEquals(1, changed.size());
        assertTrue(changed.contains("s1"));
        assertFalse(changed.contains("s2"));
    }


    private CourtScheduleJudiciary buildEntity(final String courtScheduleId, final String judiciaryId) {
        return CourtScheduleJudiciary.CourtScheduleJudiciaryBuilder.courtScheduleJudiciary()
                .withId(new CourtScheduleJudiciaryKey(courtScheduleId, judiciaryId))
                .withTitle("HHJ")
                .withForenames("Test")
                .withSurname("Judge")
                .withEmail("test@example.com")
                .withJudiciaryType("CHAIR")
                .withIsBenchChairman(false)
                .withIsDeputy(false)
                .withActive(true)
                .build();
    }
}