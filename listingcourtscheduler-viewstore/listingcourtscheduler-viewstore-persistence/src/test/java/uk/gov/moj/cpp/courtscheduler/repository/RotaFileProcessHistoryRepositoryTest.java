package uk.gov.moj.cpp.courtscheduler.repository;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;



class RotaFileProcessHistoryRepositoryTest extends uk.gov.moj.cpp.courtscheduler.repository.AbstractRepositoryTest {

    @Autowired
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @AfterEach
    public void tearDown() {
        List<RotaFileProcessHistory> all = rotaFileProcessHistoryRepository.findAll();
        all.forEach(rotaFileProcessHistory -> rotaFileProcessHistoryRepository.delete(rotaFileProcessHistory));
    }

    @Test
    public void shouldSave() {

        final RotaFileProcessHistory rotaFileProcessHistory = random(RotaFileProcessHistory.class);
        rotaFileProcessHistory.setExecutionId(UUID.randomUUID().toString());
        rotaFileProcessHistory.setProcessedOn(new Timestamp(System.currentTimeMillis())); // Set required field

        rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);

        // then
        RotaFileProcessHistory by = rotaFileProcessHistoryRepository.findById(rotaFileProcessHistory.getExecutionId()).orElse(null);

        assertThat(by, notNullValue());
    }

    @Test
    public void shouldDeleteByFileNamePrefixAndFileDate() {
        final RotaFileProcessHistory rotaFileProcessHistory = random(RotaFileProcessHistory.class);
        rotaFileProcessHistory.setExecutionId(UUID.randomUUID().toString());
        rotaFileProcessHistory.setProcessedOn(new Timestamp(System.currentTimeMillis())); // Set required field
        rotaFileProcessHistory.setFileDate(Timestamp.valueOf(LocalDate.of(2024, 10, 1).atStartOfDay()));
        rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);

        final String fileNamePrefix = rotaFileProcessHistory.getFileNamePrefix();
        final Timestamp fileDate = Timestamp.valueOf(LocalDate.of(2024, 10, 5).atStartOfDay());

        rotaFileProcessHistoryRepository.deleteByFileNamePrefixAndFileDate(fileNamePrefix, fileDate);

        final List<RotaFileProcessHistory> rotaFileProcessHistories = rotaFileProcessHistoryRepository.findAll();
        assertEquals(0, rotaFileProcessHistories.size());
    }

    @Test
    public void shouldFindByFileNamePrefixAndFileDateGreaterThan() {
        final RotaFileProcessHistory rotaFileProcessHistory = random(RotaFileProcessHistory.class);
        rotaFileProcessHistory.setExecutionId(UUID.randomUUID().toString());
        rotaFileProcessHistory.setProcessedOn(new Timestamp(System.currentTimeMillis())); // Set required field
        rotaFileProcessHistory.setFileDate(Timestamp.valueOf(LocalDate.of(2024, 10, 1).atStartOfDay()));
        rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);

        final String fileNamePrefix = rotaFileProcessHistory.getFileNamePrefix();
        final Timestamp fileDate = Timestamp.valueOf(LocalDate.of(2024, 9, 30).atStartOfDay());

        final List<RotaFileProcessHistory> rotaFileProcessHistories = rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(fileNamePrefix, fileDate);

        assertEquals(1, rotaFileProcessHistories.size());
        assertEquals(rotaFileProcessHistories.get(0).getFileNamePrefix(), fileNamePrefix);
        assertEquals(rotaFileProcessHistories.get(0).getFileDate(), rotaFileProcessHistory.getFileDate());
        assertEquals(rotaFileProcessHistories.get(0).getProcessedOn(), rotaFileProcessHistory.getProcessedOn());
    }
}
