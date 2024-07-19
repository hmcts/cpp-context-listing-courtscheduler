package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher;

@SuppressWarnings("squid:S1213")
public class MissingDataErrorMessages {

    private MissingDataErrorMessages() {

    }

    private static final String MONITORING_PREFIX = "SCSLMissingData: ";

    public static final String COURT_DETAIL_NOT_FOUND = "COURT_DETAIL_NOT_FOUND";
    public static final String SESSION_ALLOCATION_NOT_FOUND = "SESSION_ALLOCATION_NOT_FOUND";
    public static final String DELIMITER = "%n ------------------%n";
    public static final String COURT_DETAIL_NOT_FOUND_MSG = MONITORING_PREFIX + "Court details not found for the following locations :- %n%s";
    public static final String JUDICIARY_NOT_FOUND_MSG = MONITORING_PREFIX + "Judiciary detail not found for the following judiciaries :-%n%s";
    public static final String SESSION_ALLOCATION_NOT_FOUND_MSG = MONITORING_PREFIX + "%n Session allocation not found for the following :- %n%s";
    public static final String BUSINESS_TYPES_NOT_FOUND_MSG = MONITORING_PREFIX + "These business types cannot be found on newSlots : {}";
    public static final String SESSION_ALLOCATION_ERR_MSG = " Oucode : %s%n CourtRoomId : %s%n Business Type : %s%n Session : %s";
    public static final String JUDICIARY_ERR_MSG = " Name %s %s%n Email : %s";
    public static final String COURT_ROOM_ERR_MSG = " Location id : %d%n Venue name  : %s%n Venue id  : %s";
}
