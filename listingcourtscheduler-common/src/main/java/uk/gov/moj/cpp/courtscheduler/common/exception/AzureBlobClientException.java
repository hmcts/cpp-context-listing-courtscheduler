package uk.gov.moj.cpp.courtscheduler.common.exception;

public class AzureBlobClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AzureBlobClientException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public AzureBlobClientException(final String message) {
        super(message);
    }

}
