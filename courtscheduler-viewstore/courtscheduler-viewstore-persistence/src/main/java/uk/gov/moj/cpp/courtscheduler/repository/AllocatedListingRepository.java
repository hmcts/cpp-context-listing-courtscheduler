package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;

@Repository(forEntity = AllocatedListing.class)
public interface AllocatedListingRepository extends EntityRepository<AllocatedListing, String> {
}
