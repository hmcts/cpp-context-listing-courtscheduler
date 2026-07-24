package uk.gov.moj.cpp.courtscheduler.exception;

public class CrownFallbackNoSessionException extends RuntimeException {

    public CrownFallbackNoSessionException(final String message) {
        super(message);
    }
}
