package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataMapperServiceTest {

    @InjectMocks
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private ReferenceDataCache referenceDataCache;

    @Mock
    private Requester requester;

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Test
    void shouldFindByEmail() throws JsonProcessingException {
        final List<Judiciary> judiciaries = getJudiciaries();
        when(referenceDataCache.getJudiciaries(eq(requester))).thenReturn(judiciaries);

        final String emailFilter = "TienaTSSvenTS@moj.gov.uk";

        final Optional<Judiciary> judiciaryOptional = referenceDataMapperService.findByEmail(requester, emailFilter);

        assertTrue(judiciaryOptional.isPresent());
        judiciaries.stream()
                .filter(judiciary -> judiciary.getEmailAddress().equals(emailFilter))
                .findFirst()
                .ifPresent(judiciary -> {
                    assertEquals(judiciary.getId(), judiciaryOptional.get().getId());
                    assertEquals(judiciary.getForenames(), judiciaryOptional.get().getForenames());
                    assertEquals(judiciary.getSurname(), judiciaryOptional.get().getSurname());
                    assertEquals(judiciary.getJudiciaryType(), judiciaryOptional.get().getJudiciaryType());
                        }

                );

        verify(referenceDataCache, atLeastOnce()).getJudiciaries(eq(requester));
    }

    @Test
    void shouldFindByOuCodeAndRoomIdAndListingSessionAndBusinessType() throws JsonProcessingException {

        when(referenceDataCache.getCourtRoomSessionAllocations(eq(requester))).thenReturn(getCourtRoomSessionAllocations());

        final Optional<CourtRoomSessionAllocation> courtRoomSessionAllocationOptional = referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(requester, "B01KR00", 2035, "FRIPM", "GEN");

        assertTrue(courtRoomSessionAllocationOptional.isPresent());
        assertEquals("93231aab-a87e-3dbd-b334-402e07643f2f", courtRoomSessionAllocationOptional.get().getId());

        verify(referenceDataCache, atLeastOnce()).getCourtRoomSessionAllocations(eq(requester));
    }

    private List<CourtRoomSessionAllocation> getCourtRoomSessionAllocations() throws JsonProcessingException {
        final String courtRoomSessionAllocationsJsonStr = FileUtil.fileToString("/test-data/court-room-session-allocations-domain-data.json");

        return objectMapper.readValue(courtRoomSessionAllocationsJsonStr, new TypeReference<>() {});
    }


    private List<Judiciary> getJudiciaries() throws JsonProcessingException {
        final String judiciariesJsonStr = FileUtil.fileToString("/test-data/judiciaries-domain-data.json");

        return objectMapper.readValue(judiciariesJsonStr, new TypeReference<>() {});
    }

}
