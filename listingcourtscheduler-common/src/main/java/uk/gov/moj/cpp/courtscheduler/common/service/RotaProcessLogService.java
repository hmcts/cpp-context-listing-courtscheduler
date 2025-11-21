package uk.gov.moj.cpp.courtscheduler.common.service;

import static javax.transaction.Transactional.TxType.REQUIRES_NEW;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.transaction.Transactional;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.RotaProcessLogRepository;

@ApplicationScoped
public class RotaProcessLogService {

    @Inject
    private RotaProcessLogRepository rotaProcessLogRepository;

    @PersistenceContext(unitName = "courtscheduler-persistence-unit")
    private EntityManager entityManager;

    @Transactional(REQUIRES_NEW)
    public RotaProcessLog saveRotaProcessLog(final RotaProcessLog rotaProcessLog) {
        this.entityManager.persist(rotaProcessLog);
        this.entityManager.flush();
        return rotaProcessLog;
    }

    public int deleteRedundantRotaData(final int numberOfPreviousMonths) {
        return this.rotaProcessLogRepository.deleteRedundantRotaData(numberOfPreviousMonths * 30);
    }
}
