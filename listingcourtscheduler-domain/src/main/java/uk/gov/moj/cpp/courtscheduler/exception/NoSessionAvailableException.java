package uk.gov.moj.cpp.courtscheduler.exception;

/**
 * Thrown by search-and-book / move-hearing-to-past-date when no court session can be found
 * for the requested date(s) (SPRDT-1089). Maps to HTTP 404.
 */
public class NoSessionAvailableException extends RuntimeException {

    public NoSessionAvailableException(final String message) {
        super(message);
    }
}
