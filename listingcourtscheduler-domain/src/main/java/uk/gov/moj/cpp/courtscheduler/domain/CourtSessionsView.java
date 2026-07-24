package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;

public class CourtSessionsView {
    private String courtRoomId;
    private String courtRoomName;
    private List<CourtScheduleView> sessions = new ArrayList<>();

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public String getCourtRoomName() {
        return courtRoomName;
    }

    public List<CourtScheduleView>  getSessions() {
        return sessions;
    }

    public void addSession(CourtScheduleView session) {
        this.sessions.add(session);
    }

    public CourtSessionsView(String courtRoomId, String courtRoomName) {
        this.courtRoomId = courtRoomId;
        this.courtRoomName = courtRoomName;
    }
}
