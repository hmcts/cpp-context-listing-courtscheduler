package uk.gov.moj.cpp.courtscheduler.persist.entity;

/**
 * A state a booking entered, as recorded in {@code booking_log} (LPT-2611). The latest row for a
 * booking is its current state; earlier rows are how it got there.
 */
public enum BookingState {
    /** A pick took the hold. */
    RESERVED,
    /** A re-pick under the same booking gave back the session it abandoned. */
    REPICK_RELEASED,
    /** Hearing released it: the result line no longer carries it. */
    DRAFT_RELEASED,
    /** Progression released it: a share merged its line onto another booking's session. */
    SURPLUS_RELEASED,
    /** Released by a caller that gave no reason. */
    RELEASED,
    /** The nightly purge removed the lapsed hold. */
    EXPIRED,
    /** A share turned it into a listing. */
    CONFIRMED,
    /** The pre-share check re-reserved it after it expired. */
    REBOOKED
}
