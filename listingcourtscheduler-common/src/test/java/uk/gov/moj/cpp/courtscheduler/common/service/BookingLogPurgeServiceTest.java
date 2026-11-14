package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.repository.BookingLogRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BookingLogPurgeServiceTest {

    @InjectMocks
    private BookingLogPurgeService bookingLogPurgeService;

    @Mock
    private BookingLogRepository bookingLogRepository;

    @Test
    void shouldDeleteTheRowsOfDeletedSessions() {
        when(bookingLogRepository.deleteRowsOfDeletedSessions()).thenReturn(7);

        assertThat(bookingLogPurgeService.deleteRowsOfDeletedSessions(), is(7));
    }
}
