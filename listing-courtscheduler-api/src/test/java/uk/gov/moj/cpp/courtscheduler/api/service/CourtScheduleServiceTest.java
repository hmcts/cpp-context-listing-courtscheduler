package uk.gov.moj.cpp.courtscheduler.api.service;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static io.smallrye.common.constraint.Assert.assertTrue;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleServiceTest {
    @Mock
    private CourtScheduleRepository courtScheduleRepository;
    @Mock
    private AllocatedListingRepository allocatedListingRepository;

    @Mock
    private Requester requester;

    @Mock
    ReferenceDataCache referenceDataCache;

    @InjectMocks
    private CourtScheduleService courtScheduleService;

    @Test
    void shouldGetCourtSchedulesBetweenLastUpdatedOn() {
        // given
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParam();
        CourtSchedule courtSchedule = new CourtSchedule();
        given(courtScheduleRepository.findBy(courtScheduleRequestParam)).willReturn(List.of(courtSchedule));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(courtSchedule.getBusinessType()), eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));

        List<CourtSchedule> courtSchedules = courtScheduleService.getCourtSchedules(courtScheduleRequestParam, requester);

        assertThat(courtSchedules.contains(courtSchedule), is(true));
    }

    @Test
    void shouldProcessProvisionalBookingRequestSuccessfully() {
        SessionsParam sessionsParam = new SessionsParam();
        sessionsParam.setSessions(List.of("1", "2"));
        List<CourtSchedule> courtSchedules = new ArrayList<>();

        when(courtScheduleRepository.deleteCourtSchedule(anyList())).thenReturn(courtSchedules);

        JsonObject response = courtScheduleService.deleteCourtScheduleSessions(sessionsParam);

        assertTrue(response.get("sessions").asJsonArray().isEmpty());
    }

    @Test
    void shouldUpdateCourtScheduleWhenNoBusinessTypeChange() {
        final String courtScheduleId = randomUUID().toString();
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionDate(random(LocalDate.class));
        updateCourtSchedule.setSessionType(random(String.class));

        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any())).thenReturn(Result.SUCCESS());
        Result result = courtScheduleService.update(updateCourtSchedule, requester);
        assertThat(result.isSuccess(), is(true));
    }

    private static uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule getPersistedCourtSchedule(final String courtScheduleId, final String businessTypeCode) {
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtSchedule = random(uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule.class);
        courtSchedule.setCourtScheduleId(courtScheduleId);
        courtSchedule.setBusinessType(businessTypeCode);
        courtSchedule.setSessionDate(random(LocalDate.class));
        courtSchedule.setCourtSession(random(String.class));
        return courtSchedule;
    }

    private CourtScheduleRequestParam courtScheduleRequestParam() {
        String courtCentreId = "courtCentreId";
        String courtRoomId = "courtRoomId";
        String businessType = "businessType";
        String sessionStartDate = "2024-12-01";
        String sessionEndDate = "2024-12-03";
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId,
                businessType, sessionStartDate, sessionEndDate, pageSize, pageNumber);
    }

    private Optional<BusinessType> returnBusinessTypeObject(final String businessTypeCode, boolean isSlotBased) {
        return Optional.of(BusinessType.BusinessTypeBuilder.aBusinessType()
                .withId(randomUUID().toString())
                .withSeqNum(1)
                .withTypeCode(businessTypeCode)
                .withTypeDescription(businessTypeCode + "BusinessType")
                .withSlot(isSlotBased)
                .withDuration(!isSlotBased)
                .build());
    }
}

