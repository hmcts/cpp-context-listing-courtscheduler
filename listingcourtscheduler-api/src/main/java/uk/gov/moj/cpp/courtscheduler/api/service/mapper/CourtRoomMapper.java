package uk.gov.moj.cpp.courtscheduler.api.service.mapper;

import static java.util.Objects.isNull;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.persist.entity.Venue;

public class CourtRoomMapper {

    // Private constructor to prevent instantiation
    private CourtRoomMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static CourtRoom toEntity(uk.gov.moj.cpp.courtscheduler.domain.CourtRoom domain) {
        if (isNull(domain)) {
            return null;
        }

        final CourtRoom entity = new CourtRoom();
        final Venue venue = new Venue();
        venue.setVenueName(domain.getRotaVenueName());
        venue.setVenueId(domain.getRotaVenueId());
        venue.setLocationId(domain.getRotaLocationId());
        entity.setVenue(venue);
        entity.setOucode(domain.getOucode());
        entity.setOucodeL3Name(domain.getOucodeL3Name());
        return entity;
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.CourtRoom toDomain(final CourtRoom entity) {
        if (isNull(entity)) {
            return null;
        }

        return uk.gov.moj.cpp.courtscheduler.domain.CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withCourtRoomId(entity.getId())
                .withOucode(entity.getOucode())
                .withOucodeL3Name(entity.getOucodeL3Name())
                .withCourtRoomName(entity.getCourtroomName())
                .withRotaVenueId(entity.getVenue().getVenueId())
                .withRotaLocationId(entity.getVenue().getLocationId())
                .withRotaVenueName(entity.getVenue().getVenueName())
                .build();
    }
}
