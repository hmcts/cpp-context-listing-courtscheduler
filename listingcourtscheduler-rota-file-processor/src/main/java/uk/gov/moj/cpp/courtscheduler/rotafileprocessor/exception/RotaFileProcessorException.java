package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.exception;

import static java.lang.String.format;

import org.apache.commons.lang3.exception.ExceptionUtils;

public class RotaFileProcessorException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RotaFileProcessorException(final Throwable ex) {
        super(ExceptionUtils.getStackTrace(ex), ex);
    }

    public RotaFileProcessorException(final String message, final Throwable ex) {
        super(format("%s %s",message, ExceptionUtils.getStackTrace(ex)), ex);
    }
}
