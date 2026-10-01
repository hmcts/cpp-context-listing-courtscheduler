package uk.gov.moj.cpp.courtscheduler.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CourtRoom {

    private String id;
    private Integer rotaLocationId;
    private String rotaVenueName;
    private Integer cppCourtRoomId;
    private Integer rotaVenueId;
    private String oucode;
    private String oucodeL3Name;
    private String oucodeL2Name;
    private String oucodeL2Code;
    private String oucodeUUID;
    @JsonProperty("courtroomName")
    private String courtRoomName;
    @JsonProperty("courtroomId")
    private String courtRoomId;

    public String getId() {
        return id;
    }

    public Integer getRotaLocationId() {
        return rotaLocationId;
    }

    public String getRotaVenueName() {
        return rotaVenueName;
    }

    public Integer getCppCourtRoomId() {
        return cppCourtRoomId;
    }

    public Integer getRotaVenueId() {
        return rotaVenueId;
    }

    public String getOucode() {
        return oucode;
    }

    public String getOucodeL3Name() {
        return oucodeL3Name;
    }

    public String getOucodeL2Name() {
        return oucodeL2Name;
    }

    public String getOucodeL2Code() {
        return oucodeL2Code;
    }

    public String getOucodeUUID() {
        return oucodeUUID;
    }

    public String getCourtroomName() {
        return courtRoomName;
    }

    public String getCourtroomId() {
        return courtRoomId;
    }


    public static final class CourtRoomBuilder {
        private String id;
        private Integer rotaLocationId;
        private String rotaVenueName;
        private Integer cppCourtRoomId;
        private Integer rotaVenueId;
        private String oucode;
        private String oucodeL3Name;
        private String oucodeL2Name;
        private String oucodeL2Code;
        private String oucodeUUID;
        private String courtroomName;
        private String courtroomId;

        private CourtRoomBuilder() {
        }

        public static CourtRoomBuilder aCourtRoom() {
            return new CourtRoomBuilder();
        }

        public CourtRoomBuilder withId(final String id) {
            this.id = id;
            return this;
        }

        public CourtRoomBuilder withRotaLocationId(final Integer rotaLocationId) {
            this.rotaLocationId = rotaLocationId;
            return this;
        }

        public CourtRoomBuilder withRotaVenueName(final String rotaVenueName) {
            this.rotaVenueName = rotaVenueName;
            return this;
        }

        public CourtRoomBuilder withCppCourtRoomId(final Integer cppCourtRoomId) {
            this.cppCourtRoomId = cppCourtRoomId;
            return this;
        }

        public CourtRoomBuilder withRotaVenueId(final Integer rotaVenueId) {
            this.rotaVenueId = rotaVenueId;
            return this;
        }

        public CourtRoomBuilder withOucode(final String oucode) {
            this.oucode = oucode;
            return this;
        }

        public CourtRoomBuilder withOucodeL3Name(final String oucodeL3Name) {
            this.oucodeL3Name = oucodeL3Name;
            return this;
        }

        public CourtRoomBuilder withOucodeL2Name(final String oucodeL2Name) {
            this.oucodeL2Name = oucodeL2Name;
            return this;
        }

        public CourtRoomBuilder withOucodeL2Code(final String oucodeL2Code) {
            this.oucodeL2Code = oucodeL2Code;
            return this;
        }

        public CourtRoomBuilder withOucodeUUID(final String oucodeUUID) {
            this.oucodeUUID = oucodeUUID;
            return this;
        }

        public CourtRoomBuilder withCourtRoomName(final String courtroomName) {
            this.courtroomName = courtroomName;
            return this;
        }

        public CourtRoomBuilder withCourtRoomId(final String courtroomId) {
            this.courtroomId = courtroomId;
            return this;
        }

        public CourtRoom build() {
            final CourtRoom courtRoom = new CourtRoom();
            courtRoom.rotaVenueName = this.rotaVenueName;
            courtRoom.oucodeL2Name = this.oucodeL2Name;
            courtRoom.rotaLocationId = this.rotaLocationId;
            courtRoom.rotaVenueId = this.rotaVenueId;
            courtRoom.oucode = this.oucode;
            courtRoom.oucodeL2Code = this.oucodeL2Code;
            courtRoom.oucodeUUID = this.oucodeUUID;
            courtRoom.cppCourtRoomId = this.cppCourtRoomId;
            courtRoom.id = this.id;
            courtRoom.courtRoomId = this.courtroomId;
            courtRoom.courtRoomName = this.courtroomName;
            courtRoom.oucodeL3Name = this.oucodeL3Name;
            return courtRoom;
        }
    }
}
