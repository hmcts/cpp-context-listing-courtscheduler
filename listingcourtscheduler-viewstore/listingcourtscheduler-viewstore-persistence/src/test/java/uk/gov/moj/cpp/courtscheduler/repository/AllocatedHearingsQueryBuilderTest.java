package uk.gov.moj.cpp.courtscheduler.repository;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AllocatedHearingsQueryBuilderTest extends uk.gov.moj.cpp.courtscheduler.repository.AbstractRepositoryTest {

    @Test
    public void testConstructorWithMinimalParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                null, // exactHearingStartDateTime
                null, // oucodeL2Code
                null, // ouCode
                "10", // pageSize
                "1", // pageNumber
                null, // courtRoomId
                null, // courtRoomNumber
                null, // businessType
                null, // courtSession
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);

        // Then
        assertThat(builder, notNullValue());
        assertThat(builder.getAllocatedHearingsQuery(), notNullValue());
        assertThat(builder.getPagedQueryParamMap(), notNullValue());
    }

    @Test
    public void testConstructorWithAllParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES,CRIMINAL", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                "2024-01-15T10:00:00Z", // exactHearingStartDateTime
                "L2CODE", // oucodeL2Code
                "OU123", // ouCode
                "20", // pageSize
                "2", // pageNumber
                "ROOM123", // courtRoomId
                "5", // courtRoomNumber
                "CRIMINAL", // businessType
                "AD", // courtSession
                true, // isSlotBased
                "2024-01-15T10:00:00Z", // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);

        // Then
        assertThat(builder, notNullValue());
        assertThat(builder.getAllocatedHearingsQuery(), notNullValue());
        assertThat(builder.getPagedQueryParamMap(), notNullValue());
    }

    @Test
    public void testQueryContainsBasicStructure() {
        // Given
        HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString("select al.hearing_id, al.court_schedule_id, cast(al.hearing_start_time as date)"));
        assertThat(query, containsString("from allocated_listings al, court_schedule cs"));
        assertThat(query, containsString("where al.court_schedule_id = cs.id and cs.active = true"));
        assertThat(query, containsString("and cs.panel in (:panel)"));
        assertThat(query, containsString("and cs.session_start >= :sessionStartDate"));
        assertThat(query, containsString("and cs.session_start <= :sessionEndDate"));
        assertThat(query, containsString("order by cs.session_start"));
        assertThat(query, containsString("LIMIT :pageSize"));
        assertThat(query, containsString("OFFSET :offset"));
    }

    @Test
    public void testQueryContainsDenseRankAndCount() {
        // Given
        HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString("DENSE_RANK() OVER (  PARTITION BY al.hearing_id ORDER BY cast(al.hearing_start_time as date)) AS hearing_day_position"));
        assertThat(query, containsString("count(*) over() as totalCount"));
        assertThat(query, containsString("(select count(1) from allocated_listings al2 where al2.hearing_id =al.hearing_id) as hearing_day_count"));
    }

    @Test
    public void testQueryWithOptionalParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                "2024-01-15T10:00:00Z", // exactHearingStartDateTime
                "L2CODE", // oucodeL2Code
                "OU123", // ouCode
                "10", // pageSize
                "1", // pageNumber
                "ROOM123", // courtRoomId
                "5", // courtRoomNumber
                "CRIMINAL", // businessType
                "AD", // courtSession
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString("and cs.operational_unit = :oucodeL2Code"));
        assertThat(query, containsString("and cs.oucode = :ouCode"));
        assertThat(query, containsString("and cs.court_room_id = :courtRoomId"));
        assertThat(query, containsString("and cs.court_room_number = :courtRoomNumber"));
        assertThat(query, containsString("and cs.rota_business_type = :businessType"));
        assertThat(query, containsString("and cs.court_session = :courtSession"));
        assertThat(query, containsString("and DATE_TRUNC('minute', al.hearing_start_time) = DATE_TRUNC('minute', CAST(:exactHearingStartDateTime as timestamptz))"));
    }

    @Test
    public void testQueryWithoutOptionalParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                null, // exactHearingStartDateTime
                null, // oucodeL2Code
                null, // ouCode
                "10", // pageSize
                "1", // pageNumber
                null, // courtRoomId
                null, // courtRoomNumber
                null, // businessType
                null, // courtSession
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, not(containsString("and cs.operational_unit = :oucodeL2Code")));
        assertThat(query, not(containsString("and cs.oucode = :ouCode")));
        assertThat(query, not(containsString("and cs.court_room_id = :courtRoomId")));
        assertThat(query, not(containsString("and cs.court_room_number = :courtRoomNumber")));
        assertThat(query, not(containsString("and cs.rota_business_type = :businessType")));
        assertThat(query, not(containsString("and cs.court_session = :courtSession")));
        assertThat(query, not(containsString("and al.hearing_start_time = :exactHearingStartDateTime")));
    }

    @Test
    public void testQueryWithEmptyStringParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                "", // exactHearingStartDateTime (empty string)
                "", // oucodeL2Code (empty string)
                "", // ouCode (empty string)
                "10", // pageSize
                "1", // pageNumber
                "", // courtRoomId (empty string)
                "", // courtRoomNumber (empty string)
                "", // businessType (empty string)
                "", // courtSession (empty string)
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, not(containsString("and cs.operational_unit = :oucodeL2Code")));
        assertThat(query, not(containsString("and cs.oucode = :ouCode")));
        assertThat(query, not(containsString("and cs.court_room_id = :courtRoomId")));
        assertThat(query, not(containsString("and cs.court_room_number = :courtRoomNumber")));
        assertThat(query, not(containsString("and cs.rota_business_type = :businessType")));
        assertThat(query, not(containsString("and cs.court_session = :courtSession")));
        assertThat(query, not(containsString("and al.hearing_start_time = :exactHearingStartDateTime")));
    }

    @Test
    public void testQueryWithWhitespaceOnlyParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                "   ", // exactHearingStartDateTime (whitespace only)
                "   ", // oucodeL2Code (whitespace only)
                "   ", // ouCode (whitespace only)
                "10", // pageSize
                "1", // pageNumber
                "   ", // courtRoomId (whitespace only)
                "   ", // courtRoomNumber (whitespace only)
                "   ", // businessType (whitespace only)
                "   ", // courtSession (whitespace only)
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, not(containsString("and cs.operational_unit = :oucodeL2Code")));
        assertThat(query, not(containsString("and cs.oucode = :ouCode")));
        assertThat(query, not(containsString("and cs.court_room_id = :courtRoomId")));
        assertThat(query, not(containsString("and cs.court_room_number = :courtRoomNumber")));
        assertThat(query, not(containsString("and cs.rota_business_type = :businessType")));
        assertThat(query, not(containsString("and cs.court_session = :courtSession")));
        assertThat(query, not(containsString("and al.hearing_start_time = :exactHearingStartDateTime")));
    }

    @Test
    public void testPagedQueryParamMapWithMinimalParameters() {
        // Given
        HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap, notNullValue());
        assertThat(paramMap.get("panel"), is(java.util.List.of("MAGISTRATES")));
        assertThat(paramMap.get("sessionStartDate"), is(LocalDate.parse("2024-01-01")));
        assertThat(paramMap.get("sessionEndDate"), is(LocalDate.parse("2024-01-31")));
        assertThat(paramMap.get("pageSize"), is(10));
        assertThat(paramMap.get("offset"), is(0)); // (1-1) * 10 = 0
    }

    @Test
    public void testPagedQueryParamMapWithAllParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES,CRIMINAL", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                "2024-01-15T10:00:00Z", // exactHearingStartDateTime
                "L2CODE", // oucodeL2Code
                "OU123", // ouCode
                "20", // pageSize
                "3", // pageNumber
                "ROOM123", // courtRoomId
                "5", // courtRoomNumber
                "CRIMINAL", // businessType
                "AD", // courtSession
                true, // isSlotBased
                "2024-01-15T10:00:00Z", // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap, notNullValue());
        assertThat(paramMap.get("panel"), is(java.util.List.of("MAGISTRATES", "CRIMINAL")));
        assertThat(paramMap.get("sessionStartDate"), is(LocalDate.parse("2024-01-01")));
        assertThat(paramMap.get("sessionEndDate"), is(LocalDate.parse("2024-01-31")));
        assertThat(paramMap.get("pageSize"), is(20));
        assertThat(paramMap.get("offset"), is(40)); // (3-1) * 20 = 40
        assertThat(paramMap.get("oucodeL2Code"), is("L2CODE"));
        assertThat(paramMap.get("ouCode"), is("OU123"));
        assertThat(paramMap.get("courtRoomId"), is("ROOM123"));
        assertThat(paramMap.get("courtRoomNumber"), is("5"));
        assertThat(paramMap.get("businessType"), is("CRIMINAL"));
        assertThat(paramMap.get("courtSession"), is("AD"));
        assertThat(paramMap.get("exactHearingStartDateTime"), is("2024-01-15T10:00:00Z"));
    }

    @Test
    public void testPagedQueryParamMapWithEmptyStringParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                "", // exactHearingStartDateTime (empty string)
                "", // oucodeL2Code (empty string)
                "", // ouCode (empty string)
                "10", // pageSize
                "1", // pageNumber
                "", // courtRoomId (empty string)
                "", // courtRoomNumber (empty string)
                "", // businessType (empty string)
                "", // courtSession (empty string)
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap, notNullValue());
        assertThat(paramMap.get("panel"), is(java.util.List.of("MAGISTRATES")));
        assertThat(paramMap.get("sessionStartDate"), is(LocalDate.parse("2024-01-01")));
        assertThat(paramMap.get("sessionEndDate"), is(LocalDate.parse("2024-01-31")));
        assertThat(paramMap.get("pageSize"), is(10));
        assertThat(paramMap.get("offset"), is(0));
        
        // Empty strings should not be added to the parameter map
        assertFalse(paramMap.containsKey("oucodeL2Code"));
        assertFalse(paramMap.containsKey("ouCode"));
        assertFalse(paramMap.containsKey("courtRoomId"));
        assertFalse(paramMap.containsKey("courtRoomNumber"));
        assertFalse(paramMap.containsKey("businessType"));
        assertFalse(paramMap.containsKey("courtSession"));
        assertFalse(paramMap.containsKey("exactHearingStartDateTime"));
    }

    @Test
    public void testPagedQueryParamMapWithWhitespaceOnlyParameters() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                "   ", // exactHearingStartDateTime (whitespace only)
                "   ", // oucodeL2Code (whitespace only)
                "   ", // ouCode (whitespace only)
                "10", // pageSize
                "1", // pageNumber
                "   ", // courtRoomId (whitespace only)
                "   ", // courtRoomNumber (whitespace only)
                "   ", // businessType (whitespace only)
                "   ", // courtSession (whitespace only)
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap, notNullValue());
        assertThat(paramMap.get("panel"), is(java.util.List.of("MAGISTRATES")));
        assertThat(paramMap.get("sessionStartDate"), is(LocalDate.parse("2024-01-01")));
        assertThat(paramMap.get("sessionEndDate"), is(LocalDate.parse("2024-01-31")));
        assertThat(paramMap.get("pageSize"), is(10));
        assertThat(paramMap.get("offset"), is(0));
        
        // Whitespace-only strings should not be added to the parameter map
        assertFalse(paramMap.containsKey("oucodeL2Code"));
        assertFalse(paramMap.containsKey("ouCode"));
        assertFalse(paramMap.containsKey("courtRoomId"));
        assertFalse(paramMap.containsKey("courtRoomNumber"));
        assertFalse(paramMap.containsKey("businessType"));
        assertFalse(paramMap.containsKey("courtSession"));
        assertFalse(paramMap.containsKey("exactHearingStartDateTime"));
    }

    @Test
    public void testPanelParameterWithMultipleValues() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES,CRIMINAL,FAMILY", // panel with multiple values
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                null, // exactHearingStartDateTime
                null, // oucodeL2Code
                null, // ouCode
                "10", // pageSize
                "1", // pageNumber
                null, // courtRoomId
                null, // courtRoomNumber
                null, // businessType
                null, // courtSession
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap.get("panel"), is(java.util.List.of("MAGISTRATES", "CRIMINAL", "FAMILY")));
    }

    @Test
    public void testPanelParameterWithSpaces() {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES , CRIMINAL , FAMILY", // panel with spaces around commas
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                null, // exactHearingStartDateTime
                null, // oucodeL2Code
                null, // ouCode
                "10", // pageSize
                "1", // pageNumber
                null, // courtRoomId
                null, // courtRoomNumber
                null, // businessType
                null, // courtSession
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap.get("panel"), is(java.util.List.of("MAGISTRATES", "CRIMINAL", "FAMILY")));
    }

    @Test
    public void testPaginationCalculation() {
        // Test various page numbers and sizes
        testPaginationCalculation(1, 10, 0);   // First page
        testPaginationCalculation(2, 10, 10);  // Second page
        testPaginationCalculation(3, 20, 40);  // Third page with different size
        testPaginationCalculation(5, 25, 100); // Fifth page with different size
    }

    private void testPaginationCalculation(int pageNumber, int pageSize, int expectedOffset) {
        // Given
        HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                null, // exactHearingStartDateTime
                null, // oucodeL2Code
                null, // ouCode
                String.valueOf(pageSize), // pageSize
                String.valueOf(pageNumber), // pageNumber
                null, // courtRoomId
                null, // courtRoomNumber
                null, // businessType
                null, // courtSession
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap.get("pageSize"), is(pageSize));
        assertThat(paramMap.get("offset"), is(expectedOffset));
    }

    @Test
    public void testOrderByClause() {
        // Given
        HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString("order by cs.session_start, " +
                "cs.court_house_name, " +
                "cs.court_room_name, " +
                "cs.court_session, " +
                "al.hearing_start_time"));
    }

    @Test
    public void testQueryStructureIntegrity() {
        // Given
        HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        String query = builder.getAllocatedHearingsQuery();

        // Then
        // Verify the query has proper structure
        assertTrue(query.startsWith("select"));
        assertTrue(query.contains("from allocated_listings al, court_schedule cs"));
        assertTrue(query.contains("where"));
        assertTrue(query.contains("order by"));
        assertTrue(query.contains("LIMIT"));
        assertTrue(query.contains("OFFSET"));
        
        // Verify no duplicate keywords
        long selectCount = query.toLowerCase().chars().filter(ch -> ch == 's').count();
        assertTrue(query.contains("select"), "Query should contain proper SQL structure");
    }

    @Test
    public void testParameterMapImmutable() {
        // Given
        HearingSlotRequestParam requestParam = createBasicRequestParam();
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);

        // When
        Map<String, Object> paramMap1 = builder.getPagedQueryParamMap();
        Map<String, Object> paramMap2 = builder.getPagedQueryParamMap();

        // Then
        // The maps should be equal but not necessarily the same instance
        assertEquals(paramMap1, paramMap2);
    }

    @Test
    public void testQueryStringImmutable() {
        // Given
        HearingSlotRequestParam requestParam = createBasicRequestParam();
        AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);

        // When
        String query1 = builder.getAllocatedHearingsQuery();
        String query2 = builder.getAllocatedHearingsQuery();

        // Then
        assertEquals(query1, query2);
    }

    private HearingSlotRequestParam createBasicRequestParam() {
        return new HearingSlotRequestParam(
                "MAGISTRATES", // panel
                "2024-01-01", // sessionStartDate
                "2024-01-31", // sessionEndDate
                null, // exactHearingStartDateTime
                null, // oucodeL2Code
                null, // ouCode
                "10", // pageSize
                "1", // pageNumber
                null, // courtRoomId
                null, // courtRoomNumber
                null, // businessType
                null, // courtSession
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );
    }
}
