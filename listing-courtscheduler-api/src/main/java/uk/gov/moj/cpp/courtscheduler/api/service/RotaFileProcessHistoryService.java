package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.sql.Timestamp.valueOf;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistoryKey;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class RotaFileProcessHistoryService {

    @Inject
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @Transactional
    public void update(final String fileNamePrefix, final OffsetDateTime fileDate) {
        final Timestamp fileDateAsTimestamp = Timestamp.from(fileDate.toInstant());
        rotaFileProcessHistoryRepository.delete(fileNamePrefix, fileDateAsTimestamp);

        final RotaFileProcessHistory rotaFileProcessHistory = new RotaFileProcessHistory();
        final RotaFileProcessHistoryKey id = new RotaFileProcessHistoryKey();
        id.setFileDate(fileDateAsTimestamp);
        id.setFileNamePrefix(fileNamePrefix);
        rotaFileProcessHistory.setId(id);
        rotaFileProcessHistory.setProcessedOn(valueOf(LocalDateTime.now()));
        rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);
    }
}
