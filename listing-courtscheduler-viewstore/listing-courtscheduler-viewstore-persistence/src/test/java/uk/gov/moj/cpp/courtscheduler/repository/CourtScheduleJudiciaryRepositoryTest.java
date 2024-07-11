package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.util.List;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CdiTestRunner.class)
public class CourtScheduleJudiciaryRepositoryTest {
    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @After
    public void tearDown() {
        List<CourtScheduleJudiciary> all = courtScheduleJudiciaryRepository.findAll();
        all.forEach(courtScheduleJudiciary -> courtScheduleJudiciaryRepository.remove(courtScheduleJudiciary));
    }

    @Test
    public void shouldSave() {
        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);
        CourtScheduleJudiciary by = courtScheduleJudiciaryRepository.findBy(courtScheduleJudiciary.getId());

        assertThat(by, notNullValue());

    }

    @Test
    public void shouldFindByEmail() {
        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);
        CourtScheduleJudiciary by = courtScheduleJudiciaryRepository.findByEmail(courtScheduleJudiciary.getEmail());

        assertThat(by, notNullValue());

    }

    @Test
    public void shouldFindCourtScheduleJudiciariesUpdatedBetweenDates() {
        CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        MiFilterCriteria miFilterCriteria = new MiFilterCriteria(fromDate, toDate);


        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> courtScheduleJudiciaryList = courtScheduleJudiciaryRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
        assertThat(courtScheduleJudiciaryList.isEmpty(), is(false));
    }

}