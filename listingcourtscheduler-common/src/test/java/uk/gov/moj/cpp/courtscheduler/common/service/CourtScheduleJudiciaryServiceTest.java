package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleJudiciaryServiceTest {

    @InjectMocks
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Mock
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void shouldFindRelatedJudiciarySchedules() throws IOException {
        final String courtScheduleId = randomUUID().toString();
        final String courtListingProfileId = "CS2334175";
        final List<String> courtScheduleIds = List.of(courtScheduleId);

        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = getCourtScheduleJudiciaryEntities(courtScheduleId, courtListingProfileId);
        when(courtScheduleJudiciaryRepository.findInCourtScheduleIds(eq(courtScheduleIds))).thenReturn(courtScheduleJudiciaryEntities);

        final Map<String, List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary>> judiciarySchedulesMap = courtScheduleJudiciaryService.findRelatedJudiciarySchedules(courtScheduleIds);

        assertFalse(judiciarySchedulesMap.isEmpty());
        assertTrue(judiciarySchedulesMap.containsKey(courtListingProfileId));
        assertEquals(1, judiciarySchedulesMap.size());

        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> courtScheduleJudiciaries = judiciarySchedulesMap.get(courtListingProfileId);
        courtScheduleJudiciaryEntities.forEach(
                courtScheduleJudiciaryEntity -> {
                    courtScheduleJudiciaries.stream()
                            .filter(courtScheduleJudiciary -> courtScheduleJudiciaryEntity.getId().getJudiciaryId().equals(courtScheduleJudiciary.getJudiciaryId()))
                            .findFirst()
                            .ifPresent(courtScheduleJudiciary -> {
                                assertEquals(courtScheduleJudiciary.getCourtListingProfileId(), courtScheduleJudiciaryEntity.getCourtListingProfileId());
                                assertEquals(courtScheduleJudiciary.getJudiciaryType(), courtScheduleJudiciaryEntity.getJudiciaryType());
                                assertEquals(courtScheduleJudiciary.getPosition(), courtScheduleJudiciaryEntity.getPosition());
                                assertEquals(courtScheduleJudiciary.getForenames(), courtScheduleJudiciaryEntity.getForenames());
                                assertEquals(courtScheduleJudiciary.getSurname(), courtScheduleJudiciaryEntity.getSurname());
                                assertEquals(courtScheduleJudiciary.getRotaJudiciaryId(), courtScheduleJudiciaryEntity.getRotaJudiciaryId());
                            });
                }
        );

        verify(courtScheduleJudiciaryRepository, atLeastOnce()).findInCourtScheduleIds(eq(courtScheduleIds));
    }

    @Test
    void shouldGetUnAllocatedCourtScheduleJudiciariesForRotaPeriodGroupedByCourtScheduleId() {
        final java.time.LocalDate startDate = java.time.LocalDate.parse("2024-01-01");
        final java.time.LocalDate endDate = java.time.LocalDate.parse("2024-12-31");
        final List<String> ouCodes = List.of("B40IM00");
        final String courtScheduleIdA = randomUUID().toString();
        final String courtScheduleIdB = randomUUID().toString();

        final CourtScheduleJudiciary judiciaryA1 = buildEntity(courtScheduleIdA, "jud-1");
        final CourtScheduleJudiciary judiciaryA2 = buildEntity(courtScheduleIdA, "jud-2");
        final CourtScheduleJudiciary judiciaryB1 = buildEntity(courtScheduleIdB, "jud-3");
        when(courtScheduleJudiciaryRepository.findUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                eq(startDate), eq(endDate), eq(ouCodes)))
                .thenReturn(List.of(judiciaryA1, judiciaryA2, judiciaryB1));

        final Map<String, List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary>> result =
                courtScheduleJudiciaryService.getUnAllocatedCourtScheduleJudiciariesForRotaPeriod(startDate, endDate, ouCodes);

        assertEquals(2, result.size());
        assertEquals(2, result.get(courtScheduleIdA).size());
        assertEquals(1, result.get(courtScheduleIdB).size());

        final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary mappedJudiciary = result.get(courtScheduleIdB).get(0);
        assertEquals(courtScheduleIdB, mappedJudiciary.getCourtScheduleId());
        assertEquals("jud-3", mappedJudiciary.getJudiciaryId());
        assertEquals(judiciaryB1.getRotaJudiciaryId(), mappedJudiciary.getRotaJudiciaryId());
        assertEquals(judiciaryB1.getTitle(), mappedJudiciary.getTitle());
        assertEquals(judiciaryB1.getForenames(), mappedJudiciary.getForenames());
        assertEquals(judiciaryB1.getSurname(), mappedJudiciary.getSurname());
        assertEquals(judiciaryB1.getEmail(), mappedJudiciary.getEmailAddress());
        assertEquals(judiciaryB1.getJudiciaryType(), mappedJudiciary.getJudiciaryType());
        assertEquals(judiciaryB1.getBenchChairman(), mappedJudiciary.getBenchChairman());
        assertEquals(judiciaryB1.getDeputy(), mappedJudiciary.getDeputy());
        assertEquals(judiciaryB1.getPosition(), mappedJudiciary.getPosition());
        assertEquals(judiciaryB1.getCourtListingProfileId(), mappedJudiciary.getCourtListingProfileId());
        assertTrue(mappedJudiciary.isActive());
    }

    @Test
    void shouldReturnEmptyMapWhenNoUnAllocatedCourtScheduleJudiciariesFound() {
        final java.time.LocalDate startDate = java.time.LocalDate.parse("2024-01-01");
        final java.time.LocalDate endDate = java.time.LocalDate.parse("2024-12-31");
        final List<String> ouCodes = List.of("B40IM00");
        when(courtScheduleJudiciaryRepository.findUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(
                eq(startDate), eq(endDate), eq(ouCodes)))
                .thenReturn(List.of());

        final Map<String, List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary>> result =
                courtScheduleJudiciaryService.getUnAllocatedCourtScheduleJudiciariesForRotaPeriod(startDate, endDate, ouCodes);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldGetJudiciaryHearingInfoForCourtSchedules() {
        final List<String> courtScheduleIds = List.of(randomUUID().toString());
        final List<Object[]> rows = List.<Object[]>of(
                new Object[]{courtScheduleIds.get(0), randomUUID().toString(), "jud-1", "Magistrate", true, false});
        when(courtScheduleJudiciaryRepository.findJudiciaryHearingInfoByCourtScheduleIds(eq(courtScheduleIds)))
                .thenReturn(rows);

        final List<Object[]> result = courtScheduleJudiciaryService.getJudiciaryHearingInfoForCourtSchedules(courtScheduleIds);

        assertEquals(rows, result);
        verify(courtScheduleJudiciaryRepository, atLeastOnce()).findJudiciaryHearingInfoByCourtScheduleIds(eq(courtScheduleIds));
    }

    @Test
    void shouldNotQueryJudiciaryHearingInfoWhenNoCourtScheduleIdsProvided() {
        final List<Object[]> result = courtScheduleJudiciaryService.getJudiciaryHearingInfoForCourtSchedules(List.of());

        assertTrue(result.isEmpty());
        org.mockito.Mockito.verifyNoInteractions(courtScheduleJudiciaryRepository);
    }

    private CourtScheduleJudiciary buildEntity(final String courtScheduleId, final String judiciaryId) {
        return CourtScheduleJudiciary.CourtScheduleJudiciaryBuilder.courtScheduleJudiciary()
                .withId(new uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey(courtScheduleId, judiciaryId))
                .withCourtListingProfileId("CLP-" + judiciaryId)
                .withRotaJudiciaryId("ROTA-" + judiciaryId)
                .withTitle("Mr")
                .withForenames("John")
                .withSurname("Doe")
                .withEmail(judiciaryId + "@example.com")
                .withJudiciaryType("Magistrate")
                .withIsBenchChairman(true)
                .withIsDeputy(false)
                .withPosition("1")
                .withActive(true)
                .build();
    }

    @Test
    void shouldDeleteRedundantRotaData() {
        final int numberOfPreviousMonthsAndOlder = 6;
        final int numberOfDeleted = 5;
        when(courtScheduleJudiciaryRepository.deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder * 30))).thenReturn(numberOfDeleted);

        final int expectedNumberOfDeletion = courtScheduleJudiciaryService.deleteRedundantRotaData(numberOfPreviousMonthsAndOlder);
        verify(courtScheduleJudiciaryRepository, atLeastOnce()).deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder * 30));
        assertThat(expectedNumberOfDeletion, is(numberOfDeleted));
    }

    private List<CourtScheduleJudiciary> getCourtScheduleJudiciaryEntities(final String courtScheduleId, final String courtListingProfileId) throws JsonProcessingException {
        final String courtScheduleDomainsJsonString = FileUtil.fileToString("/test-data/court-schedule-judiciaries-entity-data.json")
                .replaceAll("COURT_SCHEDULE_ID", courtScheduleId)
                .replaceAll("COURT_LISTING_PROFILE_ID", courtListingProfileId);

        return objectMapper.readValue(courtScheduleDomainsJsonString, new TypeReference<List<CourtScheduleJudiciary>>(){});
    }

}
