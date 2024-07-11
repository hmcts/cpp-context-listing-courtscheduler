package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.persist.entity.SessionAllocationKey;

import java.util.List;
import java.util.UUID;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = CourtRoomSessionAllocation.class)
public interface CourtRoomSessionAllocationRepository extends EntityRepository<CourtRoomSessionAllocation, SessionAllocationKey> {

    @Query("from CourtRoomSessionAllocation entity where entity.sessionAllocationKey.ouCode = :ouCode and entity.sessionAllocationKey.roomId = :caseId and entity.sessionAllocationKey.listingSession = :listingSession and entityh.sessionAllocationKey.businessType = :businessType")
    CourtRoomSessionAllocation findByOuCodeAndRoomIdAndListingSessionAndBusinessType(@QueryParam("ouCode") String ouCode,
                                                                                     @QueryParam("roomId") Integer roomId,
                                                                                     @QueryParam("listingSession") String listingSession,
                                                                                     @QueryParam("businessType") String businessType);
}
