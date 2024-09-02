package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
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

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

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

    private List<CourtScheduleJudiciary> getCourtScheduleJudiciaryEntities(final String courtScheduleId, final String courtListingProfileId) throws JsonProcessingException {
        final String courtScheduleDomainsJsonString = FileUtil.fileToString("/test-data/court-schedule-judiciaries-entity-data.json")
                .replaceAll("COURT_SCHEDULE_ID", courtScheduleId)
                .replaceAll("COURT_LISTING_PROFILE_ID", courtListingProfileId);

        return objectMapper.readValue(courtScheduleDomainsJsonString, new TypeReference<List<CourtScheduleJudiciary>>(){});
    }

}
