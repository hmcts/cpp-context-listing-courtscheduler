package uk.gov.moj.cpp.courtscheduler.common.exception;

@SuppressWarnings("squid:S1213")
public enum MissingDataError {

    JUDICIARY_NOT_FOUND(
            "JUDICIARY_NOT_FOUND",
            Prefix.MONITORING + "Judiciary detail not found for the following judiciaries :-%n%n%s"),

    BUSINESS_TYPES_NOT_FOUND(
            "BUSINESS_TYPES_NOT_FOUND",
            Prefix.MONITORING + "These business types cannot be found on newSlots : {}"),

    JUDICIARY_ERR_MSG(
            "JUDICIARY_ERR_MSG",
            "Name %s %s%nEmail : %s"),

    MISSING_COURT_SESSION(
            "MISSING_COURT_SESSION",
            Prefix.MONITORING + "No matching sessions found for courthouse %s:%n%s"),

    REF_DATA_VENUE_NOT_FOUND(
            "REF_DATA_VENUE_NOT_FOUND",
            Prefix.MONITORING + "No matching venue found by either venueId or venueName or LocationId:%n%s"),

    ROTA_PROCESSING_ERROR(
            "ROTA_PROCESSING_ERROR",
            "Uncategorised exception - %s"),

    CREATE_SESSIONS_COURTROOM_NOT_FOUND(
            "CREATE_SESSIONS_COURTROOM_NOT_FOUND",
            "rota courtroom mapping is missing for %s"),

    CREATE_SESSIONS_DUPLICATE_COURTROOMS_FOUND(
            "CREATE_SESSIONS_DUPLICATE_COURTROOMS_FOUND",
            "Duplicate courtroom IDs found: %s"),

    JUDICIARY_ID_NOT_FOUND_ASSIGNMENT(
            "JUDICIARY_ID_NOT_FOUND_ASSIGNMENT",
            Prefix.MONITORING + "Judiciary assignment failed for ids: %s"),

    SESSION_ID_NOT_FOUND_ASSIGNMENT(
            "SESSION_ID_NOT_FOUND_ASSIGNMENT",
            Prefix.MONITORING + "Court schedule assignment failed for session ids: %s");

    public static final String DELIMITER = "%n%n------------------%n";

    private static final class Prefix {
        private static final String MONITORING = "SCSLMissingData: ";
    }

    private final String code;
    private final String template;

    MissingDataError(String code, String template) {
        this.code = code;
        this.template = template;
    }

    public String code() {
        return code;
    }

    public String template() {
        return template;
    }

    public String format(Object... args) {
        return String.format(template, args);
    }

    @Override
    public String toString() {
        return code;
    }
}
