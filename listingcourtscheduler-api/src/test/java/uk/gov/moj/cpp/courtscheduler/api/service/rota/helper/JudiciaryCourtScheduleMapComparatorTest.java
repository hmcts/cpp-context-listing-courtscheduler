package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryCourtScheduleMapComparator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryCourtScheduleMapComparatorTest {

    @InjectMocks
    private JudiciaryCourtScheduleMapComparator comparator;

    private UUID scheduleId1;
    private UUID scheduleId2;
    private UUID scheduleId3;
    private UUID scheduleId4;
    private String judiciaryId1;
    private String judiciaryId2;

    @BeforeEach
    void setUp() {
        scheduleId1 = UUID.randomUUID();
        scheduleId2 = UUID.randomUUID();
        scheduleId3 = UUID.randomUUID();
        scheduleId4 = UUID.randomUUID();
        judiciaryId1 = "judiciary-1";
        judiciaryId2 = "judiciary-2";
    }

    @Test
    void shouldFindMissingCourtScheduleIdsInDB_WhenRotaFeedHasMoreIds() {
        // given
        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2, scheduleId3));
        rotaFeedMap.put(judiciaryId2, List.of(scheduleId4));

        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId1, List.of(scheduleId1)); // scheduleId2 and scheduleId3 are missing
        databaseMap.put(judiciaryId2, List.of(scheduleId4)); // no missing

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInDB(rotaFeedMap, databaseMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId1));
        assertThat(result.get(judiciaryId1), hasSize(2));
        assertTrue(result.get(judiciaryId1).contains(scheduleId2));
        assertTrue(result.get(judiciaryId1).contains(scheduleId3));
    }

    @Test
    void shouldFindMissingCourtScheduleIdsInDB_WhenDatabaseMapIsEmpty() {
        // given
        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2));

        Map<String, List<UUID>> databaseMap = emptyMap();

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInDB(rotaFeedMap, databaseMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId1));
        assertThat(result.get(judiciaryId1), hasSize(2));
    }

    @Test
    void shouldFindMissingCourtScheduleIdsInDB_WhenJudiciaryNotInDatabase() {
        // given
        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2));

        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId2, List.of(scheduleId3)); // different judiciary

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInDB(rotaFeedMap, databaseMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId1));
        assertThat(result.get(judiciaryId1), hasSize(2));
    }

    @Test
    void shouldReturnEmptyMap_WhenNoMissingIds() {
        // given
        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2));

        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2)); // all present

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInDB(rotaFeedMap, databaseMap);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyMap_WhenRotaFeedMapIsNull() {
        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInDB(null, new HashMap<>());

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyMap_WhenRotaFeedMapIsEmpty() {
        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInDB(emptyMap(), new HashMap<>());

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipEmptyLists_WhenRotaFeedHasEmptyList() {
        // given
        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId1, emptyList());
        rotaFeedMap.put(judiciaryId2, List.of(scheduleId1));

        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId2, emptyList());

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInDB(rotaFeedMap, databaseMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId2));
    }

    @Test
    void shouldFindMissingCourtScheduleIdsInRotaFeed_WhenDatabaseHasMoreIds() {
        // given
        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2, scheduleId3));
        databaseMap.put(judiciaryId2, List.of(scheduleId4));

        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId1, List.of(scheduleId1)); // scheduleId2 and scheduleId3 are missing
        rotaFeedMap.put(judiciaryId2, List.of(scheduleId4)); // no missing

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInRotaFeed(databaseMap, rotaFeedMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId1));
        assertThat(result.get(judiciaryId1), hasSize(2));
        assertTrue(result.get(judiciaryId1).contains(scheduleId2));
        assertTrue(result.get(judiciaryId1).contains(scheduleId3));
    }

    @Test
    void shouldFindMissingCourtScheduleIdsInRotaFeed_WhenRotaFeedMapIsEmpty() {
        // given
        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2));

        Map<String, List<UUID>> rotaFeedMap = emptyMap();

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInRotaFeed(databaseMap, rotaFeedMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId1));
        assertThat(result.get(judiciaryId1), hasSize(2));
    }

    @Test
    void shouldFindMissingCourtScheduleIdsInRotaFeed_WhenJudiciaryNotInRotaFeed() {
        // given
        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2));

        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId2, List.of(scheduleId3)); // different judiciary

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInRotaFeed(databaseMap, rotaFeedMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId1));
        assertThat(result.get(judiciaryId1), hasSize(2));
    }

    @Test
    void shouldReturnEmptyMap_WhenNoMissingIdsInRotaFeed() {
        // given
        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2));

        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId1, List.of(scheduleId1, scheduleId2)); // all present

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInRotaFeed(databaseMap, rotaFeedMap);

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyMap_WhenDatabaseMapIsNull() {
        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInRotaFeed(null, new HashMap<>());

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyMap_WhenDatabaseMapIsEmpty() {
        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInRotaFeed(emptyMap(), new HashMap<>());

        // then
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void shouldSkipEmptyLists_WhenDatabaseHasEmptyList() {
        // given
        Map<String, List<UUID>> databaseMap = new HashMap<>();
        databaseMap.put(judiciaryId1, emptyList());
        databaseMap.put(judiciaryId2, List.of(scheduleId1));

        Map<String, List<UUID>> rotaFeedMap = new HashMap<>();
        rotaFeedMap.put(judiciaryId2, emptyList());

        // when
        Map<String, List<UUID>> result = comparator.findMissingCourtScheduleIdsInRotaFeed(databaseMap, rotaFeedMap);

        // then
        assertNotNull(result);
        assertThat(result.size(), is(1));
        assertTrue(result.containsKey(judiciaryId2));
    }
}

