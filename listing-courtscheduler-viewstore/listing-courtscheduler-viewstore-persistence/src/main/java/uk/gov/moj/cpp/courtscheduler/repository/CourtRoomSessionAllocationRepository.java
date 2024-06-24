package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.persist.entity.SessionAllocationKey;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = CourtRoomSessionAllocation.class)
public interface CourtRoomSessionAllocationRepository extends EntityRepository<CourtRoomSessionAllocation, SessionAllocationKey> {}
