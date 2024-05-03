package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

@Repository(forEntity = CourtSchedule.class)
public interface CourtScheduleRepository extends EntityRepository<CourtSchedule, String> {
}
