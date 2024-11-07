package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

@SuppressWarnings("squid:S1213")
public class ProcessingDataInfoMessages {

    private ProcessingDataInfoMessages() {}

    public static final String SESSION_ALLOCATION_MAX_SLOT_UPDATE_MSG = "refdata sitting pattern found for Oucode: %s, CourtRoomId: %d, sessionDate: %s, CourtSession: %s, Business Type: %s to be updated with maxSlot: %d - maxDuration: %d";
    public static final String MISSING_SLOT_FOR_JUDICIARY_WARNING_MSG = "[AMS] [missingslotsforjudiciary] - no matching slots for sessionStart: %s - courtCentreName: %s - courtRoomName: %s - businessType: %s - courtSession: %s - panel: %s";
}
