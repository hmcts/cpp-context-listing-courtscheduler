package uk.gov.moj.cpp.courtscheduler.exception;

public class CrownFallbackNoSessionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CrownFallbackNoSessionException(final String message) {
        super(message);
    }
}
