package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistoryKey;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(CdiTestRunner.class)
public class RotaFileProcessHistoryRepositoryTest {

    @Inject
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    @After
    public void tearDown() {
        List<RotaFileProcessHistory> all = rotaFileProcessHistoryRepository.findAll();
        all.forEach(rotaFileProcessHistory -> rotaFileProcessHistoryRepository.remove(rotaFileProcessHistory));
    }

    @Test
    public void shouldSave() {

        final RotaFileProcessHistory rotaFileProcessHistory = random(RotaFileProcessHistory.class);

        rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);

        // then
        RotaFileProcessHistory by = rotaFileProcessHistoryRepository.findBy(rotaFileProcessHistory.getId());

        assertThat(by, notNullValue());
    }

    @Test
    public void shouldDeleteByFileNamePrefixAndFileDate() {
        final RotaFileProcessHistory rotaFileProcessHistory = random(RotaFileProcessHistory.class);
        final RotaFileProcessHistoryKey rotaFileProcessHistoryKey = rotaFileProcessHistory.getId();
        rotaFileProcessHistoryKey.setFileDate(Timestamp.valueOf(LocalDate.of(2024, 10, 1).atStartOfDay()));
        rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);

        final String fileNamePrefix = rotaFileProcessHistory.getId().getFileNamePrefix();
        final Timestamp fileDate = Timestamp.valueOf(LocalDate.of(2024, 10, 5).atStartOfDay());

        rotaFileProcessHistoryRepository.deleteByFileNamePrefixAndFileDate(fileNamePrefix, fileDate);

        final List<RotaFileProcessHistory> rotaFileProcessHistories = rotaFileProcessHistoryRepository.findAll();
        assertEquals(0, rotaFileProcessHistories.size());
    }

    @Test
    public void shouldFindByFileNamePrefixAndFileDateGreaterThan() {
        final RotaFileProcessHistory rotaFileProcessHistory = random(RotaFileProcessHistory.class);
        final RotaFileProcessHistoryKey rotaFileProcessHistoryKey = rotaFileProcessHistory.getId();
        rotaFileProcessHistoryKey.setFileDate(Timestamp.valueOf(LocalDate.of(2024, 10, 1).atStartOfDay()));
        rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);

        final String fileNamePrefix = rotaFileProcessHistory.getId().getFileNamePrefix();
        final Timestamp fileDate = Timestamp.valueOf(LocalDate.of(2024, 9, 30).atStartOfDay());

        final List<RotaFileProcessHistory> rotaFileProcessHistories = rotaFileProcessHistoryRepository.findByFileNamePrefixAndFileDateGreaterThan(fileNamePrefix, fileDate);

        assertEquals(1, rotaFileProcessHistories.size());
        assertEquals(rotaFileProcessHistories.get(0).getId().getFileNamePrefix(), fileNamePrefix);
        assertEquals(rotaFileProcessHistories.get(0).getId().getFileDate(), rotaFileProcessHistory.getId().getFileDate());
        assertEquals(rotaFileProcessHistories.get(0).getProcessedOn(), rotaFileProcessHistory.getProcessedOn());
    }
}
