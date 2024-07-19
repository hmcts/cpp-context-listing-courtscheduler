package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.persist.entity.Venue;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = CourtRoom.class)
public interface CourtRoomRepository extends EntityRepository<CourtRoom, String> {
    CourtRoom findByVenue(Venue venue);
}
