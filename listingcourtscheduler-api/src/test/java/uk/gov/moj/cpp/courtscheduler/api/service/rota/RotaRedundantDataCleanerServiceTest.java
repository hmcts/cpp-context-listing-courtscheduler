package uk.gov.moj.cpp.courtscheduler.api.service.rota;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.common.service.BookingLogPurgeService;
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleJudiciaryService;
import uk.gov.moj.cpp.courtscheduler.common.service.CourtScheduleService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaRedundantDataCleanerServiceTest {

    @InjectMocks
    private RotaRedundantDataCleanerService rotaRedundantDataCleanerService;

    @Mock
    private CourtScheduleService courtScheduleService;

    @Mock
    private CourtScheduleJudiciaryService courtScheduleJudiciaryService;

    @Mock
    private AllocatedListingService allocatedListingService;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Mock
    private BookingLogPurgeService bookingLogPurgeService;

    @Test
    void shouldCleanDataForPreviousMonths() {
        final int numberOfPreviousMonthsAndOlder = 6;
        when(allocatedListingService.deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder))).thenReturn(1);
        when(bookingLogPurgeService.deleteRowsOfDeletedSessions()).thenReturn(3);
        when(courtScheduleJudiciaryService.deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder))).thenReturn(5);
        when(courtScheduleService.deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder))).thenReturn(20);
        when(rotaProcessLogService.deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder))).thenReturn(10);

        rotaRedundantDataCleanerService.cleanDataForPreviousMonths(numberOfPreviousMonthsAndOlder);

        verify(allocatedListingService, atLeastOnce()).deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder));
        verify(bookingLogPurgeService, atLeastOnce()).deleteRowsOfDeletedSessions();
        verify(courtScheduleJudiciaryService, atLeastOnce()).deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder));
        verify(courtScheduleService, atLeastOnce()).deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder));
        verify(rotaProcessLogService, atLeastOnce()).deleteRedundantRotaData(eq(numberOfPreviousMonthsAndOlder));
    }

    @Test
    void shouldDeleteTheBookingLogLastOnceTheOldSessionsAreGone() {
        final int months = 6;

        rotaRedundantDataCleanerService.cleanDataForPreviousMonths(months);

        final InOrder order = inOrder(courtScheduleService, rotaProcessLogService, bookingLogPurgeService);
        order.verify(courtScheduleService).deleteRedundantRotaData(months);
        order.verify(rotaProcessLogService).deleteRedundantRotaData(months);
        order.verify(bookingLogPurgeService).deleteRowsOfDeletedSessions();
    }
}
