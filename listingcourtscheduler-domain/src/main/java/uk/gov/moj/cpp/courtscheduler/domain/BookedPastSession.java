package uk.gov.moj.cpp.courtscheduler.domain;

/**
 * One booked past day of a MAGISTRATES {@code courtscheduler.move-hearing-to-past-date} move -
 * main's per-slot {@code MoveHearingToPastDateResponse} restored under a new name (SPRDT-1447; the
 * old name is taken by the CROWN response). {@code sessionStartTime}/{@code sessionEndTime} carry the
 * SUBMITTED start/end time-of-day on the booked day (the session window only when no time was
 * supplied), and {@code durationInMinutes} the submitted window - main's contract, which listing
 * maps straight onto the moved hearing day.
 */
public record BookedPastSession(String hearingId,
                                String courtScheduleId,
                                String courtRoomId,
                                String sessionDate,
                                String sessionStartTime,
                                String sessionEndTime,
                                Integer durationInMinutes,
                                Boolean isDraft,
                                String businessType,
                                String source,
                                Boolean overbooked) {
}
