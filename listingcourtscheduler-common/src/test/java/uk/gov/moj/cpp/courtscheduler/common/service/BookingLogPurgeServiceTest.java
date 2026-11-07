package uk.gov.moj.cpp.courtscheduler.common.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.repository.BookingLogRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookingLogPurgeServiceTest {

    @InjectMocks
    private BookingLogPurgeService bookingLogPurgeService;

    @Mock
    private BookingLogRepository bookingLogRepository;

    @Test
    void shouldDeleteSupersededRowsOlderThanTheRetentionPeriod() {
        ReflectionTestUtils.setField(bookingLogPurgeService, "retentionDays", 60);
        when(bookingLogRepository.deleteSupersededRecordedBefore(any(Instant.class))).thenReturn(5);

        final int deleted = bookingLogPurgeService.purge();

        assertThat(deleted, is(5));
        final ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(bookingLogRepository).deleteSupersededRecordedBefore(cutoff.capture());
        final Instant expected = Instant.now().minus(60, ChronoUnit.DAYS);
        assertTrue(Duration.between(cutoff.getValue(), expected).abs().getSeconds() < 5,
                "cutoff " + cutoff.getValue() + " should be ~" + expected);
    }
}
