package uk.gov.moj.cpp.courtscheduler.exception;

public class CourtScheduleIdNotMatchingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CourtScheduleIdNotMatchingException(final String message) {
        super(message);
    }

}
