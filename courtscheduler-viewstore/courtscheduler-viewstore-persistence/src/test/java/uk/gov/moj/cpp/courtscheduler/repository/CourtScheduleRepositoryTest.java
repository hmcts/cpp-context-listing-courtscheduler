package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import javax.inject.Inject;
import java.util.List;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(CdiTestRunner.class)
public class CourtScheduleRepositoryTest {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @After
    public void tearDown() {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.findAll();
        courtSchedules.forEach(courtSchedule -> courtScheduleRepository.remove(courtSchedule));
    }

    @Test
    public void shouldSave() {
        final CourtSchedule courtSchedule = random(CourtSchedule.class);

        courtScheduleRepository.save(courtSchedule);
        CourtSchedule by = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());

        assertThat(by, notNullValue());

    }
}
