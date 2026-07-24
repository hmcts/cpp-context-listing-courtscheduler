package uk.gov.moj.cpp.courtscheduler.exception;

import static java.lang.String.format;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class SlotsBookException extends RuntimeException {

    public SlotsBookException(final Throwable ex) {
        super(ExceptionUtils.getStackTrace(ex),ex);
    }

    public SlotsBookException(final String message) {
        super(message);
    }

    public SlotsBookException(final String message, final Throwable ex) {
        super(format("%s %s",message,ExceptionUtils.getStackTrace(ex)), ex);
    }
}
