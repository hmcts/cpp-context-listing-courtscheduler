package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.Judiciary;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = Judiciary.class)
public interface JudiciaryRepository extends EntityRepository<Judiciary, String> {

    Judiciary findByEmail(String email);
}
