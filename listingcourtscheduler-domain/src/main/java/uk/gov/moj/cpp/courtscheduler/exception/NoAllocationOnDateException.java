package uk.gov.moj.cpp.courtscheduler.exception;

/**
 * Thrown by {@code courtscheduler.change-court-room-for-multiday-hearing} when the target
 * hearing has no existing allocation on one of the requested dates. Maps to HTTP 422 with
 * errorCode {@code NO_ALLOCATION_ON_DATE}.
 */
public class NoAllocationOnDateException extends RuntimeException {

    public NoAllocationOnDateException(final String message) {
        super(message);
    }
}
