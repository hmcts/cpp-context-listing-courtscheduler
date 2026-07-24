package uk.gov.moj.cpp.courtscheduler.exception;

public class ProvisionalSlotNotFoundException extends RuntimeException {

    public ProvisionalSlotNotFoundException(final String message) {
        super(message);
    }
}
