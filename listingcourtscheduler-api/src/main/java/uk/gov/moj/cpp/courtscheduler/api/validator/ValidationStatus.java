package uk.gov.moj.cpp.courtscheduler.api.validator;

public enum ValidationStatus {
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE");

    private final String status;

    private ValidationStatus(String validationStatus) {
        this.status = validationStatus;
    }

    public String getValidationStatus() {
        return this.status;
    }
}