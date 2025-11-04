package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = RotaProcessLog.class)
public abstract class RotaProcessLogRepository extends AbstractEntityRepository<RotaProcessLog, String> {

    private static final String DELETE_REDUNDANT_ROTA_DATA = "DELETE FROM rota_process_log WHERE timestamp < (CURRENT_DATE - :numberOfDays)";

    public int deleteRedundantRotaData(final int numberOfDays) {
        return entityManager()
                .createNativeQuery(DELETE_REDUNDANT_ROTA_DATA)
                .setParameter("numberOfDays", numberOfDays)
                .executeUpdate();
    }

}
