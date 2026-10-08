package uk.gov.moj.cpp.courtscheduler.common.exception;

public class AzureAPIMInvocationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AzureAPIMInvocationException(final String listType, final String publishingHubUrl) {
        super("Failed to invoke Azure APIM with url: " + publishingHubUrl + " for " + listType);
    }

    public AzureAPIMInvocationException(final String message) {
        super(message);
    }
}
