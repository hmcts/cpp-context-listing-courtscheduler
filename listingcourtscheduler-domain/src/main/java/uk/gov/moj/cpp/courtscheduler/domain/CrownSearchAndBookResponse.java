package uk.gov.moj.cpp.courtscheduler.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response for {@code courtscheduler.crown.search.and.book} (SPRDT-1089).
 *
 * <p>{@code sessions} holds one entry per booked court day (1 for single-day, N for multi-day).
 * {@code source} echoes the audit/fallback source (e.g. {@code CROWN_FB_*}) when present.</p>
 *
 * <p>The flat fallback fields ({@code courtRoomId}, {@code sessionDate}, etc.) are populated for
 * single-day bookings only; they are {@code null} for multi-day bookings which use
 * {@code sessions} instead.</p>
 *
 * <p>Serialisation note: {@code isDraft} and {@code overbooked} are annotated with
 * {@code @JsonProperty} to preserve the exact wire-contract key names, because the framework
 * ObjectMapper (NON_ABSENT) would otherwise strip the "is" prefix from {@code isDraft()}.</p>
 */
public record CrownSearchAndBookResponse(String hearingId,
                                         String courtScheduleId,
                                         String source,
                                         List<CourtSchedule> sessions,
                                         String courtRoomId,
                                         String sessionDate,
                                         String sessionStartTime,
                                         String sessionEndTime,
                                         Integer durationInMinutes,
                                         @JsonProperty("isDraft") Boolean isDraft,
                                         String businessType,
                                         @JsonProperty("overbooked") Boolean overbooked) {
}
