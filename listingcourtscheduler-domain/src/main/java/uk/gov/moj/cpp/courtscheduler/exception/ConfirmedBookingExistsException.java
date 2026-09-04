package uk.gov.moj.cpp.courtscheduler.exception;

/**
 * Thrown by {@code reserve-unconfirmed-hearing} when the hearing already has a CONFIRMED
 * allocation (an {@code allocated_listings} row with {@code expires_at IS NULL}) — reserving
 * would otherwise silently release and replace a real booking. Maps to HTTP 409.
 */
public class ConfirmedBookingExistsException extends RuntimeException {

    public ConfirmedBookingExistsException(final String message) {
        super(message);
    }
}
