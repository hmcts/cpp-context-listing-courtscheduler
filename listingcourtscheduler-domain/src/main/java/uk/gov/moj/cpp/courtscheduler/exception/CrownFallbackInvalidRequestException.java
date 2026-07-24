package uk.gov.moj.cpp.courtscheduler.exception;

public class CrownFallbackInvalidRequestException extends RuntimeException {

    public CrownFallbackInvalidRequestException(final String message) {
        super(message);
    }
}
