package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlotsRemoveServiceTest {

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @InjectMocks
    private SlotsRemoveService slotsRemoveService;

    @BeforeEach
    public void setUp() {
        setField(slotsRemoveService, "courtScheduleRepository", courtScheduleRepository);
    }

    @Test
    void shouldRemoveSlots() {
        final String hearingId = randomUUID().toString();

        slotsRemoveService.remove(hearingId);

        verify(courtScheduleRepository, atLeastOnce()).releaseOldAllocatedListings(hearingId);
    }

    /**
     * DELETE /sessions/{id} is the vacated-hearing release and takes hearing ids only. Unconfirmed
     * holds are released by booking id through POST /unconfirmedBooking/release; letting this
     * endpoint accept a booking id as well would point a hearing-keyed release - one that also
     * removes confirmed listings - at an id that was only ever meant to name a hold.
     */
    @Test
    void shouldReleaseByHearingIdOnly() {
        final String hearingId = randomUUID().toString();

        slotsRemoveService.remove(hearingId);

        verify(courtScheduleRepository).releaseOldAllocatedListings(hearingId);
        verifyNoMoreInteractions(courtScheduleRepository);
    }
}
