package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

/**
 * Response for {@code courtscheduler.mags.search.and.book} (SPRDT-1089).
 *
 * <p>{@code sessions} holds the booked court days. For MAGS multi-day these are CONSECUTIVE
 * weekdays in one room + business type.</p>
 */
public record MagsSearchAndBookResponse(String hearingId,
                                        List<CourtSchedule> sessions) {
}
