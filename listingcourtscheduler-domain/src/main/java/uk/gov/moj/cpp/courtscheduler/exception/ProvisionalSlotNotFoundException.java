package uk.gov.moj.cpp.courtscheduler.exception;

public class ProvisionalSlotNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProvisionalSlotNotFoundException(final String message) {
        super(message);
    }
}
