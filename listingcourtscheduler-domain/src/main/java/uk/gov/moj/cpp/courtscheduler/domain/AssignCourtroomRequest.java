package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

@SuppressWarnings({"PMD.BeanMembersShouldSerialize", "squid:S2384"})
public class AssignCourtroomRequest {

    private List<String> courtScheduleIds;
    private String courtRoomId;

    public AssignCourtroomRequest() {
    }

    public AssignCourtroomRequest(final List<String> courtScheduleIds, final String courtRoomId) {
        this.courtScheduleIds = courtScheduleIds;
        this.courtRoomId = courtRoomId;
    }

    public List<String> getCourtScheduleIds() {
        return courtScheduleIds;
    }

    public void setCourtScheduleIds(final List<String> courtScheduleIds) {
        this.courtScheduleIds = courtScheduleIds;
    }

    public String getCourtRoomId() {
        return courtRoomId;
    }

    public void setCourtRoomId(final String courtRoomId) {
        this.courtRoomId = courtRoomId;
    }

    public static final class AssignCourtroomRequestBuilder {
        private List<String> courtScheduleIds;
        private String courtRoomId;

        private AssignCourtroomRequestBuilder() {
        }

        public static AssignCourtroomRequestBuilder assignCourtroomRequestBuilder() {
            return new AssignCourtroomRequestBuilder();
        }

        public AssignCourtroomRequestBuilder withCourtScheduleIds(final List<String> courtScheduleIds) {
            this.courtScheduleIds = courtScheduleIds;
            return this;
        }

        public AssignCourtroomRequestBuilder withCourtRoomId(final String courtRoomId) {
            this.courtRoomId = courtRoomId;
            return this;
        }

        public AssignCourtroomRequest build() {
            return new AssignCourtroomRequest(courtScheduleIds, courtRoomId);
        }
    }
}


