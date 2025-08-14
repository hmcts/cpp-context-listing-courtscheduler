package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;

public record IdResponse(String hearingId, String courtScheduleId, LocalDate hearingDate,
                         long hearingDayCount,
                         long hearingDayPosition) {
}