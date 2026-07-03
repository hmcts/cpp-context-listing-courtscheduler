package uk.gov.moj.cpp.courtscheduler.common.service;


import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.RotaProcessLogRepository;

@Service
@Transactional
public class RotaProcessLogService {

    @Inject
    private RotaProcessLogRepository rotaProcessLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public RotaProcessLog saveRotaProcessLog(final RotaProcessLog rotaProcessLog) {
        this.entityManager.persist(rotaProcessLog);
        this.entityManager.flush();
        return rotaProcessLog;
    }

    public int deleteRedundantRotaData(final int numberOfPreviousMonths) {
        return this.rotaProcessLogRepository.deleteRedundantRotaData(numberOfPreviousMonths * 30);
    }
}
