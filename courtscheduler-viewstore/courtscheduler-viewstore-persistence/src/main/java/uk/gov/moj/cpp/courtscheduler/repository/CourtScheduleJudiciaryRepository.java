package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.BusinessType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

@Repository(forEntity = CourtScheduleJudiciary.class)
public interface CourtScheduleJudiciaryRepository extends EntityRepository<CourtScheduleJudiciary, CourtScheduleJudiciaryKey> {
    CourtScheduleJudiciary findByEmail(String email);
}
