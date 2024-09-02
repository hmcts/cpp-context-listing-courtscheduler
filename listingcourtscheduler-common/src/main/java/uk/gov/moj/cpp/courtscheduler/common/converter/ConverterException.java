package uk.gov.moj.cpp.courtscheduler.common.converter;

public class ConverterException extends RuntimeException {
    private static final long serialVersionUID = 4101915933800790698L;

    public ConverterException(final String message) {
        super(message);
    }

    public ConverterException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
