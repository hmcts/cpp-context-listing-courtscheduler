package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

/**
 * Response for {@code courtscheduler.move-hearing-to-past-date} (SPRDT-1089).
 *
 * <p>{@code source} is {@code MOVE_TO_PAST_DATE}. {@code sessions} holds the booked past court days —
 * CONSECUTIVE weekdays (one room + business type) for both jurisdictions.</p>
 */
public record MoveHearingToPastDateResponse(String hearingId,
                                            String source,
                                            List<CourtSchedule> sessions) {
}
