package uk.gov.moj.cpp.courtscheduler.exception;

/**
 * Raised when a session cannot take the requested hold — the capacity-decrementing pipeline
 * refused the slot. Distinct from a persistence failure: this is an expected, user-facing
 * outcome that the clerk sees as "No session available, please try again".
 */
public class NoCapacityException extends RuntimeException {

    public NoCapacityException(final String message) {
        super(message);
    }
}
