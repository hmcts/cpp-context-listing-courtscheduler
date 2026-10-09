package uk.gov.moj.cpp.courtscheduler.exception;

public class MoveHearingToPastDateNoSessionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MoveHearingToPastDateNoSessionException(final String message) {
        super(message);
    }
}
