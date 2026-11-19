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
    private static final String LITERAL = "   ";
    private static final String DATE_2024_01_01 = "2024-01-01";
    private static final String VALUE_2024_01_15_T10_00_00_Z = "2024-01-15T10:00:00Z";
    private static final String DATE_2024_01_31 = "2024-01-31";
    private static final String CRIMINAL_2 = "CRIMINAL";
    private static final String L2_CODE = "L2CODE";
    private static final String MAGISTRATES_2 = "MAGISTRATES";
    private static final String OU123_2 = "OU123";
    private static final String ROOM123_2 = "ROOM123";
    private static final String AND_CS_COURT_ROOM_ID_COURT_ROOM_ID = "and cs.court_room_id = :courtRoomId";
    private static final String AND_CS_COURT_ROOM_NUMBER_COURT_ROOM_NUMBER = "and cs.court_room_number = :courtRoomNumber";
    private static final String AND_CS_COURT_SESSION_COURT_SESSION = "and cs.court_session = :courtSession";
    private static final String AND_CS_OPERATIONAL_UNIT_OUCODE_L2_CODE = "and cs.operational_unit = :oucodeL2Code";
    private static final String AND_CS_OUCODE_OU_CODE = "and cs.oucode = :ouCode";
    private static final String AND_CS_ROTA_BUSINESS_TYPE_BUSINESS_TYPE = "and cs.rota_business_type = :businessType";
    private static final String OFFSET_2 = "offset";
    private static final String PAGE_SIZE = "pageSize";
    private static final String PANEL = "panel";
    private static final String SESSION_END_DATE = "sessionEndDate";
    private static final String SESSION_START_DATE = "sessionStartDate";


    @Test
    void testConstructorWithMinimalParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);

        // Then
        assertThat(builder, notNullValue());
        assertThat(builder.getAllocatedHearingsQuery(), notNullValue());
        assertThat(builder.getPagedQueryParamMap(), notNullValue());
    }

    @Test
    void testConstructorWithAllParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES,CRIMINAL", // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
                VALUE_2024_01_15_T10_00_00_Z, // exactHearingStartDateTime
                L2_CODE, // oucodeL2Code
                OU123_2, // ouCode
                "20", // pageSize
                "2", // pageNumber
                ROOM123_2, // courtRoomId
                "5", // courtRoomNumber
                CRIMINAL_2, // businessType
                "AD", // courtSession
                true, // isSlotBased
                VALUE_2024_01_15_T10_00_00_Z, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);

        // Then
        assertThat(builder, notNullValue());
        assertThat(builder.getAllocatedHearingsQuery(), notNullValue());
        assertThat(builder.getPagedQueryParamMap(), notNullValue());
    }

    @Test
    void testQueryContainsBasicStructure() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

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
    void testQueryContainsDenseRankAndCount() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString("DENSE_RANK() OVER (  PARTITION BY al.hearing_id ORDER BY cast(al.hearing_start_time as date)) AS hearing_day_position"));
        assertThat(query, containsString("count(*) over() as totalCount"));
        assertThat(query, containsString("(select count(1) from allocated_listings al2 where al2.hearing_id =al.hearing_id) as hearing_day_count"));
    }

    @Test
    void testQueryWithOptionalParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
                VALUE_2024_01_15_T10_00_00_Z, // exactHearingStartDateTime
                L2_CODE, // oucodeL2Code
                OU123_2, // ouCode
                "10", // pageSize
                "1", // pageNumber
                ROOM123_2, // courtRoomId
                "5", // courtRoomNumber
                CRIMINAL_2, // businessType
                "AD", // courtSession
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString(AND_CS_OPERATIONAL_UNIT_OUCODE_L2_CODE));
        assertThat(query, containsString(AND_CS_OUCODE_OU_CODE));
        assertThat(query, containsString(AND_CS_COURT_ROOM_ID_COURT_ROOM_ID));
        assertThat(query, containsString(AND_CS_COURT_ROOM_NUMBER_COURT_ROOM_NUMBER));
        assertThat(query, containsString(AND_CS_ROTA_BUSINESS_TYPE_BUSINESS_TYPE));
        assertThat(query, containsString(AND_CS_COURT_SESSION_COURT_SESSION));
        assertThat(query, containsString("and DATE_TRUNC('minute', al.hearing_start_time) = DATE_TRUNC('minute', CAST(:exactHearingStartDateTime as timestamptz))"));
    }

    @Test
    void testQueryWithoutOptionalParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, not(containsString(AND_CS_OPERATIONAL_UNIT_OUCODE_L2_CODE)));
        assertThat(query, not(containsString(AND_CS_OUCODE_OU_CODE)));
        assertThat(query, not(containsString(AND_CS_COURT_ROOM_ID_COURT_ROOM_ID)));
        assertThat(query, not(containsString(AND_CS_COURT_ROOM_NUMBER_COURT_ROOM_NUMBER)));
        assertThat(query, not(containsString(AND_CS_ROTA_BUSINESS_TYPE_BUSINESS_TYPE)));
        assertThat(query, not(containsString(AND_CS_COURT_SESSION_COURT_SESSION)));
        assertThat(query, not(containsString("and al.hearing_start_time = :exactHearingStartDateTime")));
    }

    @Test
    void testQueryWithEmptyStringParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, not(containsString(AND_CS_OPERATIONAL_UNIT_OUCODE_L2_CODE)));
        assertThat(query, not(containsString(AND_CS_OUCODE_OU_CODE)));
        assertThat(query, not(containsString(AND_CS_COURT_ROOM_ID_COURT_ROOM_ID)));
        assertThat(query, not(containsString(AND_CS_COURT_ROOM_NUMBER_COURT_ROOM_NUMBER)));
        assertThat(query, not(containsString(AND_CS_ROTA_BUSINESS_TYPE_BUSINESS_TYPE)));
        assertThat(query, not(containsString(AND_CS_COURT_SESSION_COURT_SESSION)));
        assertThat(query, not(containsString("and al.hearing_start_time = :exactHearingStartDateTime")));
    }

    @Test
    void testQueryWithWhitespaceOnlyParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
                LITERAL, // exactHearingStartDateTime (whitespace only)
                LITERAL, // oucodeL2Code (whitespace only)
                LITERAL, // ouCode (whitespace only)
                "10", // pageSize
                "1", // pageNumber
                LITERAL, // courtRoomId (whitespace only)
                LITERAL, // courtRoomNumber (whitespace only)
                LITERAL, // businessType (whitespace only)
                LITERAL, // courtSession (whitespace only)
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, not(containsString(AND_CS_OPERATIONAL_UNIT_OUCODE_L2_CODE)));
        assertThat(query, not(containsString(AND_CS_OUCODE_OU_CODE)));
        assertThat(query, not(containsString(AND_CS_COURT_ROOM_ID_COURT_ROOM_ID)));
        assertThat(query, not(containsString(AND_CS_COURT_ROOM_NUMBER_COURT_ROOM_NUMBER)));
        assertThat(query, not(containsString(AND_CS_ROTA_BUSINESS_TYPE_BUSINESS_TYPE)));
        assertThat(query, not(containsString(AND_CS_COURT_SESSION_COURT_SESSION)));
        assertThat(query, not(containsString("and al.hearing_start_time = :exactHearingStartDateTime")));
    }

    @Test
    void testPagedQueryParamMapWithMinimalParameters() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap, notNullValue());
        assertThat(paramMap.get(PANEL), is(java.util.List.of(MAGISTRATES_2)));
        assertThat(paramMap.get(SESSION_START_DATE), is(LocalDate.parse(DATE_2024_01_01)));
        assertThat(paramMap.get(SESSION_END_DATE), is(LocalDate.parse(DATE_2024_01_31)));
        assertThat(paramMap.get(PAGE_SIZE), is(10));
        assertThat(paramMap.get(OFFSET_2), is(0)); // (1-1) * 10 = 0
    }

    @Test
    void testPagedQueryParamMapWithAllParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES,CRIMINAL", // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
                VALUE_2024_01_15_T10_00_00_Z, // exactHearingStartDateTime
                L2_CODE, // oucodeL2Code
                OU123_2, // ouCode
                "20", // pageSize
                "3", // pageNumber
                ROOM123_2, // courtRoomId
                "5", // courtRoomNumber
                CRIMINAL_2, // businessType
                "AD", // courtSession
                true, // isSlotBased
                VALUE_2024_01_15_T10_00_00_Z, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap, notNullValue());
        assertThat(paramMap.get(PANEL), is(java.util.List.of(MAGISTRATES_2, CRIMINAL_2)));
        assertThat(paramMap.get(SESSION_START_DATE), is(LocalDate.parse(DATE_2024_01_01)));
        assertThat(paramMap.get(SESSION_END_DATE), is(LocalDate.parse(DATE_2024_01_31)));
        assertThat(paramMap.get(PAGE_SIZE), is(20));
        assertThat(paramMap.get(OFFSET_2), is(40)); // (3-1) * 20 = 40
        assertThat(paramMap.get("oucodeL2Code"), is(L2_CODE));
        assertThat(paramMap.get("ouCode"), is(OU123_2));
        assertThat(paramMap.get("courtRoomId"), is(ROOM123_2));
        assertThat(paramMap.get("courtRoomNumber"), is("5"));
        assertThat(paramMap.get("businessType"), is(CRIMINAL_2));
        assertThat(paramMap.get("courtSession"), is("AD"));
        assertThat(paramMap.get("exactHearingStartDateTime"), is(VALUE_2024_01_15_T10_00_00_Z));
    }

    @Test
    void testPagedQueryParamMapWithEmptyStringParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap, notNullValue());
        assertThat(paramMap.get(PANEL), is(java.util.List.of(MAGISTRATES_2)));
        assertThat(paramMap.get(SESSION_START_DATE), is(LocalDate.parse(DATE_2024_01_01)));
        assertThat(paramMap.get(SESSION_END_DATE), is(LocalDate.parse(DATE_2024_01_31)));
        assertThat(paramMap.get(PAGE_SIZE), is(10));
        assertThat(paramMap.get(OFFSET_2), is(0));
        
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
    void testPagedQueryParamMapWithWhitespaceOnlyParameters() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
                LITERAL, // exactHearingStartDateTime (whitespace only)
                LITERAL, // oucodeL2Code (whitespace only)
                LITERAL, // ouCode (whitespace only)
                "10", // pageSize
                "1", // pageNumber
                LITERAL, // courtRoomId (whitespace only)
                LITERAL, // courtRoomNumber (whitespace only)
                LITERAL, // businessType (whitespace only)
                LITERAL, // courtSession (whitespace only)
                null, // isSlotBased
                null, // hearingStartTime
                null, // showOverbookedSlots
                null, // duration
                null, // status
                null // jurisdiction
        );

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap, notNullValue());
        assertThat(paramMap.get(PANEL), is(java.util.List.of(MAGISTRATES_2)));
        assertThat(paramMap.get(SESSION_START_DATE), is(LocalDate.parse(DATE_2024_01_01)));
        assertThat(paramMap.get(SESSION_END_DATE), is(LocalDate.parse(DATE_2024_01_31)));
        assertThat(paramMap.get(PAGE_SIZE), is(10));
        assertThat(paramMap.get(OFFSET_2), is(0));
        
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
    void testPanelParameterWithMultipleValues() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES,CRIMINAL,FAMILY", // panel with multiple values
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap.get(PANEL), is(java.util.List.of(MAGISTRATES_2, CRIMINAL_2, "FAMILY")));
    }

    @Test
    void testPanelParameterWithSpaces() {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                "MAGISTRATES , CRIMINAL , FAMILY", // panel with spaces around commas
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap.get(PANEL), is(java.util.List.of(MAGISTRATES_2, CRIMINAL_2, "FAMILY")));
    }

    @Test
    void testPaginationCalculation() {
        // Test various page numbers and sizes
        assertPaginationCalculation(1, 10, 0);   // First page
        assertPaginationCalculation(2, 10, 10);  // Second page
        assertPaginationCalculation(3, 20, 40);  // Third page with different size
        assertPaginationCalculation(5, 25, 100); // Fifth page with different size
    }

    private void assertPaginationCalculation(final int pageNumber, final int pageSize, final int expectedOffset) {
        // Given
        final HearingSlotRequestParam requestParam = new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final Map<String, Object> paramMap = builder.getPagedQueryParamMap();

        // Then
        assertThat(paramMap.get(PAGE_SIZE), is(pageSize));
        assertThat(paramMap.get(OFFSET_2), is(expectedOffset));
    }

    @Test
    void testOrderByClause() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString("order by cs.session_start, " +
                "cs.court_house_name, " +
                "cs.court_room_name, " +
                "cs.court_session, " +
                "al.hearing_start_time"));
    }

    @Test
    void testQueryStructureIntegrity() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParam();

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        // Verify the query has proper structure
        assertTrue(query.startsWith("select"));
        assertTrue(query.contains("from allocated_listings al, court_schedule cs"));
        assertTrue(query.contains("where"));
        assertTrue(query.contains("order by"));
        assertTrue(query.contains("LIMIT"));
        assertTrue(query.contains("OFFSET"));
        assertTrue(query.contains("select"), "Query should contain proper SQL structure");
    }

    @Test
    void testParameterMapImmutable() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParam();
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);

        // When
        final Map<String, Object> paramMap1 = builder.getPagedQueryParamMap();
        final Map<String, Object> paramMap2 = builder.getPagedQueryParamMap();

        // Then
        // The maps should be equal but not necessarily the same instance
        assertEquals(paramMap1, paramMap2);
    }

    @Test
    void testQueryStringImmutable() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParam();
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);

        // When
        final String query1 = builder.getAllocatedHearingsQuery();
        final String query2 = builder.getAllocatedHearingsQuery();

        // Then
        assertEquals(query1, query2);
    }

    private HearingSlotRequestParam createBasicRequestParam() {
        return new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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

    private HearingSlotRequestParam createBasicRequestParamWithStatus(final String status) {
        return new HearingSlotRequestParam(
                MAGISTRATES_2, // panel
                DATE_2024_01_01, // sessionStartDate
                DATE_2024_01_31, // sessionEndDate
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
                status, // status
                null // jurisdiction
        );
    }

    @Test
    void testQueryWithStatusFinalAddsNonDraftClause() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParamWithStatus("FINAL");

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString("cs.is_draft = false"));
    }

    @Test
    void testQueryWithStatusDraftAddsDraftClause() {
        // Given
        final HearingSlotRequestParam requestParam = createBasicRequestParamWithStatus("DRAFT");

        // When
        final AllocatedHearingsQueryBuilder builder = new AllocatedHearingsQueryBuilder(requestParam);
        final String query = builder.getAllocatedHearingsQuery();

        // Then
        assertThat(query, containsString("cs.is_draft = true"));
    }

    @Test
    void testQueryWithStatusAbsentOrAllAppliesNoFilter() {
        // Given / When / Then: null status
        final HearingSlotRequestParam requestParamNull = createBasicRequestParamWithStatus(null);
        final AllocatedHearingsQueryBuilder builderNull = new AllocatedHearingsQueryBuilder(requestParamNull);
        assertThat(builderNull.getAllocatedHearingsQuery(), not(containsString("is_draft")));

        // "ALL" status
        final HearingSlotRequestParam requestParamAll = createBasicRequestParamWithStatus("ALL");
        final AllocatedHearingsQueryBuilder builderAll = new AllocatedHearingsQueryBuilder(requestParamAll);
        assertThat(builderAll.getAllocatedHearingsQuery(), not(containsString("is_draft")));
    }
}
