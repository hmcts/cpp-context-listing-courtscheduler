package uk.gov.moj.cpp.courtscheduler.common.service;

import static javax.transaction.Transactional.TxType.REQUIRES_NEW;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;
import uk.gov.moj.cpp.courtscheduler.repository.RotaProcessLogRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class RotaProcessLogService {

    @Inject
    private RotaProcessLogRepository rotaProcessLogRepository;

    @Transactional(REQUIRES_NEW)
    public RotaProcessLog saveRotaProcessLog(final RotaProcessLog rotaProcessLog) {
        return rotaProcessLogRepository.save(rotaProcessLog);
    }

    public int deleteRedundantRotaData(final int numberOfPreviousMonths) {
        return rotaProcessLogRepository.deleteRedundantRotaData(numberOfPreviousMonths * 30);
    }
}
