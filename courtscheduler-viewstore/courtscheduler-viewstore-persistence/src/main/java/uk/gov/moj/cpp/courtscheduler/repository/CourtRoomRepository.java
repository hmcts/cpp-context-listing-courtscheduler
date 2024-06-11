package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BusinessType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.persist.entity.Venue;

@Repository(forEntity = CourtRoom.class)
public interface CourtRoomRepository extends EntityRepository<CourtRoom, String> {
    CourtRoom findByVenue(Venue venue);
}
