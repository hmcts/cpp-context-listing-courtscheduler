package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

@Repository(forEntity = ProvisionalBooking.class)
public interface ProvisionalBookingRepository extends EntityRepository<ProvisionalBooking, ProvisionalBookingKey> {
}
