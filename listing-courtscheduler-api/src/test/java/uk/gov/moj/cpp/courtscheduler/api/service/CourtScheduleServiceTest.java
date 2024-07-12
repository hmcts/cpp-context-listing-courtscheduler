package uk.gov.moj.cpp.courtscheduler.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.*;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtMigrationRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import javax.json.JsonObject;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static io.smallrye.common.constraint.Assert.assertFalse;
import static io.smallrye.common.constraint.Assert.assertTrue;
import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

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

    @Mock
    CourtMigrationRepository courtMigrationRepository;
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
        updateCourtSchedule.setSessionType(random(String.class));
        when(referenceDataCache.getRotaCourtRoomByCourtRoomId(eq(updateCourtSchedule.getCourtRoomId()), eq(requester))).thenReturn(Optional.of(random(CourtRoom.class)));
        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = courtScheduleService.update(updateCourtSchedule, requester);
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    void shouldUpdateCourtScheduleWhenNoCourtRoomChange() {
        final String courtScheduleId = randomUUID().toString();
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = courtScheduleService.update(updateCourtSchedule, requester);
        assertThat(result.isSuccess(), is(true));
    }


    @Test
    void shouldUpdateCourtScheduleWhenSlotBasedChange() {
        final String courtScheduleId = randomUUID().toString();
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVLA");
        persistedCourtSchedule.setSlotBased(true);
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));
        updateCourtSchedule.setCourtRoomId(persistedCourtSchedule.getCourtRoomId());
        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
        when(allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(anyString())).thenReturn(0);
        when(courtScheduleRepository.update(any(), any(), any())).thenReturn(Result.SUCCESS());
        Result result = courtScheduleService.update(updateCourtSchedule, requester);
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    void shouldReturnFailure_WhenCourtScheduleId_NotFound() {
        final String courtScheduleId = randomUUID().toString();
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));

        when(courtScheduleRepository.findBy(anyString())).thenReturn(null);

        Result result = courtScheduleService.update(updateCourtSchedule, requester);

        assertEquals("Court Schedule not found", result.getMsg());
    }

    @Test
    void shouldReturnFailure_WhenBusinessTypeChanges() {
        final String courtScheduleId = randomUUID().toString();
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = getPersistedCourtSchedule(courtScheduleId, "DVAL");
        // given
        UpdateCourtSchedule updateCourtSchedule = random(UpdateCourtSchedule.class);
        updateCourtSchedule.setCourtScheduleId(courtScheduleId);
        updateCourtSchedule.setBusinessType("DVLA");
        updateCourtSchedule.setSessionType(random(String.class));

        when(courtScheduleRepository.findBy(anyString())).thenReturn(persistedCourtSchedule);
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(persistedCourtSchedule.getBusinessType()), eq(requester))).thenReturn(returnBusinessTypeObject("DVAL", true));
        when(referenceDataCache.getRotaBusinessTypeByCode(eq(updateCourtSchedule.getBusinessType()), eq(requester))).thenReturn(returnBusinessTypeObject("DVLA", true));

        Result result = courtScheduleService.update(updateCourtSchedule, requester);

        assertEquals("Business Type cannot be changed from Slot to Non-Slot and vice versa", result.getMsg());
    }

    @Test
    void shouldReturnMigratedCourt() {
        final String oucode = "B01LY00" ;
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(true);

        when(courtMigrationRepository.findByOuCode(oucode)).thenReturn(migrationStatus);
        assertTrue(courtScheduleService.isMigrated(oucode));

    }

    @Test
    void shouldReturnFalseForNonMigratedCourt() {
        final String oucode = "B01LY00" ;
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(randomUUID().toString());
        migrationStatus.setMigrated(false);

        when(courtMigrationRepository.findByOuCode(oucode)).thenReturn(migrationStatus);
        assertFalse(courtScheduleService.isMigrated(oucode));

    }
    @Test
    void shouldReturnMigratedCourtByCourtCentreId() {
        final String oucode = "B01LY00" ;
        final String courtCentreId = randomUUID().toString();
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(courtCentreId);
        migrationStatus.setMigrated(true);
        when(courtMigrationRepository.findByCourtCentreId(courtCentreId)).thenReturn(migrationStatus);
        assertTrue(courtScheduleService.isMigratedByCourtCentreId(courtCentreId));
    }

    @Test
    void shouldReturnFalseForNonMigratedCourtByCourtCentreId() {
        final String oucode = "B01LY00" ;
        final String courtCentreId = randomUUID().toString();
        CourtSchedulerMigrationStatus migrationStatus = new CourtSchedulerMigrationStatus();
        migrationStatus.setOuCode(oucode);
        migrationStatus.setCourtCentreId(courtCentreId);
        migrationStatus.setMigrated(false);
        when(courtMigrationRepository.findByCourtCentreId(courtCentreId)).thenReturn(migrationStatus);
        assertFalse(courtScheduleService.isMigratedByCourtCentreId(courtCentreId));
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

