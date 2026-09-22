package uk.gov.moj.cpp.courtscheduler.exception;

import static java.lang.String.format;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class PersistenceStoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PersistenceStoreException(final Exception ex) {
        super(ExceptionUtils.getStackTrace(ex), ex);
    }

    public PersistenceStoreException(final String message) {
        super(message);
    }

    public PersistenceStoreException(final String message, final Exception ex) {
        super(format("%s: %s", message, ExceptionUtils.getStackTrace(ex)), ex);
    }
}
