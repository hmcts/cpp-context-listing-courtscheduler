package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;

import javax.inject.Inject;
import java.util.List;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;


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
}
