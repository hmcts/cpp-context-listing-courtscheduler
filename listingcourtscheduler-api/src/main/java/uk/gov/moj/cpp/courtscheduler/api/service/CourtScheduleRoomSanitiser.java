package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackResponse;

import java.util.List;

/**
 * A draft (unallocated) court schedule session has no confirmed courtroom — the room is only
 * settled at allocation. The courtscheduler read endpoints must therefore not expose a courtroom
 * for draft sessions. See ADR-005 (Crown court scheduling ADRs); mirrors the listing-command-side
 * invariant already enforced in CourtScheduleEnrichmentService.
 *
 * <p>The courtScheduleId and courthouse remain — only the room within the venue is provisional.
 */
public final class CourtScheduleRoomSanitiser {

    private CourtScheduleRoomSanitiser() {
    }

    public static void stripCourtRoomFromDraftSessions(final List<CourtSchedule> courtSchedules) {
        if (courtSchedules == null) {
            return;
        }
        for (final CourtSchedule courtSchedule : courtSchedules) {
            if (courtSchedule != null && courtSchedule.isDraft()) {
                courtSchedule.setCourtRoomId(null);
                courtSchedule.setCourtRoomName(null);
                courtSchedule.setCourtRoomNumber(null);
            }
        }
    }

    /**
     * The crown fallback search-and-book result carries its own draft flag. When the booked session
     * is draft, drop its courtRoomId so consumers never see a provisional room. The draft flag is
     * retained so callers still know the session is unallocated.
     */
    public static CrownFallbackResponse stripCourtRoomFromDraftFallbackResponse(final CrownFallbackResponse response) {
        if (response == null || !Boolean.TRUE.equals(response.isDraft())) {
            return response;
        }
        return new CrownFallbackResponse(
                response.hearingId(),
                response.courtScheduleId(),
                null,
                response.sessionDate(),
                response.sessionStartTime(),
                response.sessionEndTime(),
                response.durationInMinutes(),
                response.isDraft(),
                response.businessType(),
                response.source(),
                response.overbooked());
    }
}
