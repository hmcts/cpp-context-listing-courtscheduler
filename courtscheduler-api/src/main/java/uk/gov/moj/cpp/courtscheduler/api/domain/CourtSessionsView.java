package uk.gov.moj.cpp.courtscheduler.api.domain;

public class CourtSessionsView {
    private String courtRoomId;
    private String courtRoomName;

    private CourtScheduleView session;


    public static final class CourtSessionsViewBuilder {
        private String courtRoomId;
        private String courtRoomName;
        private CourtScheduleView session;

        public CourtSessionsViewBuilder() {
        }

        public static CourtSessionsViewBuilder aCourtSessionsView() {
            return new CourtSessionsViewBuilder();
        }

        public CourtSessionsViewBuilder withCourtRoomId(String courtRoomId) {
            this.courtRoomId = courtRoomId;
            return this;
        }

        public CourtSessionsViewBuilder withCourtRoomName(String courtRoomName) {
            this.courtRoomName = courtRoomName;
            return this;
        }

        public CourtSessionsViewBuilder withSession(CourtScheduleView session) {
            this.session = session;
            return this;
        }

        public CourtSessionsView build() {
            CourtSessionsView courtSessionsView = new CourtSessionsView();
            courtSessionsView.courtRoomId = this.courtRoomId;
            courtSessionsView.session = this.session;
            courtSessionsView.courtRoomName = this.courtRoomName;
            return courtSessionsView;
        }
    }
}
