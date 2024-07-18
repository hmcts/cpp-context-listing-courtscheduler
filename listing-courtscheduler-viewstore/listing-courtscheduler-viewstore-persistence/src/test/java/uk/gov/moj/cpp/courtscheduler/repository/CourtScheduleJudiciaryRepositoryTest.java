package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
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

    @Test
    public void shouldDeactivateSchedules() {
        final String courtScheduleId = randomUUID().toString();
        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary.getId().setCourtScheduleId(courtScheduleId);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);

        final Date updatedOn = Calendar.getInstance().getTime();
        courtScheduleJudiciaryRepository.deactivateSchedules(List.of(courtScheduleId), updatedOn);

        final CourtScheduleJudiciary courtScheduleJudiciaryAfterDeactivation = courtScheduleJudiciaryRepository.findBy(courtScheduleJudiciary.getId());
        courtScheduleJudiciaryRepository.refresh(courtScheduleJudiciaryAfterDeactivation);

        assertEquals(false, courtScheduleJudiciaryAfterDeactivation.getActive());
        assertEquals(courtScheduleJudiciaryAfterDeactivation.getUpdatedOn().getTime(), updatedOn.getTime());
    }

    @Test
    public void shouldUpdateCourtScheduleJudiciaryPosition() {
        final String courtScheduleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();

        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary.setPosition("6");
        courtScheduleJudiciary.setUpdatedOn(Calendar.getInstance().getTime());
        courtScheduleJudiciary.setActive(false);
        courtScheduleJudiciary.getId().setCourtScheduleId(courtScheduleId);
        courtScheduleJudiciary.getId().setJudiciaryId(judiciaryId);


        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);

        final String newPosition = "10";
        final Date updatedOn = Calendar.getInstance().getTime();
        courtScheduleJudiciaryRepository.updateCourtScheduleJudiciaryPosition(newPosition, updatedOn, courtScheduleId, judiciaryId);

        final CourtScheduleJudiciary courtScheduleJudiciaryUpdated = courtScheduleJudiciaryRepository.findBy(courtScheduleJudiciary.getId());
        courtScheduleJudiciaryRepository.refresh(courtScheduleJudiciaryUpdated);

        assertEquals(courtScheduleJudiciaryUpdated.getPosition(), newPosition);
        assertEquals(courtScheduleJudiciaryUpdated.getUpdatedOn().getTime(), updatedOn.getTime());
        assertEquals(true, courtScheduleJudiciaryUpdated.getActive());
    }

}