package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.persist.entity.SessionAllocationKey;

@Repository(forEntity = CourtRoomSessionAllocation.class)
public interface CourtRoomSessionAllocationRepository extends EntityRepository<CourtRoomSessionAllocation, SessionAllocationKey> {}
