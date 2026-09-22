package uk.gov.moj.cpp.courtscheduler.exception;

public class CrownFallbackInvalidRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CrownFallbackInvalidRequestException(final String message) {
        super(message);
    }
}
