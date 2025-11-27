package uk.gov.moj.cpp.courtscheduler.api.service.helper;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleJudiciaryQueryHelperTest {

    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @InjectMocks
    private CourtScheduleJudiciaryQueryHelper queryHelper;

    private UUID scheduleId1;
    private UUID scheduleId2;
    private String judiciaryId1;
    private String judiciaryId2;

    @BeforeEach
    void setUp() {
        scheduleId1 = UUID.randomUUID();
        scheduleId2 = UUID.randomUUID();
        judiciaryId1 = "judiciary-1";
        judiciaryId2 = "judiciary-2";
    }

    @Test
    void shouldQueryAndGroupCourtScheduleIdsByJudiciaryId() {
        // given
        Map<String, List<UUID>> inputMap = new HashMap<>();
        inputMap.put(judiciaryId1, List.of(scheduleId1));
        inputMap.put(judiciaryId2, List.of(scheduleId2));

        List<CourtScheduleJudiciary> entities = new ArrayList<>();
        entities.add(createCourtScheduleJudiciary(judiciaryId1, scheduleId1.toString()));
        entities.add(createCourtScheduleJudiciary(judiciaryId1, scheduleId2.toString()));
        entities.add(createCourtScheduleJudiciary(judiciaryId2, scheduleId1.toString()));

        when(courtScheduleJudiciaryRepository.findByJudiciaryIds(anyList())).thenReturn(entities);

        // when
        Map<String, List<UUID>> result = queryHelper.queryCourtScheduleIdsByJudiciaryIds(inputMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(2));
        assertTrue(result.containsKey(judiciaryId1));
        assertTrue(result.containsKey(judiciaryId2));
        assertThat(result.get(judiciaryId1), hasSize(2));
        assertThat(result.get(judiciaryId2), hasSize(1));
    }

    @Test
    void shouldReturnEmptyMap_WhenInputMapIsNull() {
        // when
        Map<String, List<UUID>> result = queryHelper.queryCourtScheduleIdsByJudiciaryIds(null);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(courtScheduleJudiciaryRepository, never()).findByJudiciaryIds(anyList());
    }

    @Test
    void shouldReturnEmptyMap_WhenInputMapIsEmpty() {
        // when
        Map<String, List<UUID>> result = queryHelper.queryCourtScheduleIdsByJudiciaryIds(emptyMap());

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(courtScheduleJudiciaryRepository, never()).findByJudiciaryIds(anyList());
    }

    @Test
    void shouldReturnEmptyMap_WhenRepositoryReturnsEmptyList() {
        // given
        Map<String, List<UUID>> inputMap = new HashMap<>();
        inputMap.put(judiciaryId1, List.of(scheduleId1));

        when(courtScheduleJudiciaryRepository.findByJudiciaryIds(anyList())).thenReturn(emptyList());

        // when
        Map<String, List<UUID>> result = queryHelper.queryCourtScheduleIdsByJudiciaryIds(inputMap);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldHandleInvalidUUIDFormat() {
        // given
        Map<String, List<UUID>> inputMap = new HashMap<>();
        inputMap.put(judiciaryId1, List.of(scheduleId1));

        List<CourtScheduleJudiciary> entities = new ArrayList<>();
        entities.add(createCourtScheduleJudiciary(judiciaryId1, "invalid-uuid"));
        entities.add(createCourtScheduleJudiciary(judiciaryId1, scheduleId1.toString()));

        when(courtScheduleJudiciaryRepository.findByJudiciaryIds(anyList())).thenReturn(entities);

        // when
        Map<String, List<UUID>> result = queryHelper.queryCourtScheduleIdsByJudiciaryIds(inputMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId1));
        assertThat(result.get(judiciaryId1), hasSize(1)); // Only valid UUID is added
        assertTrue(result.get(judiciaryId1).contains(scheduleId1));
    }

    @Test
    void shouldHandleExceptionFromRepository() {
        // given
        Map<String, List<UUID>> inputMap = new HashMap<>();
        inputMap.put(judiciaryId1, List.of(scheduleId1));

        when(courtScheduleJudiciaryRepository.findByJudiciaryIds(anyList()))
                .thenThrow(new RuntimeException("Database error"));

        // when
        Map<String, List<UUID>> result = queryHelper.queryCourtScheduleIdsByJudiciaryIds(inputMap);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGroupMultipleCourtSchedulesForSameJudiciary() {
        // given
        Map<String, List<UUID>> inputMap = new HashMap<>();
        inputMap.put(judiciaryId1, List.of(scheduleId1));

        List<CourtScheduleJudiciary> entities = new ArrayList<>();
        entities.add(createCourtScheduleJudiciary(judiciaryId1, scheduleId1.toString()));
        entities.add(createCourtScheduleJudiciary(judiciaryId1, scheduleId2.toString()));
        entities.add(createCourtScheduleJudiciary(judiciaryId1, UUID.randomUUID().toString()));

        when(courtScheduleJudiciaryRepository.findByJudiciaryIds(anyList())).thenReturn(entities);

        // when
        Map<String, List<UUID>> result = queryHelper.queryCourtScheduleIdsByJudiciaryIds(inputMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId1));
        assertThat(result.get(judiciaryId1), hasSize(3));
    }

    private CourtScheduleJudiciary createCourtScheduleJudiciary(String judiciaryId, String courtScheduleId) {
        CourtScheduleJudiciary entity = new CourtScheduleJudiciary();
        CourtScheduleJudiciaryKey key = new CourtScheduleJudiciaryKey();
        key.setJudiciaryId(judiciaryId);
        key.setCourtScheduleId(courtScheduleId);
        entity.setId(key);
        return entity;
    }
}

