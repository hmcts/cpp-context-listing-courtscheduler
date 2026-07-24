package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

/**
 * Response for {@code courtscheduler.change-court-room-for-multiday-hearing}.
 *
 * <p>{@code source} is {@code CHANGE_COURT_ROOM_MULTIDAY}. {@code allocatedSchedules} holds the
 * (post-change) sessions for every requested day, including idempotent no-op days.</p>
 */
public record ChangeCourtRoomForMultidayHearingResponse(String hearingId,
                                                        String source,
                                                        List<CourtSchedule> allocatedSchedules) {
}
