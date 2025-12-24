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
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.DISTRICT_JUDGES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.SCHEDULE;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.RotaReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.api.service.rota.helper.JudiciaryCourtScheduleData;
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

    @Mock
    private Requester requester;

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

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(eq(requester), eq(magistrateEmail), eq(executionId)))
                .thenReturn(Optional.of(judiciary));
        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(eq(requester), eq(judgeEmail), eq(executionId)))
                .thenReturn(Optional.of(judiciary));

        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(records, requester, executionId);

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
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(null, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(referenceDataValidationService, never()).validateAndFindJudiciaryByEmail(any(), anyString(), anyString());
    }

    @Test
    void shouldReturnEmptyMap_WhenRecordsIsEmpty() {
        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(emptyMap(), requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(referenceDataValidationService, never()).validateAndFindJudiciaryByEmail(any(), anyString(), anyString());
    }

    @Test
    void shouldSkipJudiciary_WhenEmailIsMissing() {
        // given
        final String magistrateId = "mag-1";
        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(magistrateId, Map.of("otherField", "value"));
        records.put(MAGISTRATES, magistrates);

        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(referenceDataValidationService, never()).validateAndFindJudiciaryByEmail(any(), anyString(), anyString());
    }

    @Test
    void shouldLogMissingJudiciary_WhenJudiciaryNotFound() {
        // given
        final String magistrateId = "mag-1";
        final String magistrateEmail = "missing@example.com";
        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        magistrates.put(magistrateId, Map.of(MAGS_EMAIL, magistrateEmail));
        records.put(MAGISTRATES, magistrates);

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(eq(requester), eq(magistrateEmail), eq(executionId)))
                .thenReturn(Optional.empty());

        // when
        final Map<String, UUID> result = rotaJudiciaryHelper.createJudiciaryMap(records, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
        verify(rotaProcessLogService).saveRotaProcessLog(any());
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

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(eq(requester), eq(email), eq(executionId)))
                .thenReturn(Optional.of(judiciary));

        final Map<String, String> errors = new HashMap<>();

        // when
        rotaJudiciaryHelper.enrichScheduleWithJudiciaryInfo(schedule, judiciariesMap, rotaJusticeId, requester, executionId, errors);

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

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(eq(requester), eq(email), eq(executionId)))
                .thenReturn(Optional.empty());

        final Map<String, String> errors = new HashMap<>();

        // when
        rotaJudiciaryHelper.enrichScheduleWithJudiciaryInfo(schedule, judiciariesMap, rotaJusticeId, requester, executionId, errors);

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

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(any(), anyString(), anyString()))
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
        final Map<String, JudiciaryCourtScheduleData> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, requester, executionId);

        // then
        assertThat(result.size(), is(1));
        assertThat(result.containsKey(judiciaryId), is(true));
        final JudiciaryCourtScheduleData data = result.get(judiciaryId);
        assertThat(data, is(notNullValue()));
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
        final Map<String, JudiciaryCourtScheduleData> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, requester, executionId);

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
        final Map<String, JudiciaryCourtScheduleData> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, requester, executionId);

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

        when(referenceDataValidationService.validateAndFindJudiciaryByEmail(any(), anyString(), anyString()))
                .thenReturn(Optional.of(judiciary));

        // when
        final Map<String, JudiciaryCourtScheduleData> result = rotaJudiciaryHelper.createJudiciaryCourtScheduleMap(
                records, judiciaryMap, courtScheduleMap, requester, executionId);

        // then
        assertThat(result, is(emptyMap()));
    }
}

