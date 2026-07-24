package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.EMAIL_ADDRESS;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.DISTRICT_JUDGES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.SCHEDULE;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.JUDICIARY_NOT_FOUND;

import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher.JudiciaryBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaJudiciaryHelperTest {

    @Mock
    private RotaReferenceDataService referenceDataValidationService;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Mock
    private JudiciaryBuilder judiciaryBuilder;

    @InjectMocks
    private RotaJudiciaryHelper rotaJudiciaryHelper;

    private String executionId;
    private Map<RotaPayload, Map<String, Map<String, String>>> records;
    private Judiciary judiciary;
    private String judiciaryId;

    @BeforeEach
    void setUp() {
        executionId = "execution-123";
        records = new HashMap<>();
        judiciaryId = UUID.randomUUID().toString();
        judiciary = Judiciary.JudiciaryBuilder.aJudiciary()
                .withId(judiciaryId)
                .withEmailAddress("judge@example.com")
                .withForenames("John")
                .withSurname("Doe")
                .withTitlePrefix("Mr")
                .withJudiciaryType("Judge")
                .build();
    }

    // ============================================================================
    // Tests for createJudiciaryMap
    // ============================================================================

    @Test
    void shouldCreateJudiciaryMap_WhenMagistratesAndJudgesPresent() {
        // given
        final String magistrateId = "mag-1";
        final String judgeId = "judge-1";
        final String magistrateEmail = "mag@example.com";
        final String judgeEmail = "judge@example.com";

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(magistrateId, Map.of(MAGS_EMAIL, magistrateEmail));
        records.put(MAGISTRATES, magistrates);

        final Map<String, Map<String, String>> districtJudges = new HashMap<>();
        districtJudges.put(judgeId, Map.of(JUDGE_EMAIL, judgeEmail));
        records.put(DISTRICT_JUDGES, districtJudges);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(magistrateEmail), eq(executionId)))
                .thenReturn(Optional.of(judiciary));
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(judgeEmail), eq(executionId)))
                .thenReturn(Optional.of(judiciary));

        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(records, executionId);

        // then
        assertThat(result.size(), is(2));
        assertThat(result.containsKey(magistrateId), is(true));
        assertThat(result.containsKey(judgeId), is(true));
        assertThat(result.get(magistrateId), is(UUID.fromString(judiciaryId)));
        assertThat(result.get(judgeId), is(UUID.fromString(judiciaryId)));
    }

    @Test
    void shouldReturnEmptyMap_WhenRecordsIsNull() {
        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(null, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(referenceDataValidationService, never()).validateAndFindJudiciaryByEmail(anyString(), anyString());
    }

    @Test
    void shouldReturnEmptyMap_WhenRecordsIsEmpty() {
        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(emptyMap(), executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(referenceDataValidationService, never()).validateAndFindJudiciaryByEmail(anyString(), anyString());
    }

    @Test
    void shouldSkipJudiciary_WhenEmailIsMissing() {
        // given
        final String magistrateId = "mag-1";
        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(magistrateId, Map.of("otherField", "value"));
        records.put(MAGISTRATES, magistrates);

        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(referenceDataValidationService, never()).validateAndFindJudiciaryByEmail(anyString(), anyString());
    }

    @Test
    void shouldNotLogMissingJudiciary_WhenJudiciaryNotFoundInCreateJudiciaryMap() {
        // given
        final String magistrateId = "mag-1";
        final String magistrateEmail = "missing@example.com";
        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(magistrateId, Map.of(MAGS_EMAIL, magistrateEmail));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(magistrateEmail), eq(executionId)))
                .thenReturn(Optional.empty());

        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(records, executionId);

        // then
        assertThat(result, is(emptyMap()));
        // createJudiciaryMap no longer logs missing judiciaries (logging moved to createScheduleJudiciaryList)
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    // ============================================================================
    // Tests for getJudiciaryInfoMap
    // ============================================================================

    @Test
    void shouldGetJudiciaryInfoMap_WhenRecordsContainJudiciaries() {
        // given
        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put("mag-1", Map.of("field1", "value1"));
        records.put(MAGISTRATES, magistrates);

        final Map<String, Map<String, String>> districtJudges = new HashMap<>();
        districtJudges.put("judge-1", Map.of("field2", "value2"));
        records.put(DISTRICT_JUDGES, districtJudges);

        // when
        final Map<String, Map<String, String>> result = rotaJudiciaryHelper.getJudiciaryInfoMap(records);

        // then
        assertThat(result.size(), is(2));
        assertThat(result.containsKey("mag-1"), is(true));
        assertThat(result.containsKey("judge-1"), is(true));
    }

    @Test
    void shouldReturnEmptyMap_WhenNoJudiciariesInRecords() {
        // when
        final Map<String, Map<String, String>> result = rotaJudiciaryHelper.getJudiciaryInfoMap(records);

        // then
        assertThat(result, is(emptyMap()));
    }

    // ============================================================================
    // Tests for enrichScheduleWithJudiciaryInfo
    // ============================================================================

    @Test
    void shouldEnrichSchedule_WhenJudiciaryFound() {
        // given
        final Map<String, String> schedule = new HashMap<>();
        final Map<String, Map<String, String>> judiciariesMap = new HashMap<>();
        final String rotaJusticeId = "justice-1";
        final String email = "judge@example.com";

        judiciariesMap.put(rotaJusticeId, Map.of(JUDGE_EMAIL, email));
        schedule.put(ROTA_JUDICIARY_ID, rotaJusticeId);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(email), eq(executionId)))
                .thenReturn(Optional.of(judiciary));

        final Map<String, String> errors = new HashMap<>();

        // when
        rotaJudiciaryHelper.enrichScheduleWithJudiciaryInfo(schedule, judiciariesMap, rotaJusticeId, executionId, errors);

        // then
        assertThat(schedule.get(JUDICIARY_ID), is(judiciaryId));
        assertThat(schedule.get(JUDICIARY_TYPE), is("Judge"));
        assertThat(errors.isEmpty(), is(true));
    }

    @Test
    void shouldLogError_WhenJudiciaryNotFound() {
        // given
        final Map<String, String> schedule = new HashMap<>();
        final Map<String, Map<String, String>> judiciariesMap = new HashMap<>();
        final String rotaJusticeId = "justice-1";
        final String email = "missing@example.com";

        judiciariesMap.put(rotaJusticeId, Map.of(JUDGE_EMAIL, email, JUDGE_FORENAMES, "John", JUDGE_SURNAME, "Doe"));
        schedule.put(ROTA_JUDICIARY_ID, rotaJusticeId);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(email), eq(executionId)))
                .thenReturn(Optional.empty());

        final Map<String, String> errors = new HashMap<>();

        // when
        rotaJudiciaryHelper.enrichScheduleWithJudiciaryInfo(schedule, judiciariesMap, rotaJusticeId, executionId, errors);

        // then
        assertThat(schedule.get(JUDICIARY_ID), is(nullValue()));
        assertThat(errors.containsKey(email), is(true));
    }

    // ============================================================================
    // Tests for createJudiciaryCourtScheduleMap
    // ============================================================================

    @Test
    void shouldCreateJudiciaryCourtScheduleMap_WhenValidData() {
        // given
        final String justiceId = "justice-1";
        final String courtListingProfileId = "listing-1";
        final UUID sessionId1 = UUID.randomUUID();
        final UUID sessionId2 = UUID.randomUUID();
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);

        final Map<String, UUID> judiciaryMap = Map.of(justiceId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(courtListingProfileId, Set.of(sessionId1, sessionId2));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, justiceId);
        schedule.put(COURT_LISTING_PROFILE_ID, courtListingProfileId);
        schedule.put(JUDICIARY_ID, judiciaryId);
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(justiceId, Map.of(MAGS_EMAIL, "judge@example.com"));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        final CourtScheduleJudiciary courtScheduleJudiciary = CourtScheduleJudiciary.judiciary()
                .withJudiciaryId(judiciaryId)
                .withCourtListingProfileId(courtListingProfileId)
                .withPosition("CHAIR")
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .build();

        when(judiciaryBuilder.build(anyMap(), anyString())).thenReturn(courtScheduleJudiciary);

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(judiciaryId), is(true));
        final List<JudiciaryCourtScheduleData> dataList = result.get(judiciaryId);
        assertThat(dataList, is(notNullValue()));
        assertThat(dataList.size(), is(1));
        final JudiciaryCourtScheduleData data = dataList.get(0);
        assertThat(data.courtScheduleIds().size(), is(2));
        assertThat(data.courtScheduleIds().contains(sessionId1), is(true));
        assertThat(data.courtScheduleIds().contains(sessionId2), is(true));
        assertThat(data.position(), is("CHAIR"));
        assertThat(data.isBenchChairman(), is(true));
        assertThat(data.isDeputy(), is(false));
    }

    @Test
    void shouldReturnEmptyMap_WhenNoSchedulesInRecords() {
        // given
        final Map<String, UUID> judiciaryMap = Map.of("justice-1", UUID.randomUUID());
        final Map<String, Set<UUID>> courtScheduleMap = Map.of("listing-1", Set.of(UUID.randomUUID()));

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldReturnEmptyMap_WhenCourtScheduleMapIsEmpty() {
        // given
        final Map<String, UUID> judiciaryMap = Map.of("justice-1", UUID.randomUUID());
        final Map<String, Set<UUID>> courtScheduleMap = Collections.emptyMap();

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        schedules.put("schedule-1", Map.of(ROTA_JUDICIARY_ID, "justice-1", COURT_LISTING_PROFILE_ID, "listing-1"));
        records.put(SCHEDULE, schedules);

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldSkipSchedule_WhenJudiciaryIdNotInMap() {
        // given
        final String justiceId = "justice-1";
        final String courtListingProfileId = "listing-1";
        final UUID sessionId = UUID.randomUUID();
        final UUID differentJudiciaryUuid = UUID.randomUUID();

        final Map<String, UUID> judiciaryMap = Map.of("different-justice", differentJudiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(courtListingProfileId, Set.of(sessionId));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, justiceId);
        schedule.put(COURT_LISTING_PROFILE_ID, courtListingProfileId);
        schedule.put(JUDICIARY_ID, UUID.randomUUID().toString());
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(justiceId, Map.of(MAGS_EMAIL, "judge@example.com"));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldCreateJudiciaryCourtScheduleMap_WhenMultipleSchedulesForSameJudiciary() {
        // given
        final String justiceId = "justice-1";
        final String courtListingProfileId1 = "listing-1";
        final String courtListingProfileId2 = "listing-2";
        final UUID sessionId1 = UUID.randomUUID();
        final UUID sessionId2 = UUID.randomUUID();
        final UUID sessionId3 = UUID.randomUUID();
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);

        final Map<String, UUID> judiciaryMap = Map.of(justiceId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(
                courtListingProfileId1, Set.of(sessionId1, sessionId2),
                courtListingProfileId2, Set.of(sessionId3)
        );

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule1 = new HashMap<>();
        schedule1.put(ROTA_JUDICIARY_ID, justiceId);
        schedule1.put(COURT_LISTING_PROFILE_ID, courtListingProfileId1);
        schedule1.put(JUDICIARY_ID, judiciaryId);
        schedules.put("schedule-1", schedule1);

        final Map<String, String> schedule2 = new HashMap<>();
        schedule2.put(ROTA_JUDICIARY_ID, justiceId);
        schedule2.put(COURT_LISTING_PROFILE_ID, courtListingProfileId2);
        schedule2.put(JUDICIARY_ID, judiciaryId);
        schedules.put("schedule-2", schedule2);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(justiceId, Map.of(MAGS_EMAIL, "judge@example.com"));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        final CourtScheduleJudiciary courtScheduleJudiciary1 = CourtScheduleJudiciary.judiciary()
                .withJudiciaryId(judiciaryId)
                .withCourtListingProfileId(courtListingProfileId1)
                .withPosition("CHAIR")
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .build();

        final CourtScheduleJudiciary courtScheduleJudiciary2 = CourtScheduleJudiciary.judiciary()
                .withJudiciaryId(judiciaryId)
                .withCourtListingProfileId(courtListingProfileId2)
                .withPosition("LEFT_WINGER")
                .withIsBenchChairman(false)
                .withIsDeputy(true)
                .build();

        when(judiciaryBuilder.build(anyMap(), anyString()))
                .thenReturn(courtScheduleJudiciary1)
                .thenReturn(courtScheduleJudiciary2);

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(judiciaryId), is(true));
        final List<JudiciaryCourtScheduleData> dataList = result.get(judiciaryId);
        assertThat(dataList, is(notNullValue()));
        assertThat(dataList.size(), is(2));
        
        // Verify first schedule data
        final JudiciaryCourtScheduleData data1 = dataList.stream()
                .filter(d -> d.position().equals("CHAIR"))
                .findFirst()
                .orElse(null);
        assertThat(data1, is(notNullValue()));
        assertThat(data1.courtScheduleIds().size(), is(2));
        assertThat(data1.isBenchChairman(), is(true));
        assertThat(data1.isDeputy(), is(false));

        // Verify second schedule data
        final JudiciaryCourtScheduleData data2 = dataList.stream()
                .filter(d -> d.position().equals("LEFT_WINGER"))
                .findFirst()
                .orElse(null);
        assertThat(data2, is(notNullValue()));
        assertThat(data2.courtScheduleIds().size(), is(1));
        assertThat(data2.isBenchChairman(), is(false));
        assertThat(data2.isDeputy(), is(true));
    }

    @Test
    void shouldReturnEmptyMap_WhenScheduleJudiciaryListIsNull() {
        // given
        final Map<String, UUID> judiciaryMap = Map.of("justice-1", UUID.randomUUID());
        final Map<String, Set<UUID>> courtScheduleMap = Map.of("listing-1", Set.of(UUID.randomUUID()));

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldSkipSchedule_WhenMissingJudiciaryId() {
        // given
        final String justiceId = "justice-1";
        final String courtListingProfileId = "listing-1";
        final UUID sessionId = UUID.randomUUID();
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);

        final Map<String, UUID> judiciaryMap = Map.of(justiceId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(courtListingProfileId, Set.of(sessionId));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, justiceId);
        schedule.put(COURT_LISTING_PROFILE_ID, courtListingProfileId);
        // Missing JUDICIARY_ID
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(justiceId, Map.of(MAGS_EMAIL, "judge@example.com"));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldSkipSchedule_WhenMissingCourtListingProfileId() {
        // given
        final String justiceId = "justice-1";
        final UUID sessionId = UUID.randomUUID();
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);

        final Map<String, UUID> judiciaryMap = Map.of(justiceId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of("listing-1", Set.of(sessionId));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, justiceId);
        schedule.put(JUDICIARY_ID, judiciaryId);
        // Missing COURT_LISTING_PROFILE_ID
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(justiceId, Map.of(MAGS_EMAIL, "judge@example.com"));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldHandleException_WhenProcessingSchedule() {
        // given
        final String justiceId = "justice-1";
        final String courtListingProfileId = "listing-1";
        final UUID sessionId = UUID.randomUUID();
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);

        final Map<String, UUID> judiciaryMap = Map.of(justiceId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(courtListingProfileId, Set.of(sessionId));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, justiceId);
        schedule.put(COURT_LISTING_PROFILE_ID, courtListingProfileId);
        schedule.put(JUDICIARY_ID, "invalid-uuid-format"); // This will cause exception when parsing
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(justiceId, Map.of(MAGS_EMAIL, "judge@example.com"));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        // Should handle exception gracefully and return empty map or partial results
        assertThat(result, is(notNullValue()));
    }

    @Test
    void shouldCreateJudiciaryCourtScheduleMap_WhenMultipleCourtListingProfilesForSameJudiciary() {
        // given
        final String justiceId = "justice-1";
        final String courtListingProfileId1 = "listing-1";
        final String courtListingProfileId2 = "listing-2";
        final UUID sessionId1 = UUID.randomUUID();
        final UUID sessionId2 = UUID.randomUUID();
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);

        final Map<String, UUID> judiciaryMap = Map.of(justiceId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(
                courtListingProfileId1, Set.of(sessionId1),
                courtListingProfileId2, Set.of(sessionId2)
        );

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule1 = new HashMap<>();
        schedule1.put(ROTA_JUDICIARY_ID, justiceId);
        schedule1.put(COURT_LISTING_PROFILE_ID, courtListingProfileId1);
        schedule1.put(JUDICIARY_ID, judiciaryId);
        schedules.put("schedule-1", schedule1);

        final Map<String, String> schedule2 = new HashMap<>();
        schedule2.put(ROTA_JUDICIARY_ID, justiceId);
        schedule2.put(COURT_LISTING_PROFILE_ID, courtListingProfileId2);
        schedule2.put(JUDICIARY_ID, judiciaryId);
        schedules.put("schedule-2", schedule2);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(justiceId, Map.of(MAGS_EMAIL, "judge@example.com"));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        final CourtScheduleJudiciary courtScheduleJudiciary1 = CourtScheduleJudiciary.judiciary()
                .withJudiciaryId(judiciaryId)
                .withCourtListingProfileId(courtListingProfileId1)
                .withPosition("CHAIR")
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .build();

        final CourtScheduleJudiciary courtScheduleJudiciary2 = CourtScheduleJudiciary.judiciary()
                .withJudiciaryId(judiciaryId)
                .withCourtListingProfileId(courtListingProfileId2)
                .withPosition("LEFT_WINGER")
                .withIsBenchChairman(false)
                .withIsDeputy(true)
                .build();

        when(judiciaryBuilder.build(anyMap(), anyString()))
                .thenReturn(courtScheduleJudiciary1)
                .thenReturn(courtScheduleJudiciary2);

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(judiciaryId), is(true));
        final List<JudiciaryCourtScheduleData> dataList = result.get(judiciaryId);
        assertThat(dataList.size(), is(2));
        
        // Verify both schedule data entries are present
        assertThat(dataList.stream().anyMatch(d -> d.courtScheduleIds().contains(sessionId1)), is(true));
        assertThat(dataList.stream().anyMatch(d -> d.courtScheduleIds().contains(sessionId2)), is(true));
    }

    @Test
    void shouldReturnEmptyMap_WhenScheduleJudiciaryListIsEmpty() {
        // given
        final Map<String, UUID> judiciaryMap = Map.of("justice-1", UUID.randomUUID());
        final Map<String, Set<UUID>> courtScheduleMap = Map.of("listing-1", Set.of(UUID.randomUUID()));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        records.put(SCHEDULE, schedules); // Empty schedules

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    @Test
    void shouldSkipSchedule_WhenCourtScheduleMapDoesNotContainListingProfile() {
        // given
        final String justiceId = "justice-1";
        final String courtListingProfileId = "listing-1";
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);

        final Map<String, UUID> judiciaryMap = Map.of(justiceId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of("different-listing", Set.of(UUID.randomUUID()));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, justiceId);
        schedule.put(COURT_LISTING_PROFILE_ID, courtListingProfileId);
        schedule.put(JUDICIARY_ID, judiciaryId);
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(justiceId, Map.of(MAGS_EMAIL, "judge@example.com"));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        final CourtScheduleJudiciary courtScheduleJudiciary = CourtScheduleJudiciary.judiciary()
                .withJudiciaryId(judiciaryId)
                .withCourtListingProfileId(courtListingProfileId)
                .withPosition("CHAIR")
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .build();

        when(judiciaryBuilder.build(anyMap(), anyString())).thenReturn(courtScheduleJudiciary);

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }

    // ============================================================================
    // Tests for logJudiciaryMissingMessage (tested via createJudiciaryCourtScheduleMap)
    // ============================================================================

    @Test
    void shouldLogJudiciaryMissingMessage_WhenJudiciaryNotFoundInSchedule() {
        // given
        final String rotaJusticeId = "justice-1";
        final String email = "missing@example.com";
        final String firstName = "John";
        final String lastName = "Doe";
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);
        final String courtListingProfileId = "listing-1";

        final Map<String, UUID> judiciaryMap = Map.of(rotaJusticeId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(courtListingProfileId, Set.of(UUID.randomUUID()));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, rotaJusticeId);
        schedule.put(EMAIL_ADDRESS, email);
        schedule.put(FORENAMES, firstName);
        schedule.put(SURNAME, lastName);
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(rotaJusticeId, Map.of(MAGS_EMAIL, email, JUDGE_FORENAMES, firstName, JUDGE_SURNAME, lastName));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(email), eq(executionId)))
                .thenReturn(Optional.empty());

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(notNullValue()));
        assertThat(result.isEmpty(), is(true));

        // Verify that logJudiciaryMissingMessage was called via rotaProcessLogService
        final ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> logCaptor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        final uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(JUDICIARY_NOT_FOUND.code()));
        assertThat(log.getErrorText(), is(notNullValue()));
        assertThat(log.getErrorText().contains("Judiciary detail not found"), is(true));
    }

    @Test
    void shouldNotLogJudiciaryMissingMessage_WhenNoErrors() {
        // given
        final String rotaJusticeId = "justice-1";
        final String email = "judge@example.com";
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);
        final String courtListingProfileId = "listing-1";

        final Map<String, UUID> judiciaryMap = Map.of(rotaJusticeId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(courtListingProfileId, Set.of(UUID.randomUUID()));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, rotaJusticeId);
        schedule.put(EMAIL_ADDRESS, email);
        schedule.put(COURT_LISTING_PROFILE_ID, courtListingProfileId);
        schedule.put(JUDICIARY_ID, judiciaryId);
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(rotaJusticeId, Map.of(MAGS_EMAIL, email));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(email), eq(executionId)))
                .thenReturn(Optional.of(judiciary));

        final CourtScheduleJudiciary courtScheduleJudiciary = CourtScheduleJudiciary.judiciary()
                .withJudiciaryId(judiciaryId)
                .withCourtListingProfileId(courtListingProfileId)
                .build();
        when(judiciaryBuilder.build(anyMap(), anyString())).thenReturn(courtScheduleJudiciary);

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(notNullValue()));
        // Should not log when there are no errors
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldNotLogJudiciaryMissingMessage_WhenExecutionIdIsNull() {
        // given
        final String rotaJusticeId = "justice-1";
        final String email = "missing@example.com";
        final String firstName = "John";
        final String lastName = "Doe";
        final UUID judiciaryUuid = UUID.fromString(judiciaryId);
        final String courtListingProfileId = "listing-1";

        final Map<String, UUID> judiciaryMap = Map.of(rotaJusticeId, judiciaryUuid);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(courtListingProfileId, Set.of(UUID.randomUUID()));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, String> schedule = new HashMap<>();
        schedule.put(ROTA_JUDICIARY_ID, rotaJusticeId);
        schedule.put(EMAIL_ADDRESS, email);
        schedule.put(FORENAMES, firstName);
        schedule.put(SURNAME, lastName);
        schedules.put("schedule-1", schedule);
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(rotaJusticeId, Map.of(MAGS_EMAIL, email, JUDGE_FORENAMES, firstName, JUDGE_SURNAME, lastName));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(email), eq(null)))
                .thenReturn(Optional.empty());

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, null);

        // then
        assertThat(result, is(notNullValue()));
        // Should not log when executionId is null
        verify(rotaProcessLogService, never()).saveRotaProcessLog(any());
    }

    @Test
    void shouldLogMultipleJudiciaryMissingMessages() {
        // given
        final String rotaJusticeId1 = "justice-1";
        final String rotaJusticeId2 = "justice-2";
        final String email1 = "missing1@example.com";
        final String email2 = "missing2@example.com";
        final String firstName1 = "John";
        final String lastName1 = "Doe";
        final String firstName2 = "Jane";
        final String lastName2 = "Smith";
        final UUID judiciaryUuid1 = UUID.fromString(judiciaryId);
        final UUID judiciaryUuid2 = UUID.randomUUID();
        final String courtListingProfileId = "listing-1";

        final Map<String, UUID> judiciaryMap = Map.of(rotaJusticeId1, judiciaryUuid1, rotaJusticeId2, judiciaryUuid2);
        final Map<String, Set<UUID>> courtScheduleMap = Map.of(courtListingProfileId, Set.of(UUID.randomUUID()));

        final Map<String, Map<String, String>> schedules = new HashMap<>();
        
        final Map<String, String> schedule1 = new HashMap<>();
        schedule1.put(ROTA_JUDICIARY_ID, rotaJusticeId1);
        schedule1.put(EMAIL_ADDRESS, email1);
        schedule1.put(FORENAMES, firstName1);
        schedule1.put(SURNAME, lastName1);
        schedules.put("schedule-1", schedule1);

        final Map<String, String> schedule2 = new HashMap<>();
        schedule2.put(ROTA_JUDICIARY_ID, rotaJusticeId2);
        schedule2.put(EMAIL_ADDRESS, email2);
        schedule2.put(FORENAMES, firstName2);
        schedule2.put(SURNAME, lastName2);
        schedules.put("schedule-2", schedule2);
        
        records.put(SCHEDULE, schedules);

        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(rotaJusticeId1, Map.of(MAGS_EMAIL, email1, JUDGE_FORENAMES, firstName1, JUDGE_SURNAME, lastName1));
        magistrates.put(rotaJusticeId2, Map.of(MAGS_EMAIL, email2, JUDGE_FORENAMES, firstName2, JUDGE_SURNAME, lastName2));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(email1), eq(executionId)))
                .thenReturn(Optional.empty());
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(  eq(email2), eq(executionId)))
                .thenReturn(Optional.empty());

        // when
        final Map<String, List<JudiciaryCourtScheduleData>> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, executionId);

        // then
        assertThat(result, is(notNullValue()));

        // Verify that logJudiciaryMissingMessage was called with both errors
        final ArgumentCaptor<uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog> logCaptor =
                ArgumentCaptor.forClass(uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());

        final uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog log = logCaptor.getValue();
        assertThat(log.getExecutionId(), is(executionId));
        assertThat(log.getErrorCode(), is(JUDICIARY_NOT_FOUND.code()));
        assertThat(log.getErrorText(), is(notNullValue()));
        assertThat(log.getErrorText().contains("John Doe"), is(true));
        assertThat(log.getErrorText().contains("Jane Smith"), is(true));
    }
}

