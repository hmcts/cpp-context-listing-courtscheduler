package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

public record HearingSlotRequestParam(String panel,
                                      String sessionStartDate,
                                      String sessionEndDate,
                                      String exactHearingStartDateTime,
                                      String oucodeL2Code,
                                      String ouCode,
                                      String pageSize,
                                      String pageNumber,
                                      String courtRoomId,
                                      String courtRoomNumber,
                                      String businessType,
                                      String courtSession,
                                      Boolean isSlotBased,
                                      String hearingStartTime,
                                      Boolean showOverbookedSlots,
                                      String duration,
                                      String status,
                                      String jurisdiction,
                                      Boolean isWeekCommencing) {

    private static final String CROWN = "CROWN";

    /**
     * A CROWN hearing longer than a single sitting day. 360 is the full-day duration used
     * throughout the scheduler (see {@code SessionAvailability.FULL_DAY_DURATION_MINS}); it is
     * restated here because {@code listingcourtscheduler-domain} sits below the module that
     * owns that constant, and both the API search service and the viewstore query builder need
     * to agree on this one predicate.
     */
    public static final int FULL_DAY_DURATION_MINS = 360;

    /**
     * Without the week commencing filter: {@link #isWeekCommencing()} is {@code null}, which the
     * hearing slots query treats as "non week commencing sessions only". The hearing ids query
     * ignores the field.
     */
    public HearingSlotRequestParam(final String panel, final String sessionStartDate, final String sessionEndDate,
                                   final String exactHearingStartDateTime, final String oucodeL2Code, final String ouCode,
                                   final String pageSize, final String pageNumber, final String courtRoomId,
                                   final String courtRoomNumber, final String businessType, final String courtSession,
                                   final Boolean isSlotBased, final String hearingStartTime, final Boolean showOverbookedSlots,
                                   final String duration, final String status, final String jurisdiction) {
        this(panel, sessionStartDate, sessionEndDate, exactHearingStartDateTime, oucodeL2Code, ouCode, pageSize, pageNumber,
                courtRoomId, courtRoomNumber, businessType, courtSession, isSlotBased, hearingStartTime, showOverbookedSlots,
                duration, status, jurisdiction, null);
    }

    /** True only when the caller asked for week commencing sessions; absent or false means ordinary sessions. */
    public boolean weekCommencingOnly() {
        return Boolean.TRUE.equals(isWeekCommencing);
    }

    /**
     * SPRDT-1276: the discriminator for "this search can only be satisfied by whole,
     * duration-based days". MAGISTRATES never satisfies it, so every rule keyed on this method
     * is CROWN-only by construction.
     *
     * <p>A malformed duration answers {@code false} rather than throwing: this is evaluated on
     * every hearing-slots query, including MAGISTRATES ones that never parsed the duration
     * before, and it must not introduce a new failure mode for them.</p>
     */
    public boolean isCrownMultiDaySearch() {
        if (weekCommencingOnly() || !CROWN.equalsIgnoreCase(jurisdiction) || duration == null || duration.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(duration.trim()) > FULL_DAY_DURATION_MINS;
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HearingSlotRequestParam that = (HearingSlotRequestParam) o;
        return Objects.equals(panel(), that.panel()) && Objects.equals(sessionStartDate(),
                that.sessionStartDate()) && Objects.equals(sessionEndDate(),
                that.sessionEndDate())
                && Objects.equals(exactHearingStartDateTime(),
                that.exactHearingStartDateTime())
                && Objects.equals(oucodeL2Code(),
                that.oucodeL2Code()) && Objects.equals(ouCode(),
                that.ouCode()) && Objects.equals(pageSize(),
                that.pageSize()) && Objects.equals(pageNumber(),
                that.pageNumber()) && Objects.equals(courtRoomId(),
                that.courtRoomId()) && Objects.equals(courtRoomNumber(),
                that.courtRoomNumber()) && Objects.equals(businessType(),
                that.businessType()) && Objects.equals(courtSession(),
                that.courtSession()) && Objects.equals(isSlotBased(), that.isSlotBased())
                && Objects.equals(hearingStartTime(),that.hearingStartTime())
                && Objects.equals(showOverbookedSlots(), that.showOverbookedSlots()) &&
                Objects.equals(duration(), that.duration()) &&
                Objects.equals(status(), that.status()) &&
                Objects.equals(jurisdiction(), that.jurisdiction()) &&
                Objects.equals(isWeekCommencing(), that.isWeekCommencing());
    }

    @Override
    public int hashCode() {
        return Objects.hash(panel(), sessionStartDate(), sessionEndDate(), exactHearingStartDateTime(), oucodeL2Code(),
                ouCode(), pageSize(), pageNumber(), courtRoomId(), courtRoomNumber(),
                businessType(), courtSession(), isSlotBased(), hearingStartTime(), showOverbookedSlots(), duration(),
                status(), jurisdiction(), isWeekCommencing());
    }
}
