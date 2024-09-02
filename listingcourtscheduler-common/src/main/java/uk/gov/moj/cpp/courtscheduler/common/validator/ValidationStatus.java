package uk.gov.moj.cpp.courtscheduler.common.validator;

public enum ValidationStatus {
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE");

    private final String status;

    ValidationStatus(String validationStatus) {
        this.status = validationStatus;
    }

    public String getValidationStatus() {
        return this.status;
    }
}