package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = RotaProcessLog.class)
public abstract class RotaProcessLogRepository extends AbstractEntityRepository<RotaProcessLog, String> {

}
