package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SlotsRemoveServiceTest {

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @InjectMocks
    private SlotsRemoveService slotsRemoveService;

    @BeforeEach
    public void setUp() {
        setField(slotsRemoveService, "courtScheduleRepository", courtScheduleRepository);
    }

    @Test
    public void shouldRemoveSlots() {
        final String hearingId = randomUUID().toString();

        slotsRemoveService.remove(hearingId);

        verify(courtScheduleRepository, atLeastOnce()).releaseOldAllocatedListings(hearingId);
    }
}
