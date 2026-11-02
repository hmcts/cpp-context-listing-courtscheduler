package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.util.List;

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
