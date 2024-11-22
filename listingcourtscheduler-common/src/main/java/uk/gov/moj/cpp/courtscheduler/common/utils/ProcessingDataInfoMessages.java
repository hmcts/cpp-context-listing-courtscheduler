package uk.gov.moj.cpp.courtscheduler.common.utils;

@SuppressWarnings("squid:S1213")
public class ProcessingDataInfoMessages {

    private ProcessingDataInfoMessages() {}

    public static final String SESSION_ALLOCATION_MAX_SLOT_UPDATE_MSG = "refdata sitting pattern found for Oucode: %s, CourtRoomId: %d, sessionDate: %s, CourtSession: %s, Business Type: %s to be updated with maxSlot: %d - maxDuration: %d";
    public static final String MISSING_SLOT_FOR_JUDICIARY_WARNING_MSG = "[AMS] [missingslotsforjudiciary] - no matching slots for sessionStart: %s - courtCentreName: %s - courtRoomName: %s - businessType: %s - courtSession: %s - panel: %s";
    public static final String SLOT_WILL_NOT_BE_SAVED_HAVING_ADULT_PANEL = "[AMS] [slotwillnotbesavedhavingADULTpanel] the slot will not be persisted as having ADULT panel slot existing and session will not be saved for panel: {}, ouCode: {}, businessType: {}, sessionDate: {}, courtRoomNumber: {}";
    public static final String SLOT_WILL_NOT_BE_SAVED_HAVING_YOUTH_PANEL = "[AMS] [slotwillnotbesavedhavingYOUTHpanel] the slot will not be persisted as having YOUTH panel slot existing and session will not be saved for panel: {}, ouCode: {}, businessType: {}, sessionDate: {}, courtRoomNumber: {}";
    public static final String SLOT_WILL_NOT_BE_SAVED_HAVING_AM_OR_PM_SESSION = "[AMS] [slotwillnotbesavedhavingAMorPMsession] the slot will not be persisted as having AM or PM session slot existing and {} session will not be saved for ouCode: {}, businessType: {}, sessionDate: {}, courtRoomNumber: {}";
    public static final String SLOT_WILL_NOT_BE_SAVED_HAVING_AD_SESSION = "[AMS] [slotwillnotbesavedhavingAMorPMsession] the slot will not be persisted as having AD session slot existing and {} session will not be saved for ouCode: {}, businessType: {}, sessionDate: {}, courtRoomNumber: {}";
}
