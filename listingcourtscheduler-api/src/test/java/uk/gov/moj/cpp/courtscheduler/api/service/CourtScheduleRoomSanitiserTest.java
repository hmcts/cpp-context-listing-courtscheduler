package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import static org.hamcrest.Matchers.sameInstance;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackResponse;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CourtScheduleRoomSanitiserTest {

    @Test
    void stripsCourtRoomFieldsFromDraftSession() {
        final CourtSchedule draft = scheduleWithRoom(true);

        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(List.of(draft));

        assertThat(draft.getCourtRoomId(), is(nullValue()));
        assertThat(draft.getCourtRoomName(), is(nullValue()));
        assertThat(draft.getCourtRoomNumber(), is(nullValue()));
    }

    @Test
    void preservesCourtRoomFieldsOnNonDraftSession() {
        final CourtSchedule finalSession = scheduleWithRoom(false);

        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(List.of(finalSession));

        assertThat(finalSession.getCourtRoomId(), is("room-1"));
        assertThat(finalSession.getCourtRoomName(), is("Courtroom 01"));
        assertThat(finalSession.getCourtRoomNumber(), is(101));
    }

    @Test
    void keepsCourthouseAndScheduleIdOnDraftSession() {
        // Only the room within the venue is provisional for a draft session — the venue and the
        // courtScheduleId (used to book the slot) must survive.
        final CourtSchedule draft = scheduleWithRoom(true);

        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(List.of(draft));

        assertThat(draft.getCourtScheduleId(), is("schedule-1"));
        assertThat(draft.getCourtHouseId(), is("house-1"));
        assertThat(draft.getCourtHouseName(), is("Liverpool Mags Court"));
    }

    @Test
    void stripsOnlyDraftSessionsInAMixedList() {
        final CourtSchedule draft = scheduleWithRoom(true);
        final CourtSchedule finalSession = scheduleWithRoom(false);

        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(new ArrayList<>(List.of(draft, finalSession)));

        assertThat(draft.getCourtRoomId(), is(nullValue()));
        assertThat(finalSession.getCourtRoomId(), is("room-1"));
    }

    @Test
    void toleratesNullListAndNullElements() {
        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(null);

        final List<CourtSchedule> withNull = new ArrayList<>();
        withNull.add(null);
        withNull.add(scheduleWithRoom(true));
        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(withNull);

        assertThat(withNull.get(1).getCourtRoomId(), is(nullValue()));
    }

    @Test
    void nullsCourtRoomIdOnDraftFallbackResponseAndKeepsEverythingElse() {
        final CrownFallbackResponse draft = fallbackResponse(true);

        final CrownFallbackResponse result = CourtScheduleRoomSanitiser.stripCourtRoomFromDraftFallbackResponse(draft);

        assertThat(result.courtRoomId(), is(nullValue()));
        assertThat(result.courtScheduleId(), is("schedule-1"));
        assertThat(result.isDraft(), is(true));
        assertThat(result.sessionDate(), is("2026-06-01"));
    }

    @Test
    void returnsNonDraftFallbackResponseUntouched() {
        final CrownFallbackResponse finalResponse = fallbackResponse(false);

        final CrownFallbackResponse result = CourtScheduleRoomSanitiser.stripCourtRoomFromDraftFallbackResponse(finalResponse);

        assertThat(result, is(sameInstance(finalResponse)));
        assertThat(result.courtRoomId(), is("731816c1-5ee4-373a-9bda-840e13a5bcb0"));
    }

    @Test
    void toleratesNullFallbackResponse() {
        assertThat(CourtScheduleRoomSanitiser.stripCourtRoomFromDraftFallbackResponse(null), is(nullValue()));
    }

    private static CrownFallbackResponse fallbackResponse(final boolean draft) {
        return new CrownFallbackResponse("hearing-1", "schedule-1", "731816c1-5ee4-373a-9bda-840e13a5bcb0", "2026-06-01",
                "10:00", "16:00", 360, draft, "CROWN_TRIAL", "SOURCE", false);
    }

    private static CourtSchedule scheduleWithRoom(final boolean draft) {
        final CourtSchedule courtSchedule = new CourtSchedule();
        courtSchedule.setCourtScheduleId("schedule-1");
        courtSchedule.setIsDraft(draft);
        courtSchedule.setCourtRoomId("room-1");
        courtSchedule.setCourtRoomName("Courtroom 01");
        courtSchedule.setCourtRoomNumber(101);
        courtSchedule.setCourtHouseId("house-1");
        courtSchedule.setCourtHouseName("Liverpool Mags Court");
        return courtSchedule;
    }
}
