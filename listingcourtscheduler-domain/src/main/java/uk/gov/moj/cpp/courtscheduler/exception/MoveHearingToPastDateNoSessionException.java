package uk.gov.moj.cpp.courtscheduler.exception;

public class MoveHearingToPastDateNoSessionException extends RuntimeException {

    public MoveHearingToPastDateNoSessionException(final String message) {
        super(message);
    }
}
