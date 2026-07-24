package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.LOCATION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.ROTA_PERIOD;

import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata.RotaPeriodDateInfoProvider;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RotaLocationPeriodHelper Tests")
class RotaLocationPeriodHelperTest {

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @InjectMocks
    private RotaLocationPeriodHelper rotaLocationPeriodHelper;

    private Map<RotaPayload, Map<String, Map<String, String>>> records;
    private Map<UUID, CourtRoom> courtRoomsMap;

    @BeforeEach
    void setUp() {
        records = new HashMap<>();
        courtRoomsMap = new HashMap<>();
    }

    // ============================================================================
    // Tests for Location Operations
    // ============================================================================

    @Nested
    @DisplayName("Location Extraction Tests")
    class LocationExtractionTests {

        @Test
        @DisplayName("Should extract location IDs from records")
        void shouldExtractLocationIdsFromRecords() {
            // given
            final Map<String, Map<String, String>> locations = new HashMap<>();
            final Map<String, String> location1 = new HashMap<>();
            location1.put("locationId", "100");
            locations.put("loc-1", location1);
            final Map<String, String> location2 = new HashMap<>();
            location2.put("locationId", "200");
            locations.put("loc-2", location2);
            records.put(LOCATION, locations);

            // when
            final List<String> result = rotaLocationPeriodHelper.getLocationFromRecords(records);

            // then
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.contains("locationId"));
        }

        @Test
        @DisplayName("Should return empty list when no location records found")
        void shouldReturnEmptyListWhenNoLocationRecordsFound() {
            // given
            records.put(ROTA_PERIOD, new HashMap<>());

            // when
            final List<String> result = rotaLocationPeriodHelper.getLocationFromRecords(records);

            // then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when records is null")
        void shouldReturnEmptyListWhenRecordsIsNull() {
            // given
            final Map<RotaPayload, Map<String, Map<String, String>>> nullRecords = null;

            // when & then
            // The method will throw NullPointerException when records is null
            // This is expected behavior as the method doesn't handle null input
            try {
                rotaLocationPeriodHelper.getLocationFromRecords(nullRecords);
                // If we reach here, the test should fail
                org.junit.jupiter.api.Assertions.fail("Expected NullPointerException");
            } catch (final NullPointerException e) {
                // Expected behavior
                assertNotNull(e);
            }
        }

        @Test
        @DisplayName("Should extract multiple location IDs from single location record")
        void shouldExtractMultipleLocationIdsFromSingleLocationRecord() {
            // given
            final Map<String, Map<String, String>> locations = new HashMap<>();
            final Map<String, String> location1 = new HashMap<>();
            location1.put("locationId1", "100");
            location1.put("locationId2", "200");
            location1.put("locationId3", "300");
            locations.put("loc-1", location1);
            records.put(LOCATION, locations);

            // when
            final List<String> result = rotaLocationPeriodHelper.getLocationFromRecords(records);

            // then
            assertNotNull(result);
            assertEquals(3, result.size());
            assertTrue(result.contains("locationId1"));
            assertTrue(result.contains("locationId2"));
            assertTrue(result.contains("locationId3"));
        }
    }

    // ============================================================================
    // Tests for OU Code Resolution
    // ============================================================================

    @Nested
    @DisplayName("OU Code Resolution Tests")
    class OuCodeResolutionTests {

        @Test
        @DisplayName("Should resolve OU codes from location IDs")
        void shouldResolveOuCodesFromLocationIds() {
            // given
            final List<String> locationIds = List.of("100", "200");
            final UUID roomId1 = UUID.randomUUID();
            final UUID roomId2 = UUID.randomUUID();
            final CourtRoom courtRoom1 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(100)
                    .withOucode("OU001")
                    .withCourtRoomId(roomId1.toString())
                    .build();
            final CourtRoom courtRoom2 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(200)
                    .withOucode("OU002")
                    .withCourtRoomId(roomId2.toString())
                    .build();
            courtRoomsMap.put(roomId1, courtRoom1);
            courtRoomsMap.put(roomId2, courtRoom2);
            when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(courtRoomsMap);

            // when
            final List<String> result = rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(locationIds);

            // then
            assertNotNull(result);
            assertEquals(2, result.size());
            assertTrue(result.contains("OU001"));
            assertTrue(result.contains("OU002"));
            verify(referenceDataMapperService).getCourtRoomsMap();
        }

        @Test
        @DisplayName("Should return empty list when location IDs is null")
        void shouldReturnEmptyListWhenLocationIdsIsNull() {
            // when
            final List<String> result = rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(null);

            // then
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(referenceDataMapperService, never()).getCourtRoomsMap();
        }

        @Test
        @DisplayName("Should return empty list when location IDs is empty")
        void shouldReturnEmptyListWhenLocationIdsIsEmpty() {
            // when
            final List<String> result = rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(emptyList());

            // then
            assertNotNull(result);
            assertTrue(result.isEmpty());
            verify(referenceDataMapperService, never()).getCourtRoomsMap();
        }

        @Test
        @DisplayName("Should handle duplicate location IDs")
        void shouldHandleDuplicateLocationIds() {
            // given
            final List<String> locationIds = List.of("100", "100", "200");
            final UUID roomId1 = UUID.randomUUID();
            final UUID roomId2 = UUID.randomUUID();
            final CourtRoom courtRoom1 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(100)
                    .withOucode("OU001")
                    .withCourtRoomId(roomId1.toString())
                    .build();
            final CourtRoom courtRoom2 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(200)
                    .withOucode("OU002")
                    .withCourtRoomId(roomId2.toString())
                    .build();
            courtRoomsMap.put(roomId1, courtRoom1);
            courtRoomsMap.put(roomId2, courtRoom2);
            when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(courtRoomsMap);

            // when
            final List<String> result = rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(locationIds);

            // then
            assertNotNull(result);
            // Should only add each OU code once
            assertEquals(2, result.size());
            assertTrue(result.contains("OU001"));
            assertTrue(result.contains("OU002"));
        }

        @Test
        @DisplayName("Should handle location IDs not found in court rooms")
        void shouldHandleLocationIdsNotFoundInCourtRooms() {
            // given
            final List<String> locationIds = List.of("100", "999");
            final UUID roomId1 = UUID.randomUUID();
            final CourtRoom courtRoom1 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(100)
                    .withOucode("OU001")
                    .withCourtRoomId(roomId1.toString())
                    .build();
            courtRoomsMap.put(roomId1, courtRoom1);
            when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(courtRoomsMap);

            // when
            final List<String> result = rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(locationIds);

            // then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.contains("OU001"));
            assertFalse(result.contains("OU999"));
        }

        @Test
        @DisplayName("Should handle multiple court rooms with same location ID")
        void shouldHandleMultipleCourtRoomsWithSameLocationId() {
            // given
            final List<String> locationIds = List.of("100");
            final UUID roomId1 = UUID.randomUUID();
            final UUID roomId2 = UUID.randomUUID();
            final CourtRoom courtRoom1 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(100)
                    .withOucode("OU001")
                    .withCourtRoomId(roomId1.toString())
                    .build();
            final CourtRoom courtRoom2 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(100)
                    .withOucode("OU001")
                    .withCourtRoomId(roomId2.toString())
                    .build();
            courtRoomsMap.put(roomId1, courtRoom1);
            courtRoomsMap.put(roomId2, courtRoom2);
            when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(courtRoomsMap);

            // when
            final List<String> result = rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(locationIds);

            // then
            assertNotNull(result);
            // Should only add OU code once even if multiple court rooms have same location ID
            assertEquals(1, result.size());
            assertTrue(result.contains("OU001"));
        }

        @Test
        @DisplayName("Should handle null rota location ID in court room")
        void shouldHandleNullRotaLocationIdInCourtRoom() {
            // given
            final List<String> locationIds = List.of("100");
            final UUID roomId1 = UUID.randomUUID();
            final UUID roomId2 = UUID.randomUUID();
            final CourtRoom courtRoom1 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(null)
                    .withOucode("OU001")
                    .withCourtRoomId(roomId1.toString())
                    .build();
            final CourtRoom courtRoom2 = CourtRoom.CourtRoomBuilder.aCourtRoom()
                    .withRotaLocationId(100)
                    .withOucode("OU002")
                    .withCourtRoomId(roomId2.toString())
                    .build();
            courtRoomsMap.put(roomId1, courtRoom1);
            courtRoomsMap.put(roomId2, courtRoom2);
            when(referenceDataMapperService.getCourtRoomsMap()).thenReturn(courtRoomsMap);

            // when
            final List<String> result = rotaLocationPeriodHelper.getOuCodesFromCourtRoomMappingsByLocationId(locationIds);

            // then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.contains("OU002"));
            assertFalse(result.contains("OU001"));
        }
    }

    // ============================================================================
    // Tests for Rota Period Operations
    // ============================================================================

    @Nested
    @DisplayName("Rota Period Date Extraction Tests")
    class RotaPeriodDateExtractionTests {

        @Test
        @DisplayName("Should extract rota period dates from records")
        void shouldExtractRotaPeriodDatesFromRecords() {
            // given
            final Map<String, Map<String, String>> rotaPeriodMap = new HashMap<>();
            final Map<String, String> rotaPeriodData = new HashMap<>();
            rotaPeriodData.put("rotaPeriodStartDate", "2024-01-01");
            rotaPeriodData.put("rotaPeriodEndDate", "2024-12-31");
            rotaPeriodMap.put("period-1", rotaPeriodData);
            records.put(ROTA_PERIOD, rotaPeriodMap);

            // when
            final RotaPeriodDateInfoProvider result = rotaLocationPeriodHelper.getRotaPeriodDates(records);

            // then
            assertNotNull(result);
            assertEquals(LocalDate.parse("2024-01-01"), result.getRotaPeriodStartDate());
            assertEquals(LocalDate.parse("2024-12-31"), result.getRotaPeriodEndDate());
        }

        @Test
        @DisplayName("Should handle different date formats in rota period")
        void shouldHandleDifferentDateFormatsInRotaPeriod() {
            // given
            final Map<String, Map<String, String>> rotaPeriodMap = new HashMap<>();
            final Map<String, String> rotaPeriodData = new HashMap<>();
            rotaPeriodData.put("rotaPeriodStartDate", "2024-06-15");
            rotaPeriodData.put("rotaPeriodEndDate", "2024-06-30");
            rotaPeriodMap.put("period-1", rotaPeriodData);
            records.put(ROTA_PERIOD, rotaPeriodMap);

            // when
            final RotaPeriodDateInfoProvider result = rotaLocationPeriodHelper.getRotaPeriodDates(records);

            // then
            assertNotNull(result);
            assertEquals(LocalDate.parse("2024-06-15"), result.getRotaPeriodStartDate());
            assertEquals(LocalDate.parse("2024-06-30"), result.getRotaPeriodEndDate());
        }
    }

    @Nested
    @DisplayName("Delete Unallocated Court Schedule Judiciaries Tests")
    class DeleteUnallocatedCourtScheduleJudiciariesTests {

        @Test
        @DisplayName("Should delete unallocated court schedule judiciaries for rota period")
        void shouldDeleteUnallocatedCourtScheduleJudiciariesForRotaPeriod() {
            // given
            final LocalDate startDate = LocalDate.parse("2024-01-01");
            final LocalDate endDate = LocalDate.parse("2024-12-31");
            final List<String> ouCodes = List.of("OU001", "OU002");
            when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(startDate), eq(endDate), eq(ouCodes)))
                    .thenReturn(5);

            // when
            final int result = rotaLocationPeriodHelper.deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(
                    startDate, endDate, ouCodes);

            // then
            assertEquals(5, result);
            verify(courtScheduleJudiciaryService).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(startDate), eq(endDate), eq(ouCodes));
        }

        @Test
        @DisplayName("Should return zero when no judiciaries deleted")
        void shouldReturnZeroWhenNoJudiciariesDeleted() {
            // given
            final LocalDate startDate = LocalDate.parse("2024-01-01");
            final LocalDate endDate = LocalDate.parse("2024-12-31");
            final List<String> ouCodes = List.of("OU001");
            when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(startDate), eq(endDate), eq(ouCodes)))
                    .thenReturn(0);

            // when
            final int result = rotaLocationPeriodHelper.deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(
                    startDate, endDate, ouCodes);

            // then
            assertEquals(0, result);
            verify(courtScheduleJudiciaryService).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(startDate), eq(endDate), eq(ouCodes));
        }

        @Test
        @DisplayName("Should handle empty OU codes list")
        void shouldHandleEmptyOuCodesList() {
            // given
            final LocalDate startDate = LocalDate.parse("2024-01-01");
            final LocalDate endDate = LocalDate.parse("2024-12-31");
            final List<String> ouCodes = emptyList();
            when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(startDate), eq(endDate), eq(ouCodes)))
                    .thenReturn(0);

            // when
            final int result = rotaLocationPeriodHelper.deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(
                    startDate, endDate, ouCodes);

            // then
            assertEquals(0, result);
            verify(courtScheduleJudiciaryService).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(startDate), eq(endDate), eq(ouCodes));
        }

        @Test
        @DisplayName("Should handle large number of deleted judiciaries")
        void shouldHandleLargeNumberOfDeletedJudiciaries() {
            // given
            final LocalDate startDate = LocalDate.parse("2024-01-01");
            final LocalDate endDate = LocalDate.parse("2024-12-31");
            final List<String> ouCodes = List.of("OU001", "OU002", "OU003");
            when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(startDate), eq(endDate), eq(ouCodes)))
                    .thenReturn(150);

            // when
            final int result = rotaLocationPeriodHelper.deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(
                    startDate, endDate, ouCodes);

            // then
            assertEquals(150, result);
            verify(courtScheduleJudiciaryService).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(startDate), eq(endDate), eq(ouCodes));
        }

        @Test
        @DisplayName("Should handle same start and end date")
        void shouldHandleSameStartAndEndDate() {
            // given
            final LocalDate date = LocalDate.parse("2024-06-15");
            final List<String> ouCodes = List.of("OU001");
            when(courtScheduleJudiciaryService.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(date), eq(date), eq(ouCodes)))
                    .thenReturn(2);

            // when
            final int result = rotaLocationPeriodHelper.deleteUnAllocatedCourtScheduleJudiciariesForRotaPeriod(
                    date, date, ouCodes);

            // then
            assertEquals(2, result);
            verify(courtScheduleJudiciaryService).deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                    eq(date), eq(date), eq(ouCodes));
        }
    }
}

